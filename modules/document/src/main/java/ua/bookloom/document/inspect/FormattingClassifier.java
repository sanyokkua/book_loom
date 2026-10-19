package ua.bookloom.document.inspect;

import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookStats.Formatting;

/**
 * Classifies a masked segment's protected placeholder fragments into the {@link Formatting} kinds the statistics
 * report (task 4.5): {@code i}/{@code em}/{@code emphasis} italics, {@code b}/{@code strong} bold (the FB2 name for
 * both is the same as XHTML's), {@code a} links, {@code q} quotes, {@code code}, {@code br} line breaks, anything
 * else masked as a tag {@link Formatting#OTHER}. Markdown's punctuation delimiters carry no tag name, so they are
 * classified by their own leading punctuation instead.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FormattingClassifier {

    private static final Map<String, Formatting> TAG_KINDS = Map.of(
            "i", Formatting.ITALICS,
            "em", Formatting.ITALICS,
            "emphasis", Formatting.ITALICS,
            "b", Formatting.BOLD,
            "strong", Formatting.BOLD,
            "a", Formatting.LINKS,
            "q", Formatting.QUOTES,
            "code", Formatting.CODE,
            "br", Formatting.LINE_BREAKS);

    /**
     * Classifies a tree-format (EPUB/FB2) masked fragment — an opening tag, a closing tag, or a whole void/atomic
     * element's markup.
     *
     * @param fragment the exact source fragment a placeholder replaced; never null
     * @return the formatting kind it names, or {@code null} when {@code fragment} is not element markup at all (a
     *     comment, a processing instruction, a CDATA section, or plain protected text)
     */
    public static @Nullable Formatting classifyTag(String fragment) {
        if (!isElementMarkup(fragment)) {
            return null;
        }
        final String tag = tagNameOf(fragment);
        return tag == null ? null : TAG_KINDS.getOrDefault(tag, Formatting.OTHER);
    }

    private static boolean isElementMarkup(String fragment) {
        return fragment.startsWith("<") && !fragment.startsWith("<!") && !fragment.startsWith("<?");
    }

    private static @Nullable String tagNameOf(String fragment) {
        final int start = fragment.startsWith("</") ? 2 : 1;
        int end = start;
        while (end < fragment.length() && isTagNameChar(fragment.charAt(end))) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return fragment.substring(start, end).toLowerCase(Locale.ROOT);
    }

    private static boolean isTagNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == ':';
    }

    /**
     * Classifies a Markdown masked fragment by its leading punctuation — an image's full atomic span, a link's
     * bracket delimiter, a code span, emphasis/strong delimiters, or a hard line break.
     *
     * @param fragment the exact source range a placeholder replaced; never null
     * @return the formatting kind it names; never {@code null} — an unrecognised fragment is {@link Formatting#OTHER}
     */
    public static Formatting classifyMarkdown(String fragment) {
        if (fragment.startsWith("![")) {
            return Formatting.OTHER;
        }
        if (fragment.startsWith("[") || fragment.startsWith("](")) {
            return Formatting.LINKS;
        }
        if (fragment.startsWith("`")) {
            return Formatting.CODE;
        }
        if (fragment.startsWith("**") || fragment.startsWith("__")) {
            return Formatting.BOLD;
        }
        if (fragment.startsWith("*") || fragment.startsWith("_")) {
            return Formatting.ITALICS;
        }
        if (fragment.startsWith("\\") || fragment.isBlank()) {
            return Formatting.LINE_BREAKS;
        }
        return Formatting.OTHER;
    }
}
