package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;

/**
 * One chunk's quality-loop settings.
 *
 * @param reviewMode the run's review mode; its {@link ReviewMode#threshold()} is τ and τ_judge alike
 * @param dial the quality dial's mechanics — the repair budget and whether the judge runs
 * @param frame the run's language pair, style sheet and foreign-passage policy
 * @param namePolicy the Book Brief's name policy, read by the script and echo checks' name removal
 * @param glossaryTerms every glossary term in the chunk, read by the same name removal
 */
public record LoopSettings(
        ReviewMode reviewMode,
        DialParameters dial,
        CallFrame frame,
        NamePolicy namePolicy,
        List<String> glossaryTerms) {

    /** Validates the invariants a caller is entitled to assume and defensively copies the list component. */
    public LoopSettings {
        Objects.requireNonNull(reviewMode, "reviewMode");
        Objects.requireNonNull(dial, "dial");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        glossaryTerms = List.copyOf(glossaryTerms);
    }
}
