package ua.bookloom.llm.profile;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Decides the reasoning level one call is sent from the model's profile (design D20). The order is fixed: a control the
 * model or its server does not declare is never sent; then the person's thinking choice for the model; then the call's
 * own intent, which is Off for every call BookLoom makes. A model that thinks on every call, or a call sent to think,
 * gets the {@link ReasoningReserve} on its cap.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReasoningChoice {

    // The calls "Allow on proofreading and names" lets think: the Proofread stage's window reads and every call of the
    // names pass.
    private static final Set<CallKind> PROOFREAD_AND_NAMES = EnumSet.of(
            CallKind.PROOFREAD,
            CallKind.PRESCAN,
            CallKind.REVIEW_TERMS,
            CallKind.SUGGEST_TARGETS,
            CallKind.TERM_READING,
            CallKind.TERM_CHOICE,
            CallKind.NAME_KNOWLEDGE);
    private static final Set<String> OFF_WORDS = Set.of("false", "off", "none");

    /**
     * What one call is sent.
     *
     * @param level the level sent, or null when no reasoning control is sent
     * @param reserves whether the call gets room for reasoning on its cap
     * @param leftOut whether a control was left out because the model or its server declares none that can be sent
     * @param hosted whether the model runs on the provider's hardware, which gives its reserve more room
     */
    public record Decision(@Nullable ReasoningLevel level, boolean reserves, boolean leftOut, boolean hosted) {

        /** A decision for a model on the person's own computer. */
        public Decision(final @Nullable ReasoningLevel level, final boolean reserves, final boolean leftOut) {
            this(level, reserves, leftOut, false);
        }
    }

    /**
     * Decides the level a call is sent.
     *
     * @param profile the model's resolved profile; non-null
     * @param request the call with its intent and kind; non-null
     * @return the decision; never null
     */
    public static Decision decide(final ModelProfile profile, final ChatRequest request) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(request, "request");
        final ReasoningControl control = profile.reasoning();
        final Decision chosen =
                switch (control.kind()) {
                    case NONE, UNKNOWN -> new Decision(null, false, true);
                    case UNSWITCHABLE -> new Decision(null, true, true);
                    case ALWAYS -> lowestOf(control);
                    case BOOLEAN, LEVELS -> switched(control, profile.thinking(), request);
                };
        final Decision decision = new Decision(
                chosen.level(),
                chosen.reserves(),
                chosen.leftOut(),
                profile.context().hosted());
        log.debug(
                "Reasoning decided kind={} intent={} override={} control={} offValue={} sent={} reserve={}",
                request.callKind(),
                request.reasoningEnabled(),
                profile.thinking(),
                control.kind(),
                control.offValue(),
                decision.level(),
                decision.reserves());
        return decision;
    }

    /**
     * The request with the decision applied: the level sent and, when the call thinks, the reserve added to its cap and
     * its expected output; the cap and the reserve together stay inside what the request's window leaves after the
     * prompt ({@link OutputRoom}).
     *
     * @param decision what {@link #decide} chose; non-null
     * @param request the call; non-null
     * @return the request as it goes to the provider client
     */
    public static ChatRequest applied(final Decision decision, final ChatRequest request) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(request, "request");
        final int wanted = decision.reserves() ? ReasoningReserve.tokens(request.source(), decision.hosted()) : 0;
        final OutputRoom.@Nullable Split split = wanted > 0 ? OutputRoom.split(request, wanted) : null;
        final int reserve = split == null ? 0 : split.reserve();
        log.debug(
                "Reasoning applied kind={} level={} reserve={} wanted={} source={}",
                request.callKind(),
                decision.level(),
                reserve,
                wanted,
                request.source());
        // The answer cap shrinks with the reserve when both would not fit the window beside the prompt.
        final ChatRequest capped = split == null || Objects.equals(split.cap(), request.maxOutputTokens())
                ? request
                : request.withModelSettings(request.temperature(), split.cap(), request.outputField());
        return capped.withReasoning(decision.level(), reserve);
    }

    /**
     * Whether a model's built-in row says it always reasons at a level it can be held to (gpt-oss), read from its name
     * alone, for the Test's thinking check, which then asks by that level instead of trying each off switch.
     *
     * @param engine the provider's effective engine; non-null
     * @param modelId the model id; non-null
     * @return {@code true} if the model always reasons and has a lowest level, {@code false} otherwise
     */
    public static boolean reasonsAtALevel(final ServerEngine engine, final String modelId) {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(modelId, "modelId");
        final ReasoningControl control = new ModelProfiles(new BuiltInRows())
                .resolve(engine, modelId, ModelFacts.unknown(), ModelSettings.defaults("", modelId))
                .profile()
                .reasoning();
        final boolean atLevel = control.kind() == ReasoningControl.Kind.ALWAYS
                && lowestOf(control).level() != null;
        log.debug("Reasoning at a level engine={} model={} atLevel={}", engine, modelId, atLevel);
        return atLevel;
    }

    /**
     * The value a model that always reasons is held at: its lowest level when it has one.
     *
     * @param control the model's reasoning control; non-null
     * @return the lowest level's value, or null when nothing is sent
     */
    static @Nullable String lowestSent(final ReasoningControl control) {
        final String lowest = control.offValue();
        return lowest != null && !isOffWord(lowest) ? lowest : null;
    }

    // A model that always reasons is sent its lowest level when it has one, else nothing.
    private static Decision lowestOf(final ReasoningControl control) {
        final boolean hasLevel = lowestSent(control) != null;
        return new Decision(hasLevel ? ReasoningLevel.LOW : null, true, !hasLevel);
    }

    private static Decision switched(
            final ReasoningControl control, final ThinkingOverride override, final ChatRequest request) {
        final @Nullable ReasoningLevel asked = request.reasoningEnabled();
        final ReasoningLevel intent = asked == null ? ReasoningLevel.OFF : asked;
        final @Nullable CallKind kind = request.callKind();
        final ReasoningLevel wanted =
                switch (override) {
                    case RECOMMENDED -> intent;
                    case ALLOW_PROOFREAD_AND_NAMES ->
                        kind != null && PROOFREAD_AND_NAMES.contains(kind) ? ReasoningLevel.ON : intent;
                    case ALWAYS -> ReasoningLevel.ON;
                };
        // Off is the model's own off value; a model with levels and no off value is held at its lowest level.
        final ReasoningLevel sent = wanted == ReasoningLevel.OFF ? offLevel(control) : wanted;
        return new Decision(sent, sent.isThinking(), false);
    }

    private static ReasoningLevel offLevel(final ReasoningControl control) {
        final String off = control.offValue();
        return off == null || isOffWord(off) ? ReasoningLevel.OFF : ReasoningLevel.LOW;
    }

    private static boolean isOffWord(final String value) {
        return OFF_WORDS.contains(value.toLowerCase(Locale.ROOT));
    }
}
