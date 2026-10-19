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
 * @param reviewerTruncated reviewer replies the provider cut off at the output cap
 * @param narrator the narrator mode the brief was written with
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
        int reviewerTruncated,
        SequenceNarratorMode narrator) {

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
     */
    record Decided(
            String id,
            int chapter,
            NarratorPerson narrator,
            String source,
            @Nullable String target,
            SegmentStatus status,
            SegmentPath path,
            int editsApplied) {}

    /** What the job itself reported, before the stores and the log are read. */
    record Outcome(JobReport report, Duration elapsed, List<ModelCallFinished> calls, List<Decided> segments) {}
}
