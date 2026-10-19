package ua.bookloom.pipeline.eval;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;

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
 */
record MiniRun(
        NamePolicy policy,
        JobState end,
        Map<String, Outcome> outcomes,
        List<GlossaryEntry> glossary,
        List<String> errors,
        Calls calls,
        Duration elapsed) {

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
    }
}
