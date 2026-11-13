package ua.bookloom.api.llm;

/** What a vendor's language list lets the program conclude about a language it does not name. */
public enum ListKind {
    /** The whole list: a language that is absent is not declared. An English-only list is complete with {@code en}. */
    COMPLETE,
    /** Only languages the vendor or a BookLoom run vouches for; absence says nothing. */
    POSITIVE_ONLY,
    /** No usable statement; every language is unknown. */
    NONE
}
