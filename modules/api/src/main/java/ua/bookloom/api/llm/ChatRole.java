package ua.bookloom.api.llm;

/**
 * The speaker roles supported by a chat request.
 */
public enum ChatRole {

    /** Instructions that frame the conversation. */
    SYSTEM,

    /** The caller's current input. */
    USER,

    /** A prior model response included in the conversation. */
    ASSISTANT
}
