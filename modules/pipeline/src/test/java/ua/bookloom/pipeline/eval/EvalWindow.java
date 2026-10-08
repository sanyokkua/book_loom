package ua.bookloom.pipeline.eval;

import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.pipeline.context.ContextBudget;

/**
 * The window an eval sizes its requests against, chosen the way a run chooses it: the context length the provider
 * reports for the model, limited to the run's default, unless {@code BOOKLOOM_EVAL_WINDOW} says otherwise. The
 * detection is the run's own {@link ModelCapabilities#detectedTokens} and {@link ContextBudget#windowFor}; it asks the
 * provider once per process, and only when the eval's endpoint is set, so an offline unit test never touches a network.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalWindow {

    /** Holds the one choice, made and logged on first use. */
    private static final class Chosen {
        private static final int WINDOW = choose();
    }

    /** The window for this process: the explicit override, else the detected length capped as a run caps it. */
    static int window() {
        return Chosen.WINDOW;
    }

    private static int choose() {
        final String asked = System.getenv("BOOKLOOM_EVAL_WINDOW");
        final boolean overridden = asked != null && !asked.isBlank();
        final Integer detected = overridden ? null : detect();
        final int window = resolve(asked, detected);
        log.info(
                "Eval window {} tokens ({})",
                window,
                overridden ? "BOOKLOOM_EVAL_WINDOW" : detected == null ? "default" : "detected");
        return window;
    }

    /**
     * The window for an asked-for value and a detected length.
     *
     * @param asked the override text, or null or blank for none
     * @param detected the length the provider reported, or null
     * @return the override, else the detected length limited to the run's default, else the default
     */
    static int resolve(@Nullable final String asked, @Nullable final Integer detected) {
        final Integer override = asked == null || asked.isBlank() ? null : Integer.valueOf(asked.strip());
        return ContextBudget.windowFor(detected, override);
    }

    /** What the provider reports for the model, or null when the eval has no endpoint or the provider says nothing. */
    private static @Nullable Integer detect() {
        if (System.getenv("BOOKLOOM_EVAL_URL") == null) {
            return null;
        }
        final String model = System.getenv().getOrDefault("BOOKLOOM_EVAL_MODEL", PromptEvalTest.DEFAULT_MODEL);
        final Optional<Integer> tokens = PromptEvalTest.registeredProvider()
                .getInstance(ModelCapabilities.class)
                .detectedTokens(new ModelSelection(PromptEvalTest.PROVIDER_ID, model));
        log.info("Eval provider reports context length {} for {}", tokens.orElse(null), model);
        return tokens.orElse(null);
    }
}
