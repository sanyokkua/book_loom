package ua.bookloom.document.md;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Every ASCII punctuation character CommonMark permits a backslash escape before — split out of
 * {@link MarkdownEscaper} to keep that class within its size budget; this logic has no other caller. A backslash
 * before any other character (an indent space, a letter) is not an escape at all and renders as a literal
 * backslash, so {@link MarkdownEscaper} only ever escapes a position this class confirms.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AsciiPunctuation {

    private static final String CHARACTERS = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    static boolean isPunctuation(char c) {
        return CHARACTERS.indexOf(c) >= 0;
    }
}
