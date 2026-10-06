package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;

/**
 * The synthetic book of the sequence eval and what is known about it: its chapters and who narrates each, the glossary
 * the run starts with, the recurring terms with the renderings a model is known to drift between, and the names with the
 * spellings that tempt it. The text is written for the eval; it quotes no book.
 *
 * @param source the source language tag
 * @param target the target language tag
 * @param chapters the chapters in order, named by their heading
 * @param glossary the entries the run's glossary starts with
 * @param terms the recurring terms measured for one rendering each
 * @param names the names measured for one spelling each
 */
record SequenceFixture(
        String source,
        String target,
        List<Chapter> chapters,
        List<GlossarySeed> glossary,
        List<Term> terms,
        List<Name> names) {

    static final String BOOK = "/eval/sequence/book.md";
    private static final String MANIFEST = "/eval/sequence/manifest.json";

    /** Copies the lists. */
    SequenceFixture {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        chapters = List.copyOf(chapters);
        glossary = List.copyOf(glossary);
        terms = List.copyOf(terms);
        names = List.copyOf(names);
    }

    /**
     * A chapter.
     *
     * @param title the heading's text
     * @param narrator who narrates it
     */
    record Chapter(String title, NarratorPerson narrator) {}

    /**
     * A glossary entry the run starts with; a null target is an entry the run has to fill.
     *
     * @param term the source name
     * @param target its rendering, or null
     * @param type the kind of term
     * @param gender the gender, for agreement
     * @param locked whether the target is fixed
     */
    record GlossarySeed(String term, @Nullable String target, TermType type, Gender gender, boolean locked) {}

    /**
     * One way a term is rendered.
     *
     * @param label a short name for the table
     * @param pattern a regular expression for the lower-cased word, in any inflection
     */
    record Rendering(String label, String pattern) {}

    /**
     * A recurring term.
     *
     * @param term the name in the table
     * @param pattern a regular expression for the term in a source segment
     * @param renderings the renderings a target may carry
     */
    record Term(String term, String pattern, List<Rendering> renderings) {}

    /**
     * A name and the spellings a target may give it.
     *
     * @param name the source name
     * @param spellings lower-cased prefixes, one per spelling
     */
    record Name(String name, List<String> spellings) {}

    static SequenceFixture load() {
        try (InputStream in = SequenceFixture.class.getResourceAsStream(MANIFEST)) {
            return new ObjectMapper().readValue(Objects.requireNonNull(in, MANIFEST), SequenceFixture.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String bookText() {
        try (InputStream in = SequenceFixture.class.getResourceAsStream(BOOK)) {
            return new String(Objects.requireNonNull(in, BOOK).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
