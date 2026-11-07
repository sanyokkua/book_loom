package ua.bookloom.pipeline.review;

import static ua.bookloom.util.contract.ArgNames.PROJECT_ID;
import static ua.bookloom.util.contract.ArgNames.SEGMENT_ID;

import com.google.inject.Inject;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.ProofreadVerdict;
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
    private static final String BLANK_EDIT =
            "The edit is empty — type the translation, or leave the segment for later.";
    private static final String NOT_OPEN = "The book of this project is not open, so the edit cannot be checked.";
    private static final String ACCEPT = "accept";
    private static final String SAVE_EDIT = "save the edit";
    private static final String REVERT = "revert";
    private static final String APPLY_PROPOSAL = "apply the proposal";
    private static final String UNDO_PROOFREAD = "undo the proofread";

    private final DocumentPort documents;
    private final OpenProjects openProjects;
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
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(segmentId, SEGMENT_ID);
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
     *     for a PENDING segment, for an unknown id, for a blank edit, or naming the placeholder the edit broke, with
     *     nothing changed
     */
    public Result<SegmentRecord> saveEdit(final String projectId, final String segmentId, final String maskedText) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(segmentId, SEGMENT_ID);
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
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(segmentId, SEGMENT_ID);
        log.debug("revert project={} segment={}", projectId, segmentId);
        return load(REVERT, projectId, segmentId).flatMap(this::reverted);
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
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(segmentId, SEGMENT_ID);
        log.debug("acceptProposal project={} segment={}", projectId, segmentId);
        return load(APPLY_PROPOSAL, projectId, segmentId).flatMap(this::proposed);
    }

    /**
     * Puts back the text, the status and the findings a kept proofread rewrite replaced, from the stored verdict.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the stored record, ACCEPTED or FLAGGED as before the rewrite, reviewed and with no proofread verdict;
     *     {@code validation} for a segment that is not REVISED by a kept rewrite, for one that holds the person's own
     *     edit, or for an unknown id, with nothing changed
     */
    public Result<SegmentRecord> undoProofread(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        Objects.requireNonNull(segmentId, SEGMENT_ID);
        log.debug("undoProofread project={} segment={}", projectId, segmentId);
        return load(UNDO_PROOFREAD, projectId, segmentId).flatMap(this::undone);
    }

    // The verdict's earlier text was the machine's, so it goes back as the machine target with its status and findings.
    private Result<SegmentRecord> undone(final SegmentRecord record) {
        final @Nullable ProofreadVerdict verdict = record.proofread();
        final boolean undoable = record.status() == SegmentStatus.REVISED
                && record.maskedUserTarget() == null
                && verdict != null
                && verdict.isUndoable();
        log.debug("undoProofread: status={} undoable={}", record.status(), undoable);
        if (!undoable) {
            return refuse(UNDO_PROOFREAD, record.segmentId(), "No proofread rewrite of this segment can be undone.");
        }
        final ProofreadVerdict kept = Objects.requireNonNull(verdict, "verdict");
        return write(
                UNDO_PROOFREAD,
                record,
                current -> current.withMachineTarget(
                                Objects.requireNonNull(kept.before(), "before"),
                                Objects.requireNonNull(kept.maskedBefore(), "maskedBefore"))
                        .withStatus(Objects.requireNonNull(kept.statusBefore(), "statusBefore"))
                        .withFindings(kept.findingsBefore())
                        .withProofread(null)
                        .withReviewed(true));
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
        log.debug("saveEdit: status={} blank={}", record.status(), maskedText.isBlank());
        if (record.status() == SegmentStatus.PENDING) {
            return wrongStatus(SAVE_EDIT, record, "flagged, accepted or revised");
        }
        if (maskedText.isBlank()) {
            return refuse(SAVE_EDIT, record.segmentId(), BLANK_EDIT);
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
        // A lost or repeated mark is refused, never put back by position: the editor holds Save until each is in place.
        final GateResult gate = GateFunction.of(documents, format).restore(segment, candidate);
        return switch (gate) {
            case GateResult.Restored restored -> {
                log.debug("saveEdit: gate outcome=Restored segment={}", record.segmentId());
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
            case GateResult.StepError(var error) -> {
                log.debug("saveEdit: gate outcome=StepError segment={}", record.segmentId());
                yield refuseWith(SAVE_EDIT, record.segmentId(), error);
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
        // The path the machine target came by is not kept once an edit replaces it, and no record says whether the
        // repair made it, so the reverted target counts as the draft.
        return write(
                        REVERT,
                        record,
                        current -> current.withStatus(SegmentStatus.ACCEPTED)
                                .withUserTarget(null, null)
                                .withPath(SegmentPath.DRAFT)
                                .withReviewed(true))
                .flatMap(this::withoutProposal);
    }

    // A proposal waiting on the segment was built on the wording just replaced, so the panel must not offer it.
    private Result<SegmentRecord> withoutProposal(final SegmentRecord after) {
        return Proposals.withdraw(deferrals, after.projectId(), after.segmentId())
                .map(withdrawn -> after);
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
