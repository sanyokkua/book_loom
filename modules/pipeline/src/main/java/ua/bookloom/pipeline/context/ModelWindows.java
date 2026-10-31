package ua.bookloom.pipeline.context;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ContextPolicy;
import ua.bookloom.api.llm.ModelWindow;

/**
 * The window a pipeline action sizes its calls for, read from the model it calls (design D20): the model's profile
 * holds what the server said about the window when the action began, and the person's window for the model wins. A
 * context length the caller read itself stands in only where the profile knows none (a model built without the
 * session's facts).
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ModelWindows {

    /**
     * The one window every call of the action to this model uses.
     *
     * @param model the model the action calls; never null
     * @param detected the context length the caller read from the provider, or null when it read none
     * @return the window in tokens, as {@link ModelWindow#of} decides it; positive
     */
    public static int of(final ChatModel model, final @Nullable Integer detected) {
        Objects.requireNonNull(model, "model");
        final ContextPolicy policy = policyOf(model, detected);
        final Integer override = model.contextWindowOverride();
        final int window = ModelWindow.of(policy, override);
        log.debug(
                "Model window window={} override={} detected={} loaded={} hardCap={} callerDetected={}",
                window,
                override,
                policy.detected(),
                policy.loaded(),
                policy.hardCap(),
                detected);
        return window;
    }

    /**
     * What is known about the model's window, read once from its profile.
     *
     * @param model the model the action calls; never null
     * @param detected the context length the caller read from the provider, or null; it stands in where the profile
     *     knows none
     * @return the model's window facts; never null
     */
    public static ContextPolicy policyOf(final ChatModel model, final @Nullable Integer detected) {
        Objects.requireNonNull(model, "model");
        final ContextPolicy known = model.profile().context();
        return known.detected() == null && detected != null
                ? new ContextPolicy(known.hardCap(), known.outputMax(), detected, known.loaded())
                : known;
    }
}
