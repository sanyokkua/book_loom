package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProbeVerdicts;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * What the program says about the chosen model, shown above its settings.
 *
 * @param contextLength the tokens the model can read at once, or null when the provider did not say
 * @param loaded the tokens it is loaded with now, or null when unknown or the engine does not report one
 * @param verdict how BookLoom rates the model (a {@link ua.bookloom.api.llm.ModelReport} verdict), or null until it is
 *     reported
 * @param warnings what the person should know before choosing the model; never null, may be empty
 * @param verdicts what the last Advanced features check found for this model, kept for the answers rows
 * @param structured whether the last inference test of this session got a structured answer, or null when the model
 *     was not tested this session
 * @param loading whether the provider is still being asked
 * @param thinking the thinking control the model offers, as its profile resolved it, or null until it is reported
 */
public record ModelFacts(
        @Nullable Integer contextLength,
        @Nullable Integer loaded,
        @Nullable String verdict,
        List<String> warnings,
        ProbeVerdicts verdicts,
        @Nullable Boolean structured,
        boolean loading,
        @Nullable ReasoningControl thinking) {

    /** Nothing known yet, nothing being asked. */
    public static final ModelFacts NONE =
            new ModelFacts(null, null, null, List.of(), ProbeVerdicts.none(), null, false, null);

    public ModelFacts {
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        Objects.requireNonNull(verdicts, "verdicts");
    }

    /**
     * The same facts with another structured-answer note.
     *
     * @param answered what the last inference test found, or null when not tested
     * @return the facts with only that part changed
     */
    public ModelFacts withStructured(final @Nullable Boolean answered) {
        return new ModelFacts(contextLength, loaded, verdict, warnings, verdicts, answered, loading, thinking);
    }

    /**
     * The same facts with the stored advanced-check verdicts.
     *
     * @param stored what the last Advanced features check found; non-null
     * @return the facts with only that part changed
     */
    public ModelFacts withVerdicts(final ProbeVerdicts stored) {
        return new ModelFacts(
                contextLength,
                loaded,
                verdict,
                warnings,
                Objects.requireNonNull(stored, "stored"),
                structured,
                loading,
                thinking);
    }
}
