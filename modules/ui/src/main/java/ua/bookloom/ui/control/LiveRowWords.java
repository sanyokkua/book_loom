package ua.bookloom.ui.control;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveRow;

/**
 * How the list and the detail word a live row: a segment by its locator and its source, a kept chapter summary as
 * "Chapter {n} summarised" and its text, a proofread window as "window {span}" and its text after. The state holds no catalogue, so a summary row carries only the chapter's
 * number, and its wording happens here. Nothing here logs: the cells call it on every redraw.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LiveRowWords {

    private static final Pattern MARK = Pattern.compile("⟦[^⟧]*⟧");

    static boolean isSummary(final LiveRow row) {
        return row.stage() == LiveRow.Stage.SUMMARISED;
    }

    static boolean isWindow(final LiveRow row) {
        return row.stage() == LiveRow.Stage.PROOFREAD;
    }

    static String locator(final LiveRow row, final Messages messages) {
        if (isSummary(row)) {
            return messages.get(MessageKey.LIVE_ROW_CHAPTER_SUMMARISED, row.locator());
        }
        return isWindow(row) ? messages.get(MessageKey.LIVE_ROW_WINDOW, row.locator()) : row.locator();
    }

    /** The row's whole text: a segment's source, a summary's text, or a proofread window's text after it. */
    static String text(final LiveRow row) {
        return isSummary(row) || isWindow(row) ? Objects.requireNonNullElse(row.target(), "") : row.displaySource();
    }

    /** The row's text as one line of words: the inline marks are left out and line breaks become spaces. */
    static String oneLine(final LiveRow row) {
        return MARK.matcher(text(row)).replaceAll("").replaceAll("\\s+", " ").strip();
    }
}
