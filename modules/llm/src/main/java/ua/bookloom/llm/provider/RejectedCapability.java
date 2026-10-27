package ua.bookloom.llm.provider;

import java.util.Arrays;
import java.util.Optional;

/** A request capability that a provider explicitly rejected and may be retried without. */
public enum RejectedCapability {
    /** The provider rejected the requested structured response format. */
    STRUCTURED_OUTPUT("response_format", "omitted"),

    /** The provider rejected its native reasoning-output control. */
    REASONING_CONTROL("reasoning", "omitted"),

    /** The provider rejected the sampling temperature. */
    TEMPERATURE("temperature", "omitted"),

    /** The provider rejected {@code top_p}. */
    TOP_P("top_p", "omitted"),

    /** The provider rejected {@code top_k}. */
    TOP_K("top_k", "omitted"),

    /** The provider rejected {@code min_p}. */
    MIN_P("min_p", "omitted"),

    /** The provider rejected the repeat penalty. */
    REPEAT_PENALTY("repeat_penalty", "omitted"),

    /** The provider rejected the sampling seed. */
    SEED("seed", "omitted"),

    /** The provider rejected {@code max_tokens}; {@code max_completion_tokens} is sent instead. */
    OUTPUT_FIELD("max_tokens", "max_completion_tokens"),

    /** The provider rejected {@code num_ctx}. */
    NUM_CTX("num_ctx", "omitted");

    private final String settingName;
    private final String insteadDid;

    RejectedCapability(final String settingName, final String insteadDid) {
        this.settingName = settingName;
        this.insteadDid = insteadDid;
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
