package ua.bookloom.llm.provider;

/** A request capability that a provider explicitly rejected and may be retried without. */
public enum RejectedCapability {
    /** The provider rejected the requested structured response format. */
    STRUCTURED_OUTPUT,

    /** The provider rejected its native reasoning-output control. */
    REASONING_CONTROL
}
