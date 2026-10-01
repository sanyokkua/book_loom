package ua.bookloom.pipeline.review;

import com.google.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.PlaceholderRepair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.WhitespaceRestoration;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.project.OpenProjects;

/**
 * The person's actions on one stored segment, each checked against the status machine before it writes: a refused
 * action changes nothing and answers {@code validation}. Saving an edit or reverting withdraws a backward-revision
 * proposal waiting on the segment, since it was built on the wording those actions replace. Every write goes through
 * {@link SegmentRepository#update}, which replaces that one record and applies the change to the record as it is
 * then stored, so a run deciding other segments meanwhile is never overwritten. Retry is not here: it calls the model.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SegmentActions {

    private static final String NO_MACHINE_TARGET = "There is no machine translation to accept — edit it or retry.";
    private static final String NO_TARGET_TO_REVERT_TO =
            "There is no machine translation to revert to — edit it again or retry.";
    private static final String NOT_OPEN = "The book of this project is not open, so the edit cannot be checked.";
    private static final String ACCEPT = "accept";
    private static final String SAVE_EDIT = "save the edit";
    private static final String REVERT = "revert";
    private static final String SKIP = "skip";
    private static final String APPLY_PROPOSAL = "apply the proposal";

    private final DocumentPort documents;
    private final OpenProjects openProjects;
    private final ProjectRepository projects;
    private final SegmentRepository segments;
    private final DeferralRepository deferrals;

    /**
     * Accepts a segment: FLAGGED becomes ACCEPTED with its machine target, and an ACCEPTED one is only confirmed.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the stored record, marked reviewed; {@code validation} for another status, for a FLAGGED segment with
     *     no machine target, or for an unknown id, with nothing changed
     */
    public Result<SegmentRecord> accept(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("accept project={} segment={}", projectId, segmentId);
        return load(ACCEPT, projectId, segmentId).flatMap(this::accepted);
    }

    /**
     * Stores the person's edit of a segment, keeping its machine target.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @param maskedText the non-null edit as the editor shows it, the book's {@code ⟦gN⟧} tokens in place
     * @return the stored record, REVISED and reviewed, with the edit in plain and masked form; {@code validation}
     *     for a PENDING segment, for an unknown id, or naming the placeholder the edit broke, with nothing changed
     */
    public Result<SegmentRecord> saveEdit(final String projectId, final String segmentId, final String maskedText) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(maskedText, "maskedText");
        log.debug("saveEdit project={} segment={} length={}", projectId, segmentId, maskedText.length());
        log.trace("saveEdit segment={} text={}", segmentId, maskedText);
        return load(SAVE_EDIT, projectId, segmentId).flatMap(record -> edited(record, maskedText));
    }

    /**
     * Discards the person's edit and goes back to the machine target.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the stored record, ACCEPTED and reviewed with both user forms cleared; {@code validation} for a
     *     segment that is not REVISED, for one with no machine target to go back to, or for an unknown id
     */
    public Result<SegmentRecord> revert(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("revert project={} segment={}", projectId, segmentId);
        return load(REVERT, projectId, segmentId).flatMap(this::reverted);
    }

    /**
     * Moves the queue on without touching the segment.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the id of the next FLAGGED segment after this one in document order, wrapping to the first FLAGGED
     *     one; the segment's own id when no other is FLAGGED; {@code validation} for an unknown id
     */
    public Result<String> skip(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("skip project={} segment={}", projectId, segmentId);
        return load(SKIP, projectId, segmentId).flatMap(this::skipped);
    }

    /**
     * Applies the backward-revision proposal waiting on an edited segment as the person's own edit.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the stored record, REVISED and reviewed with the proposal as its edit and the deferral resolved;
     *     {@code validation} for a segment that is not REVISED, for one with no proposal waiting, or for an unknown id
     */
    public Result<SegmentRecord> acceptProposal(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("acceptProposal project={} segment={}", projectId, segmentId);
        return load(APPLY_PROPOSAL, projectId, segmentId).flatMap(this::proposed);
    }

    private Result<SegmentRecord> accepted(final SegmentRecord record) {
        final SegmentStatus status = record.status();
        log.debug("accept: status={} hasMachineTarget={}", status, record.machineTarget() != null);
        if (status != SegmentStatus.FLAGGED && status != SegmentStatus.ACCEPTED) {
            return wrongStatus(ACCEPT, record, "flagged or accepted");
        }
        if (status == SegmentStatus.FLAGGED && record.machineTarget() == null) {
            return refuse(ACCEPT, record.segmentId(), NO_MACHINE_TARGET);
        }
        return write(
                ACCEPT,
                record,
                current -> current.withStatus(SegmentStatus.ACCEPTED).withReviewed(true));
    }

    private Result<SegmentRecord> edited(final SegmentRecord record, final String maskedText) {
        log.debug("saveEdit: status={}", record.status());
        if (record.status() == SegmentStatus.PENDING) {
            return wrongStatus(SAVE_EDIT, record, "flagged, accepted or revised");
        }
        final Document document = openProjects.get(record.projectId());
        if (document == null) {
            return refuse(SAVE_EDIT, record.segmentId(), NOT_OPEN);
        }
        final Optional<Segment> source = sourceOf(document, record.segmentId());
        if (source.isEmpty()) {
            return refuse(SAVE_EDIT, record.segmentId(), "The opened book holds no such segment.");
        }
        return restored(record, document.format(), source.get(), maskedText);
    }

    private Result<SegmentRecord> restored(
            final SegmentRecord record, final BookFormat format, final Segment segment, final String maskedText) {
        final String candidate = WhitespaceRestoration.restore(segment.masked(), maskedText.strip());
        // An edit that only lost or repeated a token keeps the person's words: the token is put back by position.
        final GateResult gate = GateFunction.of(documents, format)
                .restoreRepairing(segment, candidate, PlaceholderRepair.RESTORE_MISSING);
        return switch (gate) {
            case GateResult.Restored restored -> {
                log.debug(
                        "saveEdit: gate outcome=Restored segment={} tokensPutBack={}",
                        record.segmentId(),
                        restored.autoRepair() != null);
                yield write(
                                SAVE_EDIT,
                                record,
                                current -> current.withStatus(SegmentStatus.REVISED)
                                        .withUserTarget(restored.restored(), restored.maskedForm())
                                        .withPath(SegmentPath.USER)
                                        .withReviewed(true))
                        .flatMap(this::withoutProposal);
            }
            case GateResult.GateFailed failed -> {
                log.debug("saveEdit: gate outcome=GateFailed segment={}", record.segmentId());
                yield refuseWith(SAVE_EDIT, record.segmentId(), failed.error());
            }
            case GateResult.StepError step -> {
                log.debug("saveEdit: gate outcome=StepError segment={}", record.segmentId());
                yield refuseWith(SAVE_EDIT, record.segmentId(), step.error());
            }
        };
    }

    private Result<SegmentRecord> reverted(final SegmentRecord record) {
        log.debug("revert: status={} hasMachineTarget={}", record.status(), record.machineTarget() != null);
        if (record.status() != SegmentStatus.REVISED) {
            return wrongStatus(REVERT, record, "revised");
        }
        if (record.machineTarget() == null) {
            return refuse(REVERT, record.segmentId(), NO_TARGET_TO_REVERT_TO);
        }
        // The path the machine target came by is not kept once an edit replaces it; the repair rounds tell the two
        // apart.
        return write(
                        REVERT,
                        record,
                        current -> current.withStatus(SegmentStatus.ACCEPTED)
                                .withUserTarget(null, null)
                                .withPath(current.repairRounds() > 0 ? SegmentPath.REPAIRED : SegmentPath.DRAFT)
                                .withReviewed(true))
                .flatMap(this::withoutProposal);
    }

    // A proposal waiting on the segment was built on the wording just replaced, so the panel must not offer it.
    private Result<SegmentRecord> withoutProposal(final SegmentRecord after) {
        return Proposals.withdraw(deferrals, after.projectId(), after.segmentId())
                .map(withdrawn -> after);
    }

    private Result<String> skipped(final SegmentRecord record) {
        final Result<Optional<Project>> project = projects.find(record.projectId());
        final Result<List<SegmentRecord>> all = segments.all(record.projectId());
        if (project.isErr() || all.isErr()) {
            return Result.err(Objects.requireNonNull(project.isErr() ? project.error() : all.error()));
        }
        if (Objects.requireNonNull(project.data()).isEmpty()) {
            return refuse(SKIP, record.segmentId(), "This project is not stored.");
        }
        final Set<SegmentKind> kept =
                project.data().get().brief().alsoTranslate().keptKinds();
        final String next = nextFlagged(Objects.requireNonNull(all.data()), record.segmentId(), kept);
        log.info(
                "skip segment={} status={} reviewed={} next={}",
                record.segmentId(),
                record.status(),
                record.reviewed(),
                next);
        return Result.ok(next);
    }

    private Result<SegmentRecord> proposed(final SegmentRecord record) {
        if (record.status() != SegmentStatus.REVISED) {
            return wrongStatus(APPLY_PROPOSAL, record, "revised");
        }
        final Result<List<Deferral>> open = deferrals.open(record.projectId());
        if (open.isErr()) {
            return Result.err(Objects.requireNonNull(open.error()));
        }
        final Optional<Deferral> waiting = Proposals.waitingOn(Objects.requireNonNull(open.data()), record.segmentId());
        log.debug("acceptProposal: status={} proposalFound={}", record.status(), waiting.isPresent());
        if (waiting.isEmpty()) {
            return refuse(APPLY_PROPOSAL, record.segmentId(), "No proposal is waiting on this segment.");
        }
        final Deferral deferral = waiting.get();
        final String plain = Objects.requireNonNull(deferral.proposal(), "proposal");
        final String masked = Objects.requireNonNull(deferral.maskedProposal(), "maskedProposal");
        return write(
                        APPLY_PROPOSAL,
                        record,
                        current -> current.withStatus(SegmentStatus.REVISED)
                                .withUserTarget(plain, masked)
                                .withPath(SegmentPath.USER)
                                .withReviewed(true))
                .flatMap(after ->
                        deferrals.resolve(record.projectId(), deferral.id()).map(resolved -> after));
    }

    private static String nextFlagged(
            final List<SegmentRecord> all, final String segmentId, final Set<SegmentKind> kept) {
        final int position = indexOf(all, segmentId);
        final Optional<String> following = all.subList(position + 1, all.size()).stream()
                .filter(record -> isListedFlagged(record, kept))
                .map(SegmentRecord::segmentId)
                .findFirst();
        return following
                .or(() -> all.stream()
                        .filter(record -> isListedFlagged(record, kept))
                        .map(SegmentRecord::segmentId)
                        .filter(id -> !id.equals(segmentId))
                        .findFirst())
                .orElse(segmentId);
    }

    private static boolean isListedFlagged(final SegmentRecord record, final Set<SegmentKind> kept) {
        return record.status() == SegmentStatus.FLAGGED && !record.isKeptAsSource(kept);
    }

    private static int indexOf(final List<SegmentRecord> all, final String segmentId) {
        for (int index = 0; index < all.size(); index++) {
            if (all.get(index).segmentId().equals(segmentId)) {
                return index;
            }
        }
        return all.size() - 1;
    }

    private static Optional<Segment> sourceOf(final Document document, final String segmentId) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.id().equals(segmentId))
                .findFirst();
    }

    private Result<SegmentRecord> load(final String action, final String projectId, final String segmentId) {
        final Result<Optional<SegmentRecord>> found = segments.find(projectId, segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error()));
        }
        return Objects.requireNonNull(found.data())
                .map(Result::ok)
                .orElseGet(() -> refuse(action, segmentId, "This project holds no segment " + segmentId + "."));
    }

    private Result<SegmentRecord> write(
            final String action, final SegmentRecord before, final UnaryOperator<SegmentRecord> change) {
        final Result<SegmentRecord> updated = segments.update(before.projectId(), before.segmentId(), change);
        final SegmentRecord after = updated.data();
        if (after != null) {
            log.info(
                    "{} segment={} status={} reviewed={}", action, after.segmentId(), after.status(), after.reviewed());
        }
        return updated;
    }

    private static <T> Result<T> wrongStatus(final String action, final SegmentRecord record, final String allowed) {
        final String status = record.status().name().toLowerCase(Locale.ROOT);
        return refuse(
                action,
                record.segmentId(),
                "This segment is " + status + "; only a " + allowed + " segment can " + action + ".");
    }

    private static <T> Result<T> refuse(final String action, final String segmentId, final String message) {
        return refuseWith(action, segmentId, AppError.of(ErrorCode.validation, "Cannot " + action, message));
    }

    private static <T> Result<T> refuseWith(final String action, final String segmentId, final AppError error) {
        log.warn("{} refused segment={} code={}", action, segmentId, error.code());
        return Result.err(error);
    }
}
