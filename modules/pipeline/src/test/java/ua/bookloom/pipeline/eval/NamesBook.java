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

/**
 * An invented mini-book in one shape Names &amp; style failed on in an owner's run ({@code eval/names/*.json}; no real
 * book text), with the gold a reader would give it and the answers a scripted model gives when the offline test runs
 * the book through the production services. The same file is a book the {@code names} promptEval suite feeds a real
 * model.
 *
 * @param id the case id, which is the resource name
 * @param about the shape the book has and what it tests, in a sentence
 * @param title the book's title
 * @param author the author's first and last name
 * @param chapters the book's sections in order, front matter included
 * @param gold what a reader concludes: the characters with type, gender and cluster, the junk a scan must not leave,
 *     the real names it must keep and the recurring terms worth keeping
 * @param model what the scripted model answers where it differs from a model that follows the gold
 * @param knownFailure whether today's Scan, Review and Translate end wrong on this book; the offline test fails once
 *     they end right, so the flag is dropped with the task that fixed it
 * @param fixedBy the task that fixes it, when {@code knownFailure}
 */
record NamesBook(
        String id,
        String about,
        String title,
        String author,
        List<Fb2Fixture.Section> chapters,
        JudgementGold gold,
        Model model,
        boolean knownFailure,
        @Nullable String fixedBy) {

    /** The committed books, one failure shape each. */
    static final List<String> IDS = List.of(
            "misattributed-she",
            "short-and-full-form",
            "short-and-full-weak",
            "city-her",
            "stray-he",
            "bartender-mark",
            "brand-and-camera",
            "groups-nationalities-converter",
            "technical-headings",
            "mayday");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Rejects a missing part and copies the chapters. */
    NamesBook {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(gold, "gold");
        chapters = List.copyOf(chapters);
        model = model == null ? new Model(List.of(), Map.of(), Map.of()) : model;
    }

    /**
     * What the scripted model says, over what a model that follows the gold says.
     *
     * @param verdicts the review verdicts that replace the gold-following one for their term
     * @param targets the rendering the model suggests by term; a term not listed is left without one
     * @param terms the recurring-term keep answers by lower-case word; a word not listed follows the gold, else drops
     */
    record Model(List<Verdict> verdicts, Map<String, String> targets, Map<String, Boolean> terms) {

        /** Copies the parts. */
        Model {
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
            targets = targets == null ? Map.of() : Map.copyOf(targets);
            terms = terms == null ? Map.of() : Map.copyOf(terms);
        }
    }

    /**
     * One review verdict as the model's reply writes it.
     *
     * @param term the term as listed
     * @param verdict {@code name}, {@code term} or {@code not-a-name}
     * @param type {@code person}, {@code place}, {@code org}, {@code term}, {@code title} or {@code other}
     * @param gender {@code male}, {@code female}, {@code neuter} or {@code unknown}
     * @param windows the window numbers it cites
     */
    record Verdict(String term, String verdict, String type, String gender, List<Integer> windows) {

        /** Copies the windows. */
        Verdict {
            windows = windows == null ? List.of() : List.copyOf(windows);
        }
    }

    /** All committed books. */
    static List<NamesBook> all() {
        return IDS.stream().map(NamesBook::byId).toList();
    }

    /** One committed book. */
    static NamesBook byId(final String id) {
        final String resource = "/eval/names/" + id + ".json";
        try (InputStream in = NamesBook.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, resource);
            return MAPPER.readValue(in, NamesBook.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the book as FB2 into {@code dir}. */
    Path write(final Path dir) {
        return Fb2Fixture.write(dir, new Fb2Fixture.Meta(id, title, author, gold.sourceLanguage()), chapters);
    }

    /** Every paragraph of the book, front matter included. */
    List<String> paragraphs() {
        return chapters.stream()
                .flatMap(chapter -> chapter.paragraphs().stream())
                .toList();
    }
}
