package ua.bookloom.ui.screen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.GlossaryEntry;

/** What Start translation asks about on a glossary row: an empty target. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryFlags {

    static boolean hasNoTarget(final GlossaryEntry entry) {
        return entry.target() == null || entry.target().isBlank();
    }
}
