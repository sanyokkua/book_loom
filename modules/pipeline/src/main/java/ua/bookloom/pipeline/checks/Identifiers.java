package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The strings no language owns and a translation must keep character for character: a UUID, a long hexadecimal
 * string, an ISBN, a web address, an e-mail address. The checks that judge language, script or numbers read a text
 * with them {@linkplain #masked masked}, and {@link IdentifierCheck} holds the translation to writing each one as the
 * source does.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Identifiers {

    private static final String HEX = "[0-9a-fA-F]";
    private static final int MIN_HEX_DIGITS = 8;
    private static final char FILL = '_';
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?)\\]}»”’\"'>]+$");

    /** One identifier; the web address and the e-mail alternatives take the rest of the non-space run. */
    static final Pattern ANY = Pattern.compile("(?i)(?:https?://\\S+|www\\.\\S+|[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+"
            + "|\\b" + HEX + "{8}-" + HEX + "{4}-" + HEX + "{4}-" + HEX + "{4}-" + HEX + "{12}\\b"
            + "|\\bisbn(?:-1[03])?:?\\s*[\\dx][\\dx -]{8,16}[\\dx]\\b"
            + "|\\b97[89](?:[- ]?\\d){10}\\b"
            + "|\\b0x" + HEX + "+\\b"
            + "|\\b(?=" + HEX + "*\\d)(?=" + HEX + "*[a-f])" + HEX + "{" + MIN_HEX_DIGITS + ",}\\b)");

    /** The text with each identifier overwritten by filler of the same length, so every span keeps its place. */
    static String masked(final String text) {
        return ANY.matcher(text).replaceAll(found -> String.valueOf(FILL).repeat(found.end() - found.start()));
    }

    /** The identifiers of the text in order, each without the punctuation that closes the sentence after it. */
    static List<String> in(final String text) {
        final List<String> found = new ArrayList<>();
        final Matcher matcher = ANY.matcher(text);
        while (matcher.find()) {
            final String trimmed = TRAILING_PUNCTUATION.matcher(matcher.group()).replaceFirst("");
            if (!trimmed.isEmpty()) {
                found.add(trimmed);
            }
        }
        return found;
    }
}
