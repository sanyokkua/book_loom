package ua.bookloom.document.md;

import java.util.Objects;

/**
 * How a frontmatter scalar was quoted in the source, which is also how its translation is written back: a value
 * keeps its quotes, and only a plain value may need new ones.
 */
enum YamlQuote {

    /** No quotes; the translation is wrapped in double quotes only when it would stop reading as text. */
    PLAIN,

    /** Double quotes; {@code "} and {@code \} are backslash-escaped inside them. */
    DOUBLE,

    /** Single quotes; {@code '} is doubled inside them. */
    SINGLE;

    /**
     * The text a scalar's raw span holds once its quote escapes are read.
     *
     * @param raw the characters between the quotes, or the plain value
     * @return the text the translator sees
     */
    String decode(String raw) {
        Objects.requireNonNull(raw, "raw");
        return switch (this) {
            case PLAIN -> raw;
            case DOUBLE -> unescapeDouble(raw);
            case SINGLE -> raw.replace("''", "'");
        };
    }

    /**
     * The characters to write into a scalar's span so that a YAML reader reads {@code text} back as text.
     *
     * @param text the translated value as plain text
     * @return the span's new content, wrapped in double quotes when a plain value would not read as text
     */
    String encode(String text) {
        Objects.requireNonNull(text, "text");
        return switch (this) {
            case PLAIN -> YamlScalars.needsQuotes(text) ? '"' + escapeDouble(text) + '"' : text;
            case DOUBLE -> escapeDouble(text);
            case SINGLE -> text.replace("'", "''");
        };
    }

    private static String escapeDouble(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescapeDouble(String raw) {
        final StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            final boolean isEscape = c == '\\' && i + 1 < raw.length() && "\"\\".indexOf(raw.charAt(i + 1)) >= 0;
            out.append(isEscape ? raw.charAt(++i) : c);
        }
        return out.toString();
    }
}
