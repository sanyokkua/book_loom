package ua.bookloom.llm.profile;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ThinkingOverride;

/**
 * Writes the profile a run starts with to the always-on run log, as choices and counts only: with it a wrong
 * translation can be traced to how the model was handled. The model id is the person's choice, not book text, so it is
 * written; nothing of the book ever is.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class ProfileLog {

    private static final String NO_VALUE = "none";

    /**
     * Writes the one {@code profile resolved} line of a run.
     *
     * @param modelId the model id; non-null
     * @param resolved the model's resolved handling; non-null
     * @param windowOverride the window the person set for the model, or null when none is set; positive when set
     */
    public static void runStart(
            final String modelId, final ResolvedProfile resolved, final @Nullable Integer windowOverride) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(resolved, "resolved");
        final ModelProfile profile = resolved.profile();
        log.info(
                "profile resolved model={} match={} quant={} reasoning={} sampling={} window={} batch={}",
                modelId,
                resolved.match(),
                resolved.quant() == null ? "unknown" : resolved.quant(),
                reasoning(profile),
                profile.sampling().mode() == SamplingOverride.Mode.CUSTOM ? "custom" : "profile",
                ModelWindow.of(profile.context(), windowOverride),
                profile.batchSize());
    }

    // What a draft is sent: thinking off by its off value, unless the person chose Always; a model that always reasons
    // is held at its lowest level when it has one; a model with no switch gets no control at all.
    private static String reasoning(final ModelProfile profile) {
        final ReasoningControl control = profile.reasoning();
        return switch (control.kind()) {
            case NONE, UNKNOWN -> "NONE(" + NO_VALUE + ")";
            case ALWAYS -> "ALWAYS(" + valueOr(ReasoningChoice.lowestSent(control)) + ")";
            case UNSWITCHABLE -> "ALWAYS(" + NO_VALUE + ")";
            case BOOLEAN ->
                profile.thinking() == ThinkingOverride.ALWAYS ? "ON(true)" : "OFF(" + valueOr(control.offValue()) + ")";
            case LEVELS ->
                profile.thinking() == ThinkingOverride.ALWAYS
                        ? "ON(" + valueOr(control.serverDefault()) + ")"
                        : "OFF(" + valueOr(control.offValue()) + ")";
        };
    }

    private static String valueOr(final @Nullable String value) {
        return value == null ? NO_VALUE : value;
    }
}
