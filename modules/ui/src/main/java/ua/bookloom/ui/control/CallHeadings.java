package ua.bookloom.ui.control;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The two headings of a step's Simple view for the call it made: what the call asked and what the model gave back, in
 * the words of the work (names, terms, passages) instead of the generic "reads" and "says".
 *
 * @param input the heading of what the call asked about
 * @param output the heading of what the model answered
 */
record CallHeadings(MessageKey input, MessageKey output) {

    static final CallHeadings GENERIC = new CallHeadings(MessageKey.PROGRESS_INPUT, MessageKey.PROGRESS_OUTPUT);

    private static final CallHeadings NAMES =
            new CallHeadings(MessageKey.PROGRESS_INPUT_NAMES, MessageKey.PROGRESS_OUTPUT_NAMES);
    private static final CallHeadings TERMS =
            new CallHeadings(MessageKey.PROGRESS_INPUT_TERMS, MessageKey.PROGRESS_OUTPUT_TERMS);
    private static final CallHeadings TARGETS =
            new CallHeadings(MessageKey.PROGRESS_INPUT_TARGETS, MessageKey.PROGRESS_OUTPUT_TARGETS);
    private static final CallHeadings BRIEF =
            new CallHeadings(MessageKey.PROGRESS_INPUT_BRIEF, MessageKey.PROGRESS_OUTPUT_BRIEF);
    private static final CallHeadings REVISION =
            new CallHeadings(MessageKey.PROGRESS_INPUT_REVISION, MessageKey.PROGRESS_OUTPUT_REVISION);
    private static final CallHeadings SUMMARY =
            new CallHeadings(MessageKey.PROGRESS_INPUT_SUMMARY, MessageKey.PROGRESS_OUTPUT_SUMMARY);
    private static final CallHeadings FILE_NAME =
            new CallHeadings(MessageKey.PROGRESS_INPUT_FILE_NAME, MessageKey.PROGRESS_OUTPUT_FILE_NAME);

    /**
     * The headings for a call.
     *
     * @param kind what the call was for, or {@code null} when the step made none
     * @return the headings in the call's own words; the generic pair for a call about segments or none
     */
    static CallHeadings of(final @Nullable CallKind kind) {
        if (kind == null) {
            return GENERIC;
        }
        return switch (kind) {
            case PRESCAN, NAME_KNOWLEDGE -> NAMES;
            case REVIEW_TERMS, TERM_READING, TERM_CHOICE -> TERMS;
            case SUGGEST_TARGETS -> TARGETS;
            case BRIEF_SUGGESTION, BRIEF_SYNTHESIS, BOOK_KNOWLEDGE -> BRIEF;
            case REVISION -> REVISION;
            case CHAPTER_SUMMARY -> SUMMARY;
            case FILE_NAME -> FILE_NAME;
            case DRAFT, STRUCTURAL_REPAIR, REPAIR, REVIEW, DIRECTED_FIX -> GENERIC;
        };
    }
}
