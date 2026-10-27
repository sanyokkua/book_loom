package ua.bookloom.api.document;

/**
 * What {@link BookInspector#inspect} found when it looked at a candidate file before opening it.
 *
 * <p>A refusing verdict ({@link #DRM_PROTECTED}, {@link #UNSUPPORTED}) is a normal answer from {@code inspect} —
 * not a failed {@link ua.bookloom.api.Result}. Actually opening such a book through {@link DocumentPort#open} still
 * fails, carrying {@code ErrorCode.validation}; {@code inspect} exists so the import screen can tell those two
 * refusals apart from each other and from a genuinely corrupt file (ADR-0039).
 */
public enum InspectionVerdict {

    /** The file is a supported format and can be opened. */
    READABLE,

    /** The file is encrypted by a DRM scheme this application does not decrypt. */
    DRM_PROTECTED,

    /** The file's type is not one of the four formats this application parses. */
    UNSUPPORTED,

    /**
     * The file's name promises one of the four formats, but its content is not that format (an {@code .epub} that is
     * no archive, an {@code .fb2} with no FictionBook root): the file is damaged rather than of another type, so the
     * refusal names the format it could not be read as.
     */
    DAMAGED,

    /**
     * The book opened, but its body holds no word to translate (an empty or whitespace-only file, a body with no
     * paragraph). Never answered by {@link BookInspector#inspect}, which does not read the text: the import answers it
     * after opening the book, and creates no project for it.
     */
    NO_TEXT
}
