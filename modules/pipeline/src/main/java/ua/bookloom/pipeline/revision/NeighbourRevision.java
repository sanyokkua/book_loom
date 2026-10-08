package ua.bookloom.pipeline.revision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The model's check of decided paragraphs against their neighbours. Each paragraph in scope is shown to the model with
 * the paragraph before and after it (source and translation), the names, terms and characters the book holds, the
 * rolling summary and the source, and the model fixes what disagrees. Only a machine-owned target is ever replaced:
 * the person's own edit is not touched. An answer that changes nothing stores nothing, and one that breaks a rule of
 * {@link PassChecks} is refused and counted by the rule.
 */
@Slf4j
@RequiredArgsConstructor
final class NeighbourRevision {

    private final SegmentRepository segments;
    private final RevisionWriter writer;
    private final RevisionCall call;

    /**
     * Which paragraphs are checked.
     *
     * @param doubted the ids the run flagged or the audit doubted as the pass started, checked even when the first
     *     step drafted them again
     * @param everySegment whether every machine-owned decided paragraph is checked
     */
    record Scope(Set<String> doubted, boolean everySegment) {

        /** Copies the ids. */
        Scope {
            doubted = Set.copyOf(Objects.requireNonNull(doubted, "doubted"));
        }
    }

    /**
     * Checks every paragraph in scope against its neighbours.
     *
     * @param inputs what the pass read as it started
     * @param calls the seam every call is sent through
     * @param tally where each outcome is counted
     * @param scope which paragraphs are checked
     * @return ok once every such paragraph is checked, or the error that ended the pass
     */
    Result<Boolean> revise(final PassInputs inputs, final ModelCalls calls, final PassTally tally, final Scope scope) {
        final Result<List<SegmentRecord>> read = segments.all(inputs.projectId());
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        final Map<String, SegmentRecord> byId = new HashMap<>();
        Objects.requireNonNull(read.data(), "records").forEach(record -> byId.put(record.segmentId(), record));
        final List<String> order = inputs.order();
        final List<Integer> checked = IntStream.range(0, order.size())
                .filter(at -> isInScope(byId.get(order.get(at)), scope))
                .boxed()
                .toList();
        log.info(
                "Neighbour check started project={} segments={} inScope={} everySegment={}",
                inputs.projectId(),
                order.size(),
                checked.size(),
                scope.everySegment());
        calls.planned(ModelCalls.Stage.NEIGHBOUR_CHECK, checked.size());
        for (final int at : checked) {
            final Result<Boolean> done = checkOne(inputs, order, at, byId, calls, tally);
            if (done.isErr()) {
                return done;
            }
        }
        log.info("Neighbour check ended project={} checked={}", inputs.projectId(), checked.size());
        return Result.ok(true);
    }

    // Repaired, flagged or doubted: the shapes where a defect is likely, so by default the pass costs a call only for
    // the paragraphs the run already had trouble with; "every segment" reads the whole book.
    private static boolean isInScope(@Nullable final SegmentRecord record, final Scope scope) {
        if (record == null
                || record.userTarget() != null
                || record.maskedMachineTarget() == null
                || record.status() == SegmentStatus.PENDING
                || record.path() == SegmentPath.VERBATIM) {
            return false;
        }
        return scope.everySegment()
                || record.status() == SegmentStatus.FLAGGED
                || record.path() == SegmentPath.REPAIRED
                || scope.doubted().contains(record.segmentId());
    }

    private Result<Boolean> checkOne(
            final PassInputs inputs,
            final List<String> order,
            final int at,
            final Map<String, SegmentRecord> byId,
            final ModelCalls calls,
            final PassTally tally) {
        final String id = order.get(at);
        final SegmentRecord record = byId.get(id);
        final Segment source = inputs.source(id).orElse(null);
        final String masked = record == null ? null : record.maskedMachineTarget();
        if (record == null || source == null || masked == null) {
            return Result.ok(false);
        }
        final Map<String, String> user = NeighbourPrompt.slots(
                inputs, source, masked, neighbour(inputs, order, at - 1, byId), neighbour(inputs, order, at + 1, byId));
        final Result<RevisionAnswer> answered = call.checkAgainstNeighbours(inputs, source, masked, user, calls);
        if (answered.isErr()) {
            // One paragraph's failed call (a timeout, an unreadable reply) is no reason to lose the whole pass or the
            // book's export; only the person's stop ends it.
            final AppError error = Objects.requireNonNull(answered.error(), "error");
            if (error.code() == ErrorCode.cancelled) {
                return Result.err(error);
            }
            log.warn("Neighbour check segmentId={} skipped: the call failed code={}", id, error.code());
            tally.skipped();
            return Result.ok(false);
        }
        return settle(inputs, record, masked, Objects.requireNonNull(answered.data(), "answered"), tally);
    }

    private static NeighbourPrompt.@Nullable Neighbour neighbour(
            final PassInputs inputs, final List<String> order, final int at, final Map<String, SegmentRecord> byId) {
        if (at < 0 || at >= order.size()) {
            return null;
        }
        final String id = order.get(at);
        return NeighbourPrompt.Neighbour.of(inputs.source(id).orElse(null), byId.get(id));
    }

    private Result<Boolean> settle(
            final PassInputs inputs,
            final SegmentRecord record,
            final String masked,
            final RevisionAnswer answer,
            final PassTally tally) {
        final String id = record.segmentId();
        return switch (answer) {
            case RevisionAnswer.Refused refused -> {
                log.debug("Neighbour check segmentId={} refused rule={}", id, refused.reason());
                tally.refused(refused.reason());
                yield Result.ok(false);
            }
            case RevisionAnswer.Revised revised
            when revised.restored().maskedForm().equals(masked) -> {
                log.debug("Neighbour check segmentId={} found nothing to change", id);
                tally.neighbourUnchanged();
                yield Result.ok(false);
            }
            case RevisionAnswer.Revised revised -> storeFix(inputs, record, masked, revised.restored(), tally);
        };
    }

    private Result<Boolean> storeFix(
            final PassInputs inputs,
            final SegmentRecord record,
            final String masked,
            final GateResult.Restored restored,
            final PassTally tally) {
        final String id = record.segmentId();
        return writer.store(record, restored, List.of(), RevisionWriter.Change.NEIGHBOUR)
                .map(how -> {
                    log.debug("Neighbour check segmentId={} stored={}", id, how);
                    log.trace("Neighbour check segmentId={} before={} after={}", id, masked, restored.maskedForm());
                    if (how == RevisionWriter.Stored.MACHINE_TARGET) {
                        tally.neighbourFixed(id, inputs.locator(id));
                    }
                    return how == RevisionWriter.Stored.MACHINE_TARGET;
                });
    }
}
