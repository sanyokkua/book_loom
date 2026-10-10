package ua.bookloom.pipeline.revision;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.memory.CheckedParagraphs;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.BlockingFindings;
import ua.bookloom.pipeline.review.RetryCandidate;
import ua.bookloom.pipeline.review.RetryDraft;
import ua.bookloom.pipeline.run.OutcomeRecords;

/**
 * The pass's first model step: every flagged segment, and every accepted one the audit doubts, is drafted again as the
 * review desk's Retry drafts it — the context its first draft saw, the run's checks, reviewer and acceptance rule —
 * and the new text replaces the old only when the acceptance rule takes it, it breaks none of {@link PassChecks}'s
 * rules against the old text, and it is better: the old text was flagged or failed a check, or the new one fails fewer
 * checks or fewer audit checks. Segments with a blocking finding are drafted first. Only a machine-owned target is touched; the person's own text never is.
 * A segment left as it read is remembered by a fingerprint of what its retry depended on, so the next export of an
 * unchanged book drafts it no more.
 */
@Slf4j
@RequiredArgsConstructor
final class RetryPass {

    static final String NOT_ACCEPTED = "not-accepted";

    /** Keeps the retry's memory apart from the neighbour check's, which keys the same segments. */
    private static final String MEMO_KEY = "retry:";

    private static final String STEP = "retry-doubted";

    private final SegmentRepository segments;
    private final RetryDraft retryDraft;
    private final CheckedParagraphs checked;

    /** How one retry ended: a new text stored, the segment left as it read, or the call failed. */
    private enum Attempt {
        STORED,
        KEPT,
        SKIPPED
    }

    /**
     * Drafts the doubted segments again.
     *
     * @param inputs what the pass read as it started
     * @param doubted the flagged and audit-doubted segment ids in reading order, each with the audit's findings on it
     *     (empty for a segment only the run flagged)
     * @param calls the seam every call is sent through
     * @param tally where each outcome is counted
     * @return ok once every eligible segment was tried, or the error that ended the pass — a cancel or a storage error
     */
    Result<Boolean> retry(
            final PassInputs inputs,
            final Map<String, List<QaFinding>> doubted,
            final ModelCalls calls,
            final PassTally tally) {
        final Result<List<SegmentRecord>> read = segments.all(inputs.projectId());
        if (read.isErr()) {
            return Result.err(Objects.requireNonNull(read.error(), "error"));
        }
        final List<SegmentRecord> records = Objects.requireNonNull(read.data(), "records");
        final Map<String, SegmentRecord> byId = new HashMap<>();
        records.forEach(record -> byId.put(record.segmentId(), record));
        final Map<String, String> fingerprints = new HashMap<>();
        final List<SegmentRecord> eligible = records.stream()
                .filter(record -> doubted.containsKey(record.segmentId()) && isRetryable(record))
                .filter(record -> isNew(inputs, record, fingerprint(inputs, record, byId, doubted), fingerprints))
                .sorted(Comparator.comparing(
                        record -> BlockingFindings.of(record).isEmpty()))
                .toList();
        log.info("Retry of doubted segments started project={} eligible={}", inputs.projectId(), eligible.size());
        calls.planned(ModelCalls.Stage.RETRY_DOUBTED, eligible.size());
        for (int done = 0; done < eligible.size(); done++) {
            final SegmentRecord record = eligible.get(done);
            final Result<Attempt> one = retryOne(inputs, record, calls, tally);
            if (one.isErr()) {
                return Result.err(Objects.requireNonNull(one.error(), "error"));
            }
            rememberIfKept(inputs, record, fingerprints.get(record.segmentId()), Objects.requireNonNull(one.data()));
            calls.advanced(ModelCalls.Stage.RETRY_DOUBTED, done + 1);
        }
        log.info("Retry of doubted segments ended project={} tried={}", inputs.projectId(), eligible.size());
        return Result.ok(true);
    }

