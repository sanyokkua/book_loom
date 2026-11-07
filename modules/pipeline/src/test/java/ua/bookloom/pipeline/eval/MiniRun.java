package ua.bookloom.pipeline.eval;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.api.project.ProofreadVerdict;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;

/**
 * What one run of the mini book left behind: each block's decision, the glossary after the names pass and the cost.
 *
 * @param policy the name policy the brief was written with
 * @param end the translation job's terminal state
 * @param outcomes each block's decision by block id; a block the importer made no segment of is missing
 * @param glossary the glossary at the end of the run
 * @param errors the phases that ended in an error, each as {@code phase: code}
 * @param calls the model calls made by the names pass, the translation and the consistency pass
 * @param elapsed the wall time of the three phases
 * @param sent every call the run made, with the usage each reply reported, for the per-case records
 * @param summaryGate what the chapter-summary gate kept and dropped
 * @param proofread what the Proofread stage decided for each segment it read, as the job left it (before the
 *     consistency pass); empty when no window was read
 */
record MiniRun(
        NamePolicy policy,
        JobState end,
        Map<String, Outcome> outcomes,
        List<GlossaryEntry> glossary,
        List<String> errors,
        Calls calls,
        Duration elapsed,
        List<StageProbe.Call> sent,
        SummaryGateTally summaryGate,
        List<Proofread> proofread) {

    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");

    /** A run that kept no record of its calls and whose log was not read. */
    MiniRun(
            final NamePolicy policy,
            final JobState end,
            final Map<String, Outcome> outcomes,
            final List<GlossaryEntry> glossary,
            final List<String> errors,
            final Calls calls,
            final Duration elapsed) {
        this(policy, end, outcomes, glossary, errors, calls, elapsed, List.of(), SummaryGateTally.NONE, List.of());
    }

    /**
     * One segment as the run decided it.
     *
     * @param id the block id
     * @param source the block's text
     * @param target the segment's stored target with its protection tokens taken out, or null when none was stored
     * @param status the segment's final status
     * @param findings the findings the final decision carries
     */
    record Outcome(String id, String source, @Nullable String target, SegmentStatus status, List<QaFinding> findings) {

        /** Copies the findings. */
        Outcome {
            Objects.requireNonNull(id, "id");
            findings = List.copyOf(findings);
        }

        /** The block's decision as {@code record} stores it, the protection tokens taken out of its target. */
        static Outcome of(final String id, final String source, final SegmentRecord record) {
            return new Outcome(id, source, plain(record.maskedMachineTarget()), record.status(), record.findings());
        }
    }

    private static @Nullable String plain(@Nullable final String masked) {
        return masked == null ? null : TOKEN.matcher(masked).replaceAll("");
    }

    /**
     * One segment the Proofread stage read, mapped from its stored verdict: the text, status and findings a kept
     * rewrite replaced, and those it left. A verdict that kept nothing has the same text on both sides.
     *
     * @param id the block id
     * @param outcome what the stage decided
     * @param reason the verdict's stored reason, for a refusal the check that got worse; may be empty
     * @param before the block as it stood before the stage
     * @param after the block as the stage left it
     */
    record Proofread(String id, ProofreadOutcome outcome, String reason, Outcome before, Outcome after) {

        /** Rejects a missing part. */
        Proofread {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }

        /**
         * Maps a stored verdict.
         *
         * @param id the block id
         * @param source the block's text
         * @param record a stored record that holds a proofread verdict
         * @return the segment as the stage read it; never null
         */
        static Proofread of(final String id, final String source, final SegmentRecord record) {
            final ProofreadVerdict verdict = Objects.requireNonNull(record.proofread(), "proofread");
            final Outcome after = Outcome.of(id, source, record);
            final Outcome before = verdict.isUndoable()
                    ? new Outcome(
                            id,
                            source,
                            plain(verdict.maskedBefore()),
                            Objects.requireNonNull(verdict.statusBefore(), "statusBefore"),
                            verdict.findingsBefore())
                    : after;
            return new Proofread(id, verdict.outcome(), verdict.reason(), before, after);
        }

        /** Whether the stage's rewrite is the segment's text now. */
        boolean isKept() {
            return outcome == ProofreadOutcome.KEPT;
        }
    }

    /**
     * The model calls of each phase.
     *
     * @param names the names pass
     * @param translation the translation job
     * @param consistency the consistency pass
     */
    record Calls(int names, int translation, int consistency) {

        int total() {
            return names + translation + consistency;
        }
    }

    /** Copies the collections. */
    MiniRun {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(end, "end");
        outcomes = Map.copyOf(outcomes);
        glossary = List.copyOf(glossary);
        errors = List.copyOf(errors);
        sent = List.copyOf(sent);
        Objects.requireNonNull(summaryGate, "summaryGate");
        proofread = List.copyOf(proofread);
    }
}
