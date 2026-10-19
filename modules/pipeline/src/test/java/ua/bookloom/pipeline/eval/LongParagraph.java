package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * One paragraph of more than 180 words from a public-domain novel (published before 1929), the set the long-paragraph
 * experiment (15i.X3) is measured on. The text is copied as Project Gutenberg prints it, with the italic underscores
 * and the hard line breaks taken out.
 *
 * @param id the paragraph id, {@code lp-NN}
 * @param title the novel
 * @param author the author
 * @param chapter the chapter heading the paragraph sits under
 * @param gutenberg the Project Gutenberg ebook number the text was taken from
 * @param words the paragraph's word count, counted at white space
 * @param text the English paragraph
 */
record LongParagraph(String id, String title, String author, String chapter, int gutenberg, int words, String text) {

    private static final String RESOURCE = "/eval/long-paragraphs.json";

    /** Rejects missing parts. */
    LongParagraph {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(text, "text");
    }

    /** Every paragraph of the set, in file order. */
    static List<LongParagraph> load() {
        try (InputStream in = LongParagraph.class.getResourceAsStream(RESOURCE)) {
            Objects.requireNonNull(in, RESOURCE);
            return new ObjectMapper().readValue(in, new TypeReference<>() {});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
