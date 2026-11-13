package ua.bookloom.llm.provider;

import java.util.Arrays;
import java.util.Optional;

/** A request capability that a provider explicitly rejected and may be retried without. */
public enum RejectedCapability {
    /** The provider rejected the requested structured response format. */
    STRUCTURED_OUTPUT("response_format"),

    /** The provider rejected its native reasoning-output control. */
    REASONING_CONTROL("reasoning"),

    /** The provider rejected the sampling temperature. */
    TEMPERATURE("temperature"),

    /** The provider rejected {@code top_p}. */
    TOP_P("top_p"),

    /** The provider rejected {@code top_k}. */
    TOP_K("top_k"),

    /** The provider rejected {@code min_p}. */
    MIN_P("min_p"),

    /** The provider rejected the repeat penalty. */
    REPEAT_PENALTY("repeat_penalty"),

    /** The provider rejected the sampling seed. */
    SEED("seed"),

    /** The provider rejected {@code max_tokens}; {@code max_completion_tokens} is sent instead. */
    OUTPUT_FIELD("max_tokens", "max_completion_tokens"),

    /** The provider rejected {@code num_ctx}. */
    NUM_CTX("num_ctx"),

    /** The model's chat template has no system role; the system text goes into the first user message instead. */
    SYSTEM_ROLE("system_role", "folded"),

    /**
     * The model's chat template wants the roles to alternate; neighbouring messages of one role are merged. Not
     * remembered: the merge is made on the one call that was refused, and the person has nothing to send again.
     */
    ROLE_ORDER("role_order", "merged", false);

    private final String settingName;
    private final String insteadDid;
    private final boolean remembered;

    RejectedCapability(final String settingName) {
        this(settingName, "omitted");
    }

    RejectedCapability(final String settingName, final String insteadDid) {
        this(settingName, insteadDid, true);
    }

    RejectedCapability(final String settingName, final String insteadDid, final boolean remembered) {
        this.settingName = settingName;
        this.insteadDid = insteadDid;
        this.remembered = remembered;
    }

    /**
     * Whether the refusal is kept on the model's settings so later calls leave the setting out from the start.
     *
     * @return {@code true} if it is remembered, {@code false} if it holds for the refused call alone
     */
    public boolean isRemembered() {
        return remembered;
    }

    /**
     * The stable wire name stored in the refused-settings list.
     *
     * @return the name; never null
     */
    public String settingName() {
        return settingName;
    }

    /**
     * What is done in place of the setting, stored beside its name.
     *
     * @return a code such as {@code omitted}; never null
     */
    public String insteadDid() {
        return insteadDid;
    }

    /**
     * Finds a capability by its stored name.
     *
     * @param name a stored setting name; non-null
     * @return the capability, or empty when the name is not one
     */
    public static Optional<RejectedCapability> fromSettingName(final String name) {
        return Arrays.stream(values())
                .filter(capability -> capability.settingName.equals(name))
                .findFirst();
    }
}
