package ua.bookloom.pipeline.index;

/** Whether a mention must be written the way the name is. */
public enum Case {
    /** Each word must be written exactly as the name writes it: {@code Mark} is a mention, {@code mark} is not. */
    SENSITIVE,
    /** Letter case is ignored. */
    INSENSITIVE
}
