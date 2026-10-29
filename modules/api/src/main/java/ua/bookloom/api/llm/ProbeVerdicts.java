package ua.bookloom.api.llm;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What the optional advanced check found for one model; a null verdict means that feature was never checked.
 *
 * @param answerSchema whether the structured-answer schema was honoured
 * @param thinkingSwitch which thinking switch worked, or a verdict naming none
 * @param extraSamplers whether {@code top_k} and {@code min_p} were honoured
 * @param capField whether the answer-length cap field was honoured
 */
public record ProbeVerdicts(
        @Nullable Verdict answerSchema,
        @Nullable ThinkingVerdict thinkingSwitch,
        @Nullable Verdict extraSamplers,
        @Nullable Verdict capField) {

    /** How the server treated a setting that was sent. */
    public enum Outcome {
        /** The server acted on it. */
        HONOURED,
        /** The server accepted it and did nothing. */
        IGNORED,
        /** The server answered with an error. */
        REFUSED
    }

    /**
     * One check's result.
     *
     * @param outcome how the server treated the setting; non-null
     * @param source where the verdict came from, such as {@code test}; non-null
     * @param time when it was found; non-null
     */
    public record Verdict(Outcome outcome, String source, Instant time) {
        public Verdict {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(time, "time");
        }
    }

    /**
     * The thinking check's result.
     *
     * @param switchName the request field that worked, or null when none did
     * @param verdict the outcome; non-null
     */
    public record ThinkingVerdict(@Nullable String switchName, Verdict verdict) {
        public ThinkingVerdict {
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /**
     * Nothing checked.
     *
     * @return verdicts with every part null
     */
    public static ProbeVerdicts none() {
        return new ProbeVerdicts(null, null, null, null);
    }

    /**
     * Lays newer verdicts over these: a check that ran replaces what was kept for it, one that did not run leaves the
     * kept verdict, so a recognised engine's two checks never erase a generic server's earlier four.
     *
     * @param newer the verdicts just found; non-null, null parts for checks that did not run
     * @return the combined verdicts
     */
    public ProbeVerdicts mergedWith(final ProbeVerdicts newer) {
        Objects.requireNonNull(newer, "newer");
        return new ProbeVerdicts(
                newer.answerSchema() != null ? newer.answerSchema() : answerSchema,
                newer.thinkingSwitch() != null ? newer.thinkingSwitch() : thinkingSwitch,
                newer.extraSamplers() != null ? newer.extraSamplers() : extraSamplers,
                newer.capField() != null ? newer.capField() : capField);
    }
}
