package ua.bookloom.document.md;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Splits a leading {@code ---}-delimited frontmatter block off a Markdown file, and reads the two scalar keys the
 * import card needs out of it.
 *
 * <p><strong>Why it is split off before the parser sees it.</strong> CommonMark has no notion of frontmatter: a
 * leading {@code ---} block parses as a thematic break followed by a setext heading, which would make the keys and
 * values translatable prose. Splitting is also stronger than the front-matter extension would be, because
 * re-emitting the block verbatim is then unconditional — nothing re-renders it, so nothing can normalise it.
 *
 * <p><strong>Why only the first line can open a block.</strong> A {@code ---} elsewhere is an ordinary thematic
 * break, and real documents use it that way — the surveyed corpus has 23 of them, plus two files that show
 * frontmatter <em>inside</em> a code fence as an example. Because the split only ever examines the first line,
 * neither can be mistaken for frontmatter.
 *
 * <p><strong>Why a flat scan and not a YAML parser.</strong> The only values needed are two scalars at the top
 * level. Adding a YAML dependency to {@code :document} to read two strings would be the largest dependency in the
 * module serving its smallest purpose. A key that is absent, nested, or not a plain scalar simply yields nothing —
 * absence is representable, and inventing a value is the failure mode the requirement forbids.
 *
 * <p>Reading and preserving are independent: the block is re-emitted byte-for-byte whatever the scan finds, and no
 * key or value ever becomes a segment.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Frontmatter {

    private static final String DELIMITER = "---";

    /**
     * Splits {@code text} into its frontmatter block and the body that follows.
     *
     * @param text the whole file, decoded and with any byte-order mark already removed
     * @return the split; the block is empty when the file does not open with one
     */
    static Split split(String text) {
        Objects.requireNonNull(text, "text");
        if (!opensWithDelimiter(text)) {
            return new Split("", text);
        }
        final int closing = findClosingDelimiter(text);
        if (closing < 0) {
            return new Split("", text);
        }
        return new Split(text.substring(0, closing), text.substring(closing));
    }

    /** Only an exact {@code ---} line at the very start opens a block. */
    private static boolean opensWithDelimiter(String text) {
        final int firstLineEnd = endOfLine(text, 0);
        return DELIMITER.equals(text.substring(0, firstLineEnd).strip()) && firstLineEnd < text.length();
    }

    /** The character index just past the closing delimiter's own line, or {@code -1} if there is none. */
    private static int findClosingDelimiter(String text) {
        int lineStart = nextLineStart(text, 0);
        while (lineStart < text.length()) {
            final int lineEnd = endOfLine(text, lineStart);
            if (DELIMITER.equals(text.substring(lineStart, lineEnd).strip())) {
                return nextLineStart(text, lineStart);
            }
            lineStart = nextLineStart(text, lineStart);
        }
        return -1;
    }

    private static int endOfLine(String text, int from) {
        final int newline = text.indexOf('\n', from);
        return newline < 0 ? text.length() : newline;
    }

    private static int nextLineStart(String text, int from) {
        final int newline = text.indexOf('\n', from);
        return newline < 0 ? text.length() : newline + 1;
    }

    /**
     * The top-level scalar keys the import card needs.
     *
     * @param block the frontmatter block as split off, possibly empty
     * @return an ordered map carrying only {@code title} and {@code lang} where the block declares them as plain
     *     top-level scalars; never null, possibly empty
     */
    static Map<String, String> scan(String block) {
        final Map<String, String> found = new LinkedHashMap<>();
        for (final String line : block.split("\n", -1)) {
            scanLine(line, found);
        }
        return found;
    }

    private static void scanLine(String line, Map<String, String> found) {
        if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || DELIMITER.equals(line.strip())) {
            return;
        }
        final int colon = line.indexOf(':');
        if (colon <= 0) {
            return;
        }
        final String key = line.substring(0, colon).strip();
        final String value = unquote(line.substring(colon + 1).strip());
        if (!value.isEmpty() && ("title".equals(key) || "lang".equals(key))) {
            found.put(key, value);
        }
    }

    private static String unquote(String value) {
        final boolean quoted = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")));
        return quoted ? value.substring(1, value.length() - 1) : value;
    }

    /**
     * A file split into its frontmatter block and its body.
     *
     * @param block the frontmatter block including both delimiter lines and the newline after the closing one;
     *     empty when the file has none
     * @param body everything after it — the only part the CommonMark parser ever sees
     */
    record Split(String block, String body) {

        Split {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(body, "body");
        }

        /**
         * The character offset the body starts at within the whole file, which every source span must be shifted
         * by before it can address the original buffer.
         *
         * @return the body's start offset in characters
         */
        int bodyCharOffset() {
            return block.length();
        }
    }

    /**
     * The declared language a frontmatter block carries.
     *
     * @param scanned the result of {@link #scan}
     * @return the {@code lang} value, or {@code null} when the block declares none
     */
    static @Nullable String declaredLang(Map<String, String> scanned) {
        return scanned.get("lang");
    }
}