    // A segment drafted again in this very state on an earlier export, with nothing it depends on changed, would cost
    // the same call for the same answer; it is left as that export left it.
    private boolean isNew(
            final PassInputs inputs,
            final SegmentRecord record,
            final String fingerprint,
            final Map<String, String> fingerprints) {
        final String id = record.segmentId();
        if (checked.has(inputs.projectId(), MEMO_KEY + id, fingerprint)) {
            log.debug("Retry of doubted segmentId={} skipped: drafted again before in this state", id);
            return false;
        }
        fingerprints.put(id, fingerprint);
        return true;
    }

    // Kept or refused, the segment reads as before, so the same question is not asked again; a failed call is asked
    // again next time, and a stored text changes the segment, which changes its fingerprint anyway.
    private void rememberIfKept(
            final PassInputs inputs,
            final SegmentRecord record,
            @Nullable final String fingerprint,
            final Attempt attempt) {
        log.debug("Retry of doubted segmentId={} attempt={}", record.segmentId(), attempt);
        if (attempt == Attempt.KEPT && fingerprint != null) {
            checked.remember(inputs.projectId(), MEMO_KEY + record.segmentId(), fingerprint);
        }
    }

    /**
     * The fingerprint of everything the retry of one segment depends on: its source, its machine text and status, the
     * run's and the audit's findings on it, the context its first draft saw, both neighbours' texts and the glossary.
     */
    private static String fingerprint(
            final PassInputs inputs,
            final SegmentRecord record,
            final Map<String, SegmentRecord> byId,
            final Map<String, List<QaFinding>> doubted) {
        final String id = record.segmentId();
        final int at = inputs.order().indexOf(id);
        final Map<String, String> slots = new HashMap<>();
        slots.put("source", inputs.source(id).map(Segment::masked).orElse(""));
        slots.put("text", Objects.requireNonNullElse(record.maskedMachineTarget(), ""));
        slots.put("status", record.status().name());
        slots.put("findings", record.findings().toString());
        slots.put("audit", doubted.getOrDefault(id, List.of()).toString());
        slots.put("context", String.valueOf(record.context()));
        slots.put("previous", neighbourText(inputs, at - 1, byId));
        slots.put("next", neighbourText(inputs, at + 1, byId));
        slots.put("glossary", inputs.glossary().toString());
        return ParagraphFingerprint.of(inputs, slots, STEP);
    }

    private static String neighbourText(final PassInputs inputs, final int at, final Map<String, SegmentRecord> byId) {
        if (at < 0 || at >= inputs.order().size()) {
            return "";
        }
        final SegmentRecord neighbour = byId.get(inputs.order().get(at));
        return neighbour == null
                ? ""
                : String.valueOf(neighbour.effectiveTarget().orElse(""));
    }

    // A retry replays the context the first draft saw, so a record with none cannot be retried; a kept-verbatim
    // segment was never translated.
    private static boolean isRetryable(final SegmentRecord record) {
        final boolean retryable = record.userTarget() == null
                && record.context() != null
                && record.path() != SegmentPath.VERBATIM
                && (record.status() == SegmentStatus.FLAGGED || record.status() == SegmentStatus.ACCEPTED);
        log.debug(
                "Retry eligibility segmentId={} status={} path={} personText={} snapshot={} retryable={}",
                record.segmentId(),
                record.status(),
                record.path(),
                record.userTarget() != null,
                record.context() != null,
                retryable);
        return retryable;
    }

