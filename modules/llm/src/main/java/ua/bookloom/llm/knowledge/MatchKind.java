package ua.bookloom.llm.knowledge;

/** How a language row's pattern is compared with a model name split into whole "-" and ":" segments. */
public enum MatchKind {
    /** The pattern equals the whole name; beats any prefix row. */
    EXACT,
    /** The pattern equals the first segments of the name; the longest such pattern wins. */
    PREFIX
}
