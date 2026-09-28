package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Finds every top-level {@code key: value} line of a frontmatter block and says which values are text a translator
 * may see (the owner's frontmatter decision, design.md D12). Each result carries the character span of the value
 * <em>inside</em> its quotes, so a translation can be spliced in without touching the quotes, the key or a trailing
 * comment.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FrontmatterScalars {

    private static final Pattern KEY_VALUE = Pattern.compile("([^:]+?)[ \\t]*:(?:[ \\t]+(.*?))?[ \\t]*");

    private static final Pattern TRAILING_COMMENT = Pattern.compile("[ \\t]#");

    /** A value opening with one of these is a collection, block scalar, anchor, alias or tag, never a scalar. */
    private static final String STRUCTURE_OPENERS = "[{|>&*!";

    private static final String RESERVED_RULE = "reserved key";

    /**
     * One top-level line's value.
     *
     * @param key the key as written
     * @param start the value's first character inside its quotes, in the block; {@code -1} when it is no scalar
     * @param end one past its last character inside its quotes; {@code -1} when it is no scalar
     * @param quote how the value was quoted
     * @param source the value with its quote escapes read; empty when it is no scalar
     * @param rejection the rule that keeps it from being text, or {@code null} when it is text
     */
    record Scalar(
            String key,
            int start,
            int end,
            YamlQuote quote,
            String source,
            @Nullable String rejection) {

        boolean isText() {
            return rejection == null;
        }
    }

    /**
     * Reads the block's top-level lines in order.
     *
     * @param block the frontmatter block including both delimiter lines
     * @return one entry per {@code key: value} line; never null, empty for a block with none
     */
    static List<Scalar> scan(String block) {
        final List<Scalar> found = new ArrayList<>();
        int lineStart = Frontmatter.nextLineStart(block, 0);
        while (lineStart < block.length()) {
            final int lineEnd = trimCarriageReturn(block, lineStart, Frontmatter.endOfLine(block, lineStart));
            final String line = block.substring(lineStart, lineEnd);
            if ("---".equals(line.strip())) {
                break;
            }
            readLine(line, lineStart, found);
            lineStart = Frontmatter.nextLineStart(block, lineStart);
        }
        return found;
    }

    private static int trimCarriageReturn(String block, int start, int end) {
        return end > start && block.charAt(end - 1) == '\r' ? end - 1 : end;
    }

    private static void readLine(String line, int lineStart, List<Scalar> found) {
        if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || isNonKeyLine(line)) {
            return;
        }
        final Matcher matcher = KEY_VALUE.matcher(line);
        if (!matcher.matches()) {
            return;
        }
        final String key = matcher.group(1);
        final String value = matcher.group(2);
        final Scalar scalar =
                value == null ? rejected(key, "empty") : classify(key, value, lineStart + matcher.start(2));
        log.debug("frontmatter key {}: {}", key, scalar.isText() ? "text" : "not text (" + scalar.rejection() + ")");
        found.add(scalar);
    }

    private static boolean isNonKeyLine(String line) {
        return line.startsWith("#") || line.equals("-") || line.startsWith("- ") || line.startsWith("? ");
    }

    private static Scalar classify(String key, String value, int valueStart) {
        final char opener = value.charAt(0);
        if (STRUCTURE_OPENERS.indexOf(opener) >= 0) {
            return rejected(key, "not a single-line scalar");
        }
        final Scalar scalar =
                switch (opener) {
                    case '#' -> rejected(key, "empty");
                    case '"' -> quoted(key, value, valueStart, YamlQuote.DOUBLE);
                    case '\'' -> quoted(key, value, valueStart, YamlQuote.SINGLE);
                    default -> plain(key, value, valueStart);
                };
        return isReserved(key) ? withRejection(scalar, RESERVED_RULE) : scalar;
    }

    private static boolean isReserved(String key) {
        return "lang".equals(key) || "language".equals(key);
    }

    private static Scalar plain(String key, String value, int valueStart) {
        final String text = withoutComment(value);
        final String rule = ruleFor(text, YamlScalars.isTyped(text) ? "typed value" : null);
        return new Scalar(key, valueStart, valueStart + text.length(), YamlQuote.PLAIN, text, rule);
    }

    private static String withoutComment(String value) {
        final Matcher comment = TRAILING_COMMENT.matcher(value);
        return comment.find() ? value.substring(0, comment.start()).stripTrailing() : value;
    }

    private static Scalar quoted(String key, String value, int valueStart, YamlQuote quote) {
        final int close = closingQuote(value, quote);
        if (close < 0) {
            return rejected(key, "unterminated quote");
        }
        final String rest = value.substring(close + 1).strip();
        if (!rest.isEmpty() && rest.charAt(0) != '#') {
            return rejected(key, "text after the closing quote");
        }
        final String source = quote.decode(value.substring(1, close));
        return new Scalar(key, valueStart + 1, valueStart + close, quote, source, ruleFor(source, null));
    }

    private static int closingQuote(String value, YamlQuote quote) {
        final char mark = quote == YamlQuote.DOUBLE ? '"' : '\'';
        for (int i = 1; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (quote == YamlQuote.DOUBLE && c == '\\') {
                i++;
            } else if (c == mark
                    && quote == YamlQuote.SINGLE
                    && i + 1 < value.length()
                    && value.charAt(i + 1) == mark) {
                i++;
            } else if (c == mark) {
                return i;
            }
        }
        return -1;
    }

    private static @Nullable String ruleFor(String text, @Nullable String typedRule) {
        if (typedRule != null) {
            return typedRule;
        }
        if (YamlScalars.isAddress(text)) {
            return "address";
        }
        return YamlScalars.hasLetter(text) ? null : "no letter";
    }

    private static Scalar withRejection(Scalar scalar, String rule) {
        return new Scalar(scalar.key(), scalar.start(), scalar.end(), scalar.quote(), scalar.source(), rule);
    }

    private static Scalar rejected(String key, String rule) {
        return new Scalar(key, -1, -1, YamlQuote.PLAIN, "", rule);
    }
}