    private Result<Attempt> retryOne(
            final PassInputs inputs, final SegmentRecord record, final ModelCalls calls, final PassTally tally) {
        final String id = record.segmentId();
        final Result<RetryCandidate> drafted = retryDraft.candidate(record, calls);
        if (drafted.isErr()) {
            final AppError error = Objects.requireNonNull(drafted.error(), "error");
            if (error.code() == ErrorCode.cancelled) {
                return Result.err(error);
            }
            log.warn("Retry of doubted segmentId={} skipped: the draft failed code={}", id, error.code());
            tally.skipped();
            return Result.ok(Attempt.SKIPPED);
        }
        final RetryCandidate candidate = Objects.requireNonNull(drafted.data(), "candidate");
        final Optional<String> refusal = refusal(inputs, record, candidate);
        if (refusal.isPresent()) {
            log.debug("Retry of doubted segmentId={} refused rule={}", id, refusal.get());
            tally.refused(refusal.get());
            return Result.ok(Attempt.KEPT);
        }
        final String before = record.maskedMachineTarget();
        final String after = Objects.requireNonNull(candidate.outcome().maskedMachineTarget(), "accepted target");
        // A flagged text is not accepted, so an accepted new one that breaks no rule is better by the acceptance rule.
        final boolean flagged = record.status() == SegmentStatus.FLAGGED;
        if (before != null && !flagged && !PassChecks.isBetter(inputs, candidate.segment(), before, after)) {
            log.debug("Retry of doubted segmentId={} kept the old text: the new one is no better", id);
            tally.retryKept();
            return Result.ok(Attempt.KEPT);
        }
        return store(inputs, record, candidate, tally);
    }

    private static Optional<String> refusal(
            final PassInputs inputs, final SegmentRecord record, final RetryCandidate candidate) {
        final SegmentOutcome outcome = candidate.outcome();
        final String after = outcome.maskedMachineTarget();
        if (outcome.status() != SegmentStatus.ACCEPTED || after == null) {
            return Optional.of(NOT_ACCEPTED);
        }
        final String before = record.maskedMachineTarget();
        final Segment segment = candidate.segment();
        return before == null
                ? Optional.empty()
                : PassChecks.refusal(inputs, segment, before, after, after, RevisionGuards.Mode.NO_LOSS);
    }

    // Stored only while the segment still reads as it did when the draft was built: the person may have acted on it
    // while the calls ran, and their action stands.
    private Result<Attempt> store(
            final PassInputs inputs, final SegmentRecord base, final RetryCandidate candidate, final PassTally tally) {
        final String id = base.segmentId();
        final AtomicBoolean written = new AtomicBoolean();
        final Result<SegmentRecord> updated = segments.update(base.projectId(), id, current -> {
            if (!current.equals(base)) {
                return current;
            }
            written.set(true);
            return OutcomeRecords.decided(current, candidate.outcome(), candidate.snapshot())
                    .withStatus(SegmentStatus.REVISED);
        });
        if (updated.isErr()) {
            return Result.err(Objects.requireNonNull(updated.error(), "error"));
        }
        log.debug("Retry of doubted segmentId={} stored={}", id, written.get());
        if (log.isTraceEnabled()) {
            log.trace(
                    "Retry of doubted segmentId={} before={} after={}",
                    id,
                    base.maskedMachineTarget(),
                    candidate.outcome().maskedMachineTarget());
        }
        if (written.get()) {
            tally.retryImproved(id, inputs.locator(id));
        } else {
            tally.retryKept();
        }
        // Not written means the person acted on the segment meanwhile: its state is new, so nothing is remembered.
        return Result.ok(written.get() ? Attempt.STORED : Attempt.SKIPPED);
    }

    /**
     * The segments the first step retries: the flagged ones and the accepted ones the audit doubts.
     *
     * @param records the stored records in reading order
     * @param suspicious the audit's doubted segment ids
     * @return the ids in reading order, each with the audit's findings on it (empty when only the run flagged it);
     *     never null
     */
    static Map<String, List<QaFinding>> doubted(
            final List<SegmentRecord> records, final Map<String, List<QaFinding>> suspicious) {
        final Map<String, List<QaFinding>> doubted = new LinkedHashMap<>();
        records.stream()
                .filter(record ->
                        record.status() == SegmentStatus.FLAGGED || suspicious.containsKey(record.segmentId()))
                .forEach(record ->
                        doubted.put(record.segmentId(), suspicious.getOrDefault(record.segmentId(), List.of())));
        return doubted;
    }
}
