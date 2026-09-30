package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * What a book's language declaration gives the person to think about before continuing. It never blocks: the source
 * language is chosen on the Book Brief either way.
 */
public sealed interface LanguageWarning {

    /**
     * The declared language differs from the language the book's text appears to be in.
     *
     * @param declared the normalized tag the book declares
     * @param content the tag of the language the content majority appears to be in
     */
    record Mismatch(String declared, String content) implements LanguageWarning {

        /** Rejects a missing tag. */
        public Mismatch {
            Objects.requireNonNull(declared, "declared");
            Objects.requireNonNull(content, "content");
        }
    }

    /**
     * The book declares a language the application cannot name.
     *
     * @param rawCode the declaration exactly as the book wrote it
     */
    record Unrecognized(String rawCode) implements LanguageWarning {

        /** Rejects a missing code. */
        public Unrecognized {
            Objects.requireNonNull(rawCode, "rawCode");
        }
    }
}
