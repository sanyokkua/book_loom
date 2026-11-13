package ua.bookloom.pipeline.eval;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.SegmentPath;

/**
 * What one run of the fixture left behind, read from the stores, the events and the log, before any metric is computed.
 *
 * @param report the job's terminal report
 * @param elapsed the wall time of the run
 * @param calls every model call that finished
 * @param segments every segment in book order with its decision
 * @param lexicon the lexicon at the end of the run
 * @param fallbackReasons per {@code status/problems} the batch items that fell back to their own draft
 * @param editsRefused the reviewer edits the verifier refused
 * @param hardGateFailuresRound0 the segments whose first evaluation failed a hard gate (a placeholder or a blocking
 *     text check)
 * @param hardGateKinds the findings that sent those segments to their first repair round, counted per kind
 * @param proofreadTruncated proofread replies the provider cut off at the output cap
 * @param narrator the narrator mode the brief was written with
 * @param summaryGate what the chapter-summary gate kept and dropped
 */
record SequenceRun(
        JobReport report,
        Duration elapsed,
        List<ModelCallFinished> calls,
        List<Decided> segments,
        List<LexiconEntry> lexicon,
        Map<String, Integer> fallbackReasons,
        int editsRefused,
        int hardGateFailuresRound0,
        Map<String, Integer> hardGateKinds,
        int proofreadTruncated,
        SequenceNarratorMode narrator,
        SummaryGateTally summaryGate) {

    /** A run whose log was not read for the chapter-summary gate. */
    SequenceRun(
            final JobReport report,
            final Duration elapsed,
            final List<ModelCallFinished> calls,
            final List<Decided> segments,
            final List<LexiconEntry> lexicon,
            final Map<String, Integer> fallbackReasons,
            final int editsRefused,
            final int hardGateFailuresRound0,
            final Map<String, Integer> hardGateKinds,
            final int proofreadTruncated,
            final SequenceNarratorMode narrator) {
        this(
                report,
                elapsed,
                calls,
                segments,
                lexicon,
                fallbackReasons,
                editsRefused,
                hardGateFailuresRound0,
                hardGateKinds,
                proofreadTruncated,
                narrator,
                SummaryGateTally.NONE);
    }

    /** Copies the lists. */
    SequenceRun {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(elapsed, "elapsed");
        calls = List.copyOf(calls);
        segments = List.copyOf(segments);
        lexicon = List.copyOf(lexicon);
        fallbackReasons = Map.copyOf(fallbackReasons);
        hardGateKinds = Map.copyOf(hardGateKinds);
        Objects.requireNonNull(narrator, "narrator");
        Objects.requireNonNull(summaryGate, "summaryGate");
    }

    /**
     * One segment as the run decided it.
     *
     * @param id the segment id
     * @param chapter the index of its chapter in the fixture, -1 before the first heading
     * @param narrator who narrates its chapter
     * @param source its masked source with the placeholder tokens taken out
     * @param target its stored machine target, masked, with the tokens taken out; null when none was stored
     * @param status its final status
     * @param path how it was decided
     * @param editsApplied the reviewer edits that were applied to it
     * @param proofread what the Proofread stage last decided for it, or null when no window read it
     */
    record Decided(
            String id,
            int chapter,
            NarratorPerson narrator,
            String source,
            @Nullable String target,
            SegmentStatus status,
            SegmentPath path,
            int editsApplied,
            @Nullable Proofread proofread) {

        /** A segment no Proofread window read. */
        Decided(
                final String id,
                final int chapter,
                final NarratorPerson narrator,
                final String source,
                @Nullable final String target,
                final SegmentStatus status,
                final SegmentPath path,
                final int editsApplied) {
            this(id, chapter, narrator, source, target, status, path, editsApplied, null);
        }
    }

    /**
     * A segment's last proofread verdict, so an arm can judge a kept rewrite against the text it replaced (arm 7b).
     *
     * @param outcome the verdict's outcome name
     * @param round the round of the window that decided it (1 marked, 2 Max's whole book), from its fingerprint
     * @param before the masked text before a kept rewrite, tokens taken out, or null when none was kept
     */
    record Proofread(String outcome, int round, @Nullable String before) {

        /**
         * The round a verdict's fingerprint names: it starts with {@code r<round>:}.
         *
         * @param fingerprint the non-null stored fingerprint
         * @return the round, or 0 when the fingerprint names none
         */
        static int roundOf(final String fingerprint) {
            final int end = fingerprint.indexOf(':');
            return fingerprint.startsWith("r") && end > 1 ? Integer.parseInt(fingerprint.substring(1, end)) : 0;
        }
    }

    /** What the job itself reported, before the stores and the log are read. */
    record Outcome(JobReport report, Duration elapsed, List<ModelCallFinished> calls, List<Decided> segments) {}
}
