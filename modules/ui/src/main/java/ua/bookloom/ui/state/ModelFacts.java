package ua.bookloom.ui.state;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ReasoningControl;

/**
 * What the program says about the chosen model, shown above its settings.
 *
 * @param contextLength the tokens the model can read at once, or null when the provider did not say
 * @param loaded the tokens it is loaded with now, or null when unknown (Ollama only)
 * @param structured whether the last inference test of this session got a structured answer, or null when the model
 *     was not tested this session
 * @param loading whether the provider is still being asked
 * @param thinking the thinking control the model offers, as its profile resolved it, or null until it is reported
 */
public record ModelFacts(
        @Nullable Integer contextLength,
        @Nullable Integer loaded,
        @Nullable Boolean structured,
        boolean loading,
        @Nullable ReasoningControl thinking) {

    /** Nothing known yet, nothing being asked. */
    public static final ModelFacts NONE = new ModelFacts(null, null, null, false, null);
}
