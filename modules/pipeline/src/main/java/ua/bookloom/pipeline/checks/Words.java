package ua.bookloom.pipeline.checks;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The word patterns the text checks share, so every check agrees on what a word is. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Words {

    /** A run of letters, marks and decimal digits: where a stray letter or digit would sit inside a word. */
    static final Pattern RUN = Pattern.compile("[\\p{L}\\p{M}\\p{Nd}]+");

    /** A word as a reader sees it: letters with the apostrophes and hyphens that join its parts. */
    static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");

    static int count(final String text) {
        return (int) WORD.matcher(text).results().count();
    }
}
