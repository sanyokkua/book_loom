package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;

/**
 * Stores one revised target: a machine-owned segment takes it as its machine target in both forms and becomes
 * REVISED; a segment the person edited keeps their text and gets it as a proposal in both forms instead, so an
 * accepted proposal can be edited again with its placeholders. The record is read again right before the write,
 * because the person may have edited it while a revision call waited on a pause.
 */
@Slf4j
@RequiredArgsConstructor
final class RevisionWriter {

    private final SegmentRepository segments;
    private final DeferralRepository deferrals;

    /** How a revised target was stored. */
    enum Stored {
        /** The machine target was replaced and the segment is REVISED. */
        MACHINE_TARGET,

        /** The person's edit was kept and the target waits as a proposal. */
        PROPOSAL
    }

    /**
     * Stores {@code revised} for its segment and resolves the deferrals it answers.
     *
     * @param projectId the project id
     * @param segmentId the segment the target is for
     * @param revised the new target, restored through the document gate
     * @param answered the open deferrals the new target answers, the first of which carries a proposal; not empty
     * @return how the target was stored, or the storage error
     */
    Result<Stored> store(
            final String projectId,
            final String segmentId,
            final GateResult.Restored revised,
            final List<Deferral> answered) {
        final Result<Optional<SegmentRecord>> found = segments.find(projectId, segmentId);
        if (found.isErr()) {
            return Result.err(Objects.requireNonNull(found.error(), "error"));
        }
        final boolean edited = Objects.requireNonNull(found.data(), "found")
                .map(record -> record.userTarget() != null)
                .orElse(false);
        log.debug("Storing a revised target segmentId={} edited={} answers={}", segmentId, edited, answered.size());
        return edited ? propose(revised, answered) : replace(projectId, segmentId, revised, answered);
    }

    private Result<Stored> replace(
            final String projectId,
            final String segmentId,
            final GateResult.Restored revised,
            final List<Deferral> answered) {
        final Result<SegmentRecord> updated = segments.update(
                projectId,
                segmentId,
                current -> current.withMachineTarget(revised.restored(), revised.maskedForm())
                        .withStatus(SegmentStatus.REVISED));
        if (updated.isErr()) {
            return Result.err(Objects.requireNonNull(updated.error(), "error"));
        }
        return resolveAll(answered).map(done -> Stored.MACHINE_TARGET);
    }

    // The carrier is resolved and added again with the proposal, since an open deferral is never changed in place.
    private Result<Stored> propose(final GateResult.Restored revised, final List<Deferral> answered) {
        final Deferral carrier = answered.getFirst();
        final Deferral withProposal = new Deferral(
                carrier.id(),
                carrier.projectId(),
                carrier.segmentId(),
                carrier.reason(),
                carrier.waitingOn(),
                carrier.replacedRendering(),
                revised.restored(),
                revised.maskedForm());
        return resolveAll(answered).flatMap(done -> deferrals.add(withProposal)).map(added -> Stored.PROPOSAL);
    }

    Result<Boolean> resolveAll(final List<Deferral> answered) {
        for (final Deferral deferral : answered) {
            final Result<Boolean> resolved = deferrals.resolve(deferral.projectId(), deferral.id());
            if (resolved.isErr()) {
                return resolved;
            }
        }
        return Result.ok(true);
    }
}
