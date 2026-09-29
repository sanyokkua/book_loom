package ua.bookloom.pipeline.prompt;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

/**
 * One owner per model call's template files, call kind, response-format name and sampling temperature, so no call
 * goes out at a temperature the design never chose.
 */
// Slots is a record of defensively copied immutable sets; Error Prone cannot see that without @Immutable.
@SuppressWarnings("ImmutableEnumChecker")
public enum PromptName {

    /** The first translation attempt; a review retry may ask for the lower temperature. */
    DRAFT(
            "draft",
            CallKind.DRAFT,
            "draft",
            DraftSchema.SCHEMA,
            0.2,
            0.1,
            new Slots(Set.of("source", "target", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(
                    Set.of("source", "target", "tokens", "text"),
                    Set.of("summary", "glossaryTerms", "memoryHint", "precedingTargets"))),

    /** The correction call after a reply is not the required JSON object. */
    STRUCTURAL_REPAIR(
            "structural-repair",
            CallKind.STRUCTURAL_REPAIR,
            "structural-repair",
            DraftSchema.SCHEMA,
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedReply", "diagnostic"), Set.of())),

    /** The correction call after a target fails the placeholder-multiset gate. */
    PLACEHOLDER_REPAIR(
            "placeholder-repair",
            CallKind.PLACEHOLDER_REPAIR,
            "placeholder-repair",
            DraftSchema.SCHEMA,
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedTarget", "tokens"), Set.of("gateNote"))),

    /** Scores a chunk's drafted pairs once per chunk, so it states no expected output of its own. */
    JUDGE(
            "judge",
            CallKind.JUDGE,
            "judge",
            JudgeSchema.SCHEMA,
            0.1,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(Set.of("pairs"), Set.of("glossaryTerms"))),

    /** The self-heal call that rewrites one rejected target to fix its concrete, named findings. */
    DIRECTED_FIX(
            "directed-fix",
            CallKind.DIRECTED_FIX,
            "directed-fix",
            DraftSchema.SCHEMA,
            0.2,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(Set.of("source", "text", "findings"), Set.of("expectedTokens"))),

    /** The reflection critique before a rewrite, for a vague quality concern with no concrete finding. */
    REFLECT(
            "reflect",
            CallKind.REFLECT,
            "reflect",
            ReflectSchema.SCHEMA,
            0.35,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(Set.of("source", "text"), Set.of())),

    /** The rewrite that consumes reflect's critique, at a higher temperature to escape a bad local phrasing. */
    IMPROVE(
            "improve",
            CallKind.IMPROVE,
            "improve",
            DraftSchema.SCHEMA,
            0.35,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(Set.of("source", "text"), Set.of("issues"))),

    /** The optional monolingual smoothing pass run only on a borderline improved target. */
    POLISH(
            "polish",
            CallKind.POLISH,
            "polish",
            DraftSchema.SCHEMA,
            0.2,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage", "styleSheet", "foreignPassageRule"), Set.of()),
            new Slots(Set.of("source", "text"), Set.of())),

    /** The name and term proposal the person asks for; a batch of candidates per call, never run by itself. */
    PRESCAN(
            "prescan",
            CallKind.PRESCAN,
            "prescan",
            PrescanSchema.SCHEMA,
            0.2,
            null,
            new Slots(Set.of("sourceLanguage", "targetLanguage"), Set.of("styleSheet", "foreignPassageRule")),
            new Slots(Set.of("candidates"), Set.of("existingTerms")));

    private final String resourceBaseName;
    private final CallKind callKind;
    private final String responseFormatName;
    private final String responseSchema;
    private final double temperature;
    private final @Nullable Double lowerTemperature;
    private final @Nullable Slots systemSlots;
    private final Slots userSlots;

    PromptName(
            final String resourceBaseName,
            final CallKind callKind,
            final String responseFormatName,
            final String responseSchema,
            final double temperature,
            @Nullable final Double lowerTemperature,
            @Nullable final Slots systemSlots,
            final Slots userSlots) {
        this.resourceBaseName = resourceBaseName;
        this.callKind = callKind;
        this.responseFormatName = responseFormatName;
        this.responseSchema = responseSchema;
        this.temperature = temperature;
        this.lowerTemperature = lowerTemperature;
        this.systemSlots = systemSlots;
        this.userSlots = userSlots;
    }

    /** The template files' name without the {@code .system.prompt}/{@code .user.prompt} suffix. */
    public String resourceBaseName() {
        return resourceBaseName;
    }

    /** The kind of call this template serves. */
    public CallKind callKind() {
        return callKind;
    }

    /** The response-format name the pseudo model switches on. */
    public String responseFormatName() {
        return responseFormatName;
    }

    /** The JSON schema this call's structured response must match. */
    public String responseSchema() {
        return responseSchema;
    }

    /**
     * Returns this call's sampling temperature.
     *
     * @param lower {@code true} to ask for the lower temperature a retry uses
     * @return the temperature to send
     * @throws IllegalArgumentException if {@code lower} is asked for and this call has no lower temperature
     */
    public double temperature(final boolean lower) {
        if (!lower) {
            return temperature;
        }
        if (lowerTemperature == null) {
            throw new IllegalArgumentException(name() + " has no lower temperature");
        }
        return lowerTemperature;
    }

    /** The system template's slots, or empty when the call reuses another call's system message. */
    public Optional<Slots> systemSlots() {
        return Optional.ofNullable(systemSlots);
    }

    /** The user template's slots. */
    public Slots userSlots() {
        return userSlots;
    }

    /**
     * The slots one template file may use.
     *
     * @param required slots the file must contain as {@code {{slot}}}
     * @param optional slots the file may contain, alone or as a {@code {{#slot}}} block dropped when empty
     */
    public record Slots(Set<String> required, Set<String> optional) {

        /** Copies both sets so a declaration can never change after construction. */
        public Slots {
            required = Set.copyOf(Objects.requireNonNull(required, "required"));
            optional = Set.copyOf(Objects.requireNonNull(optional, "optional"));
        }

        boolean declares(final String slot) {
            return required.contains(slot) || optional.contains(slot);
        }
    }
}
