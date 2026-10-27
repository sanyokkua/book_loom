package ua.bookloom.ui;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The English catalogue a test words things from. */
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below
// (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EnglishMessages {

    /** A new catalogue that answers in English. */
    public static Messages create() {
        return new Messages((LocaleProvider) () -> Locale.ENGLISH);
    }
}
