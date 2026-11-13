package ua.bookloom.api.llm;

/** What is known about one language for one model. */
public enum LanguageState {
    /** The model's vendor lists the language, or BookLoom's own run proved it. */
    DECLARED,
    /** A complete vendor list leaves the language out. */
    NOT_DECLARED,
    /** Nothing is stated either way. */
    UNKNOWN
}
