package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * The AI program and model the run uses, as the title bar's chip and the navigation footer both name it, so the two
 * are one fact shown twice.
 *
 * @param provider the program's display name ("Ollama"), empty while no provider is selected
 * @param modelId the chosen model ("gemma4:e4b"), empty while none is chosen
 * @param chosen {@code true} only when both a provider and a model are chosen, so a run could start
 */
public record ModelChoice(String provider, String modelId, boolean chosen) {

    /** Nothing selected yet. */
    public static final ModelChoice NONE = new ModelChoice("", "", false);

    /** Rejects a missing part. */
    public ModelChoice {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(modelId, "modelId");
    }
}
