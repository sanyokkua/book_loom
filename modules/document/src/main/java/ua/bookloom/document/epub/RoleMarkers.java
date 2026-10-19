package ua.bookloom.document.epub;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The words that place an EPUB document in the front or back of its book: the {@code epub:type} vocabulary and the
 * EPUB 2 guide types, and the labels books give such documents in their contents, file names or first line.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RoleMarkers {

    /** What a marker says about the document. */
    enum Place {
        /** Stated front matter. */
        FRONT,
        /** Stated back matter. */
        BACK,
        /** Stated story text. */
        BODY,
        /** Matter that is neither story nor stated front or back: its place is where the story is. */
        MATTER,
        /** No opinion. */
        NONE
    }

    private static final Set<String> FRONT_TYPES = Set.of(
            "cover",
            "frontmatter",
            "titlepage",
            "title-page",
            "halftitlepage",
            "half-title-page",
            "copyright-page",
            "dedication",
            "toc",
            "contents",
            "imprint",
            "frontispiece");

    private static final Set<String> BACK_TYPES =
            Set.of("backmatter", "colophon", "acknowledgments", "acknowledgements", "index", "bibliography");

    private static final Set<String> BODY_TYPES = Set.of(
            "bodymatter",
            "text",
            "chapter",
            "part",
            "prologue",
            "epilogue",
            "foreword",
            "preface",
            "introduction",
            "afterword",
            "volume",
            "division");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Labels of matter that is not the story, whatever the book calls its file. */
    private static final Pattern MATTER_LABEL = Pattern.compile("^(cover( page)?|title ?page|half ?title( page)?"
            + "|copyright( page| notice)?|dedication|contents|table of contents|toc|imprint|colophon|credits"
            + "|permissions|index|newsletter.*|praise\\b.*|also by\\b.*|other books\\b.*|books by\\b.*"
            + "|by the same author|about the authors?\\b.*|authors? bio\\b.*|acknowledg\\w*( .*)?"
            + "|preview\\b.*|excerpt\\b.*|sneak peek\\b.*|coming soon\\b.*)$");

    /**
     * Reads an {@code epub:type} or guide {@code type} value, which may hold several space-separated tokens.
     *
     * @param value the attribute value; blank reads as no opinion
     * @return the place the strongest token names; front and back outrank body, since a document may be both
     *     {@code bodymatter} and a {@code chapter} but a stated front type is never story
     */
    static Place ofTypes(final String value) {
        Place found = Place.NONE;
        for (final String token : (Iterable<String>)
                WHITESPACE.splitAsStream(value.toLowerCase(Locale.ROOT).strip())::iterator) {
            final Place place = ofToken(token);
            if (place == Place.FRONT || place == Place.BACK) {
                return place;
            }
            if (place == Place.BODY) {
                found = Place.BODY;
            }
        }
        return found;
    }

    private static Place ofToken(final String token) {
        final String bare = token.startsWith("other.") ? token.substring("other.".length()) : token;
        if (FRONT_TYPES.contains(bare)) {
            return Place.FRONT;
        }
        if (BACK_TYPES.contains(bare)) {
            return Place.BACK;
        }
        return BODY_TYPES.contains(bare) ? Place.BODY : Place.NONE;
    }

    /**
     * Reads a label: a contents entry, a file's name without its extension, or a short first line.
     *
     * @param label the text; may be blank
     * @return {@link Place#MATTER} when it names matter outside the story, {@link Place#NONE} otherwise
     */
    static Place ofLabel(final String label) {
        final String words =
                NON_WORD.matcher(label.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
        return MATTER_LABEL.matcher(words).matches() ? Place.MATTER : Place.NONE;
    }

    /**
     * Counts a text's words.
     *
     * @param text the text; may be blank
     * @return how many whitespace-separated words it holds, zero when blank
     */
    static int wordCount(final String text) {
        final String stripped = text.strip();
        return stripped.isEmpty() ? 0 : (int) WHITESPACE.splitAsStream(stripped).count();
    }
}
