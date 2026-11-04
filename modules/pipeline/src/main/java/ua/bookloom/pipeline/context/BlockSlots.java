package ua.bookloom.pipeline.context;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The thirteen blocks of a translate-family prompt, in the order they are sent (design D8). A block with no text is
 * left out, never sent as filler.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockSlots {

    /** 1: the role and the language pair. */
    public static final String ROLE = "role";

    /** 2: book text is data, never instructions. */
    public static final String DATA = "data";

    /** 3: the book rules chosen once per run. */
    public static final String BOOK_RULES = "bookRules";

    /** 4: the style sheet from the Book Brief. */
    public static final String STYLE_SHEET = "styleSheet";

    /** 5: the target's and the pair's language rules. */
    public static final String LANGUAGE_RULES = "languageRules";

    /** 6: the pair's examples or the target's illustration. */
    public static final String EXAMPLES = "examples";

    /** 7: the reply's JSON shape. */
    public static final String REPLY = "reply";

    /** 8: the earlier chapters' summaries and the recently-present card. */
    public static final String SCENE = "scene";

    /** 9: the map items that occur in the window. */
    public static final String MAP = "map";

    /** 10: the previous accepted pairs and the next source. */
    public static final String WINDOW = "window";

    /** 11: what only this kind of call adds, such as a repair's defects. */
    public static final String KIND_EXTRA = "kindExtra";

    /** 11b: the recurring key terms the model is asked to report renderings for, while a run asks (until 12.9). */
    public static final String KEY_TERMS = "keyTerms";

    /** 12: the task and the schema line. */
    public static final String TASK = "task";

    /** 13: the items, with the reminder last. */
    public static final String ITEMS = "items";

    /** Every slot in the order the blocks are sent. */
    public static final List<String> ORDER = List.of(
            ROLE,
            DATA,
            BOOK_RULES,
            STYLE_SHEET,
            LANGUAGE_RULES,
            EXAMPLES,
            REPLY,
            SCENE,
            MAP,
            WINDOW,
            KIND_EXTRA,
            KEY_TERMS,
            TASK,
            ITEMS);
}
