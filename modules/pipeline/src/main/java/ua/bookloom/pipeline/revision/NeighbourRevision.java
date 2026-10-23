package ua.bookloom.pipeline.revision;

import java.util.Comparator;
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
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.memory.CheckedParagraphs;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.BlockingFindings;

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
    private final CheckedParagraphs checked;

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
                .filter(at -> isInScope(inputs, byId.get(order.get(at)), scope))
                .boxed()
                .sorted(Comparator.comparing(at -> !hasBlocking(byId.get(order.get(at)))))
                .toList();
        logStarted(inputs, byId, scope, checked.size());
        calls.planned(ModelCalls.Stage.NEIGHBOUR_CHECK, checked.size());
        final Result<Boolean> ran = checkAll(inputs, checked, byId, calls, tally);
        log.info(
                "Neighbour check ended project={} checked={} skippedAsRemembered={} notReread={}",
                inputs.projectId(),
                checked.size(),
                tally.rememberedCount(),
                tally.notRereadCount());
        return ran;
    }

    private Result<Boolean> checkAll(
            final PassInputs inputs,
            final List<Integer> checked,
            final Map<String, SegmentRecord> byId,
            final ModelCalls calls,
            final PassTally tally) {
        for (int next = 0; next < checked.size(); next++) {
            final int left = checked.size() - next;
            if (EarlyStop.isRequested(calls, "Neighbour check", left)) {
                tally.notReread(left);
                return Result.ok(true);
            }
            final Result<Boolean> done = checkOne(inputs, inputs.order(), checked.get(next), byId, calls, tally);
            if (done.isErr()) {
                return stoppedOrFailed(done, calls, tally, left);
            }
        }
        return Result.ok(true);
    }

    // The paragraph whose call the early stop interrupted was not reread either, so it counts with the rest.
    private static Result<Boolean> stoppedOrFailed(
            final Result<Boolean> failed, final ModelCalls calls, final PassTally tally, final int left) {
        if (!EarlyStop.isStop(failed, calls, "Neighbour check")) {
            return failed;
        }
        tally.notReread(left);
        return Result.ok(true);
    }

    // Repaired, flagged or doubted: the shapes where a defect is likely, so by default the pass costs a call only for
    // the paragraphs the run already had trouble with; "every segment" reads the whole book.
    private static boolean isInScope(final PassInputs inputs, @Nullable final SegmentRecord record, final Scope scope) {
        if (record != null && !inputs.isStory(record.segmentId())) {
            log.debug("Neighbour check segmentId={} skipped: navigation, front or back matter", record.segmentId());
            return false;
        }
        return isInScopeByState(record, scope);
    }

    private static boolean isInScopeByState(@Nullable final SegmentRecord record, final Scope scope) {
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

    private static void logStarted(
            final PassInputs inputs, final Map<String, SegmentRecord> byId, final Scope scope, final int inScope) {
        log.info(
                "Neighbour check started project={} segments={} inScope={} everySegment={} skippedAsMatter={}",
                inputs.projectId(),
                inputs.order().size(),
                inScope,
                scope.everySegment(),
                matterSkipped(inputs, byId, scope));
    }

    private static long matterSkipped(
            final PassInputs inputs, final Map<String, SegmentRecord> byId, final Scope scope) {
        return inputs.order().stream()
                .filter(id -> !inputs.isStory(id) && isInScopeByState(byId.get(id), scope))
                .count();
    }

    // Blocking defects first, in reading order within each group, so a stopped pass has spent its calls where they
    // matter.
    private static boolean hasBlocking(@Nullable final SegmentRecord record) {
        return record != null && !BlockingFindings.of(record).isEmpty();
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
        final NeighbourPrompt.@Nullable Neighbour before = neighbour(inputs, order, at - 1, byId);
        final NeighbourPrompt.@Nullable Neighbour after = neighbour(inputs, order, at + 1, byId);
        final Map<String, String> user = NeighbourPrompt.slots(inputs, source, masked, before, after);
        final ExportProgress.Neighbours around = NeighbourPrompt.Neighbour.around(before, source, after);
        return ask(inputs, source, user, new Checked(record, masked, around), calls, tally);
    }

    private Result<Boolean> ask(
            final PassInputs inputs,
            final Segment source,
            final Map<String, String> user,
            final Checked item,
            final ModelCalls calls,
            final PassTally tally) {
        final SegmentRecord record = item.record();
        final String id = record.segmentId();
        final RevisionGuards.Mode mode = guardMode(record);
        final String fingerprint = ParagraphFingerprint.of(inputs, user, mode);
        if (checked.has(inputs.projectId(), id, fingerprint)) {
            log.debug("Neighbour check segmentId={} skipped: checked before in this state", id);
            tally.neighbourRemembered();
            return unchanged(calls, record, "remembered", item.around());
        }
        log.debug("Neighbour check segmentId={} status={} guards={}", id, record.status(), mode);
        final Result<RevisionAnswer> answered =
                call.checkAgainstNeighbours(inputs, source, item.masked(), user, mode, calls);
        if (answered.isErr()) {
            return skipped(record, Objects.requireNonNull(answered.error(), "error"), tally, calls, item.around());
        }
        final RevisionAnswer answer = Objects.requireNonNull(answered.data(), "answered");
        rememberIfSettled(inputs.projectId(), id, fingerprint, item.masked(), answer);
        return settle(inputs, item, answer, tally, calls);
    }

    // A paragraph left as it was, or whose change was refused for what it broke, is not asked again until something it
    // was shown changes; a fix that was stored changes the paragraph itself, so the next pass checks the new text.
    private void rememberIfSettled(
            final String projectId,
            final String id,
            final String fingerprint,
            final String masked,
            final RevisionAnswer answer) {
        final boolean settled =
                switch (answer) {
                    case RevisionAnswer.Unchanged unchanged -> true;
                    case RevisionAnswer.Refused refused -> !refused.isUnreadable();
                    case RevisionAnswer.Revised revised ->
                        revised.restored().maskedForm().equals(masked);
                };
        if (settled) {
            checked.remember(projectId, id, fingerprint);
        }
    }

    // One paragraph's failed call (a timeout, an unreadable reply) is no reason to lose the whole pass or the book's
    // export; only the person's stop ends it.
    private static Result<Boolean> skipped(
            final SegmentRecord record,
            final AppError error,
            final PassTally tally,
            final ModelCalls calls,
            final ExportProgress.@Nullable Neighbours around) {
        if (error.code() == ErrorCode.cancelled) {
            return Result.err(error);
        }
        log.warn("Neighbour check segmentId={} skipped: the call failed code={}", record.segmentId(), error.code());
        tally.skipped();
        return unchanged(calls, record, "skipped", around);
    }

    // Shows the paragraph as it was, with no new text, for an item the pass went over without changing it.
    private static Result<Boolean> unchanged(
            final ModelCalls calls,
            final SegmentRecord record,
            final String verdict,
            final ExportProgress.@Nullable Neighbours around) {
        calls.reread(record.segmentId(), shownBefore(record), null, verdict, around);
        return Result.ok(false);
    }

    private static String shownBefore(final SegmentRecord record) {
        final String target = record.machineTarget();
        return target != null ? target : Objects.requireNonNullElse(record.maskedMachineTarget(), "");
    }

    // A flagged paragraph's text failed a check, often a lost sentence or quote mark: its fix may add one back.
    private static RevisionGuards.Mode guardMode(final SegmentRecord record) {
        return record.status() == SegmentStatus.FLAGGED ? RevisionGuards.Mode.NO_LOSS : RevisionGuards.Mode.SAME_COUNTS;
    }

    private static NeighbourPrompt.@Nullable Neighbour neighbour(
            final PassInputs inputs, final List<String> order, final int at, final Map<String, SegmentRecord> byId) {
        if (at < 0 || at >= order.size()) {
            return null;
        }
        final String id = order.get(at);
        return NeighbourPrompt.Neighbour.of(inputs.source(id).orElse(null), byId.get(id));
    }

    /**
     * The paragraph a check answered for.
     *
     * @param record its stored record when the call was made
     * @param masked its stored target, masked
     * @param around the paragraphs around it, as the reread item shows them
     */
    private record Checked(SegmentRecord record, String masked, ExportProgress.@Nullable Neighbours around) {}

    private Result<Boolean> settle(
            final PassInputs inputs,
            final Checked item,
            final RevisionAnswer answer,
            final PassTally tally,
            final ModelCalls calls) {
        final SegmentRecord record = item.record();
        final String id = record.segmentId();
        return switch (answer) {
            case RevisionAnswer.Unchanged unchanged -> {
                log.debug("Neighbour check segmentId={} answered unchanged", id);
                tally.neighbourUnchanged();
                yield unchanged(calls, record, "unchanged", item.around());
            }
            case RevisionAnswer.Refused refused -> {
                log.debug("Neighbour check segmentId={} refused rule={}", id, refused.reason());
                tally.refused(refused.reason());
                yield unchanged(calls, record, "refused:" + refused.reason(), item.around());
            }
            case RevisionAnswer.Revised revised
            when revised.restored().maskedForm().equals(item.masked()) -> {
                log.debug("Neighbour check segmentId={} found nothing to change", id);
                tally.neighbourUnchanged();
                yield unchanged(calls, record, "unchanged", item.around());
            }
            case RevisionAnswer.Revised revised
            when isRewriteTooLarge(record, item.masked(), revised) -> {
                log.debug("Neighbour check segmentId={} refused rule={}", id, RevisionGuards.REWRITE_CAP_RULE);
                tally.refused(RevisionGuards.REWRITE_CAP_RULE);
                yield unchanged(calls, record, "refused:" + RevisionGuards.REWRITE_CAP_RULE, item.around());
            }
            case RevisionAnswer.Revised revised -> storeFix(inputs, item, revised.restored(), tally, calls);
        };
    }

    // A paragraph with a blocking finding is the defect the answer is there to remove, so it may be rewritten freely.
    private static boolean isRewriteTooLarge(
            final SegmentRecord record, final String masked, final RevisionAnswer.Revised revised) {
        return BlockingFindings.of(record).isEmpty()
                && RevisionGuards.exceedsRewriteCap(masked, revised.restored().maskedForm());
    }

    private Result<Boolean> storeFix(
            final PassInputs inputs,
            final Checked item,
            final GateResult.Restored restored,
            final PassTally tally,
            final ModelCalls calls) {
        final SegmentRecord record = item.record();
        final String id = record.segmentId();
        return writer.store(record, restored, List.of(), RevisionWriter.Change.NEIGHBOUR)
                .map(how -> {
                    log.debug("Neighbour check segmentId={} stored={}", id, how);
                    log.trace(
                            "Neighbour check segmentId={} before={} after={}",
                            id,
                            item.masked(),
                            restored.maskedForm());
                    final boolean fixed = how == RevisionWriter.Stored.MACHINE_TARGET;
                    if (fixed) {
                        tally.neighbourFixed(id, inputs.locator(id));
                    }
                    calls.reread(
                            id,
                            shownBefore(record),
                            fixed ? restored.restored() : null,
                            fixed ? "fixed" : "skipped",
                            item.around());
                    return fixed;
                });
    }
}
