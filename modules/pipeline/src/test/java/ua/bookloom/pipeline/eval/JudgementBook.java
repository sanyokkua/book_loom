package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;

/**
 * An invented book in a shape the model's judgement of a book failed on or could fail on ({@code eval/judgement/*.json};
 * no book text): a name whose gender shows late, speech that names another person, a vocative name, a narrator shown
 * only by how she is addressed, front matter and a converter's page before the story, narrators that alternate. Each
 * carries the gold a reader would give, what the code alone must conclude ({@link Expect}) and what a good model quotes
 * for the narrator's gender ({@link Quote}), so the same file is an offline test case and a book the stability suite
 * feeds a real model.
 *
 * @param id the case id, which is the resource name
 * @param about what shape the book has and what it tests, in a sentence
 * @param title the book's title
 * @param author the author's first and last name
 * @param chapters the book's sections in order, front matter included
 * @param gold what a reader concludes about the book
 * @param expect what the deterministic code must conclude
 * @param narratorQuotes the quotes a good model gives for the narrator's gender, with the name it reads from each;
 *     empty when the book shows none
 */
record JudgementBook(
        String id,
        String about,
        String title,
        String author,
        List<Fb2Fixture.Section> chapters,
        JudgementGold gold,
        Expect expect,
        List<Quote> narratorQuotes) {

    /** The committed cases, in the order the stability suite runs them with {@code BOOKLOOM_EVAL_BOOK=all}. */
    static final List<String> IDS = List.of(
            "gender-late-or-object",
            "straight-quoted-speech",
            "vocative-name",
            "female-narrator-address",
            "front-matter-converter",
            "alternating-narrators");

    /** The case the stability suite runs when {@code BOOKLOOM_EVAL_BOOK} is unset. */
    static final String DEFAULT_ID = "front-matter-converter";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rejects a missing part and copies the lists. */
    JudgementBook {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gold, "gold");
        Objects.requireNonNull(expect, "expect");
        chapters = List.copyOf(chapters);
        narratorQuotes = narratorQuotes == null ? List.of() : List.copyOf(narratorQuotes);
    }

    /**
     * What the code concludes from the book with no model.
     *
     * @param narrator the narrator detector's person: {@code FIRST}, {@code THIRD}, {@code MIXED}, or {@code NONE} when
     *     it gives no answer
     * @param pronouns the gender the book's pronouns decide for a name, {@link Gender#UNKNOWN} when they decide none
     * @param sampleStartsWith the text the brief's first sample must start with, or null when not checked
     * @param sampleNever text no sample the brief reads may hold
     */
    record Expect(
            String narrator,
            Map<String, Gender> pronouns,
            @Nullable String sampleStartsWith,
            List<String> sampleNever) {

        /** Copies the parts. */
        Expect {
            Objects.requireNonNull(narrator, "narrator");
            pronouns = pronouns == null ? Map.of() : Map.copyOf(pronouns);
            sampleNever = sampleNever == null ? List.of() : List.copyOf(sampleNever);
        }
    }

    /**
     * A line a good model quotes as the narrator's gender evidence.
     *
     * @param quote the line, as it stands in the book
     * @param name the narrator's name the line gives, empty for a word of address
     */
    record Quote(String quote, String name) {}

    /** All committed cases. */
    static List<JudgementBook> all() {
        return IDS.stream().map(JudgementBook::byId).toList();
    }

    /** One committed case. */
    static JudgementBook byId(final String id) {
        final String resource = "/eval/judgement/" + id + ".json";
        try (InputStream in = JudgementBook.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, resource);
            return MAPPER.readValue(in, JudgementBook.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the book as FB2 into {@code dir}. */
    Path write(final Path dir) {
        return Fb2Fixture.write(dir, new Fb2Fixture.Meta(id, title, author, gold.sourceLanguage()), chapters);
    }

    /** Every paragraph of the book, front matter included, as the pronoun reader is given them. */
    List<String> paragraphs() {
        return chapters.stream()
                .flatMap(chapter -> chapter.paragraphs().stream())
                .toList();
    }
}
