package ua.bookloom.ui.screen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.util.text.JunkRules;

/** What the glossary table flags on a row: an empty target, and a term that is probably not a name. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryFlags {

    static boolean hasNoTarget(final GlossaryEntry entry) {
        return entry.target() == null || entry.target().isBlank();
    }

    static int junkScore(final GlossaryEntry entry) {
        return JunkRules.likelihood(entry.term());
    }

    static boolean isLikelyJunk(final GlossaryEntry entry) {
        return junkScore(entry) >= JunkRules.MARK_SCORE;
    }
}
