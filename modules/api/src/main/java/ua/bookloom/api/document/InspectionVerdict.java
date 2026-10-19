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
    UNSUPPORTED
}
