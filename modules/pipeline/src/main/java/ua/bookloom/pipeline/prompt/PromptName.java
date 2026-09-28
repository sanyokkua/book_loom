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
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedReply", "diagnostic"), Set.of())),

    /** The correction call after a target fails the placeholder-multiset gate. */
    PLACEHOLDER_REPAIR(
            "placeholder-repair",
            CallKind.PLACEHOLDER_REPAIR,
            "placeholder-repair",
            0.2,
            null,
            null,
            new Slots(Set.of("rejectedTarget", "tokens"), Set.of()));

    private final String resourceBaseName;
    private final CallKind callKind;
    private final String responseFormatName;
    private final double temperature;
    private final @Nullable Double lowerTemperature;
    private final @Nullable Slots systemSlots;
    private final Slots userSlots;

    PromptName(
            final String resourceBaseName,
            final CallKind callKind,
            final String responseFormatName,
            final double temperature,
            @Nullable final Double lowerTemperature,
            @Nullable final Slots systemSlots,
            final Slots userSlots) {
        this.resourceBaseName = resourceBaseName;
        this.callKind = callKind;
        this.responseFormatName = responseFormatName;
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
