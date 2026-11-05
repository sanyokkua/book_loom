package ua.bookloom.pipeline.context;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;

/** Which fixed part a kind of call sends (design D8): the TRANSLATE frame, the ANALYSIS frame, or none of the builder's. */
enum FrameKind {
    /** Blocks 1–13: drafts, format re-asks and repairs. */
    TRANSLATE,
    /** Blocks 1, 2 and 7 and the kind's user blocks: the names pass, the brief, the file name, the summary. */
    ANALYSIS,
    /** A call the builder does not plan. */
    NONE;

    static FrameKind of(final CallKind kind) {
        return switch (kind) {
            case DRAFT, REPAIR, STRUCTURAL_REPAIR -> TRANSLATE;
            case PRESCAN,
                    REVIEW_TERMS,
                    SUGGEST_TARGETS,
                    SUMMARY,
                    FILE_NAME,
                    BRIEF_SUGGESTION,
                    BOOK_KNOWLEDGE,
                    BRIEF_SYNTHESIS,
                    TERM_READING,
                    TERM_CHOICE,
                    NAME_KNOWLEDGE -> ANALYSIS;
            case REVIEW, DIRECTED_FIX, REVISION -> NONE;
        };
    }

    /**
     * Why a call of this frame cannot be planned with {@code focus}.
     *
     * @param focus the call's items
     * @return the reason, or null when it can be planned
     */
    @Nullable
    String refusal(final Focus focus) {
        if (this == NONE) {
            return "This kind of call is not built by the context builder.";
        }
        return this == TRANSLATE && focus.items().isEmpty() ? "A translation call needs at least one item." : null;
    }
}
