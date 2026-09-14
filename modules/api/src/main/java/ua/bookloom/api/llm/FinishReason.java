package ua.bookloom.api.llm;

/**
 * Normal and degraded ways a model response can finish.
 */
public enum FinishReason {

    /** The model completed the response normally. */
    STOP,

    /** The response was cut off by a length limit. */
    LENGTH,

    /** The provider reported another termination reason. */
    OTHER
}
