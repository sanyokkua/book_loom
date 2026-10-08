package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.SamplingParams;

/**
 * The sampling controls a call kind sends in {@link SamplingMode#TUNED}, kept in one place. Repeat penalty 1.0
 * stops the engine penalising the copied quote marks and names a translation must repeat; a top-k, top-p and min-p
 * cut the improbable tail (a stray Latin or Thai token) off. Analysis calls cut harder because their answer is a
 * short closed vocabulary.
 */
// SamplingParams is a record of boxed numbers; Error Prone cannot see that without @Immutable.
@SuppressWarnings("ImmutableEnumChecker")
public enum SamplingProfile {

    /** Translating and repairing text: the wider tail keeps wording natural. */
    TEXT(new SamplingParams(0.9, 40, 0.05, 1.0)),

    /** Judging, scanning and summarising: a narrow tail keeps the answer on the asked vocabulary. */
    ANALYSIS(new SamplingParams(0.9, 20, 0.05, 1.0));

    private final SamplingParams params;

    SamplingProfile(final SamplingParams params) {
        this.params = params;
    }

    /** The controls this profile sends. */
    public SamplingParams params() {
        return params;
    }

    /**
     * Chooses the profile of a call.
     *
     * @param name the call's template name; never null
     * @return the profile that call kind uses
     */
    public static SamplingProfile of(final PromptName name) {
        return switch (Objects.requireNonNull(name, "name")) {
            case DRAFT, DRAFT_BATCH_JSON, STRUCTURAL_REPAIR, PLACEHOLDER_REPAIR, DIRECTED_FIX, REVISION, CONSISTENCY ->
                TEXT;
            case REVIEWER,
                    TERM_CHOICE,
                    PRESCAN,
                    REVIEW_TERMS,
                    SUGGEST_TARGETS,
                    SUSPICIOUS_WORDS,
                    FILE_NAME,
                    BRIEF_SUGGESTION,
                    SUMMARY -> ANALYSIS;
        };
    }

    /**
     * The controls to send for a call in a mode.
     *
     * @param name the call's template name; never null
     * @param mode the sampling mode; never null
     * @return the call's profile controls, or null when the mode leaves sampling to the server
     */
    public static @Nullable SamplingParams paramsFor(final PromptName name, final SamplingMode mode) {
        return switch (Objects.requireNonNull(mode, "mode")) {
            case SERVER -> null;
            case TUNED -> of(name).params();
        };
    }
}
