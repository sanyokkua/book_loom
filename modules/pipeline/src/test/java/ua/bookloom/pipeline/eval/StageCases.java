package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.TermType;

/**
 * The hand-made cases of the stage suites ({@code eval/stages/*.json}): invented neutral sentences, no book text. Each
 * case states what the production class must end with, so the score is a comparison, not a judgement.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StageCases {

    /** One name the scan is held to: kept with its type and gender, or left out. */
    record Expect(
            String term,
            boolean keep,
            @Nullable TermType type,
            @Nullable Gender gender) {}

    /** A book's lines and the names the glossary scan must and must not end with. */
    record PreScanCase(String id, String source, String target, List<String> sentences, List<Expect> expect) {

        PreScanCase {
            sentences = List.copyOf(sentences);
            expect = List.copyOf(expect);
        }
    }

    /** A term the term choice must keep (a real term) or drop (an everyday word). */
    record TermVerdict(String term, boolean keep) {}

    /** A glossary entry the review must keep or remove. */
    record HeldTerm(String term, TermType type, boolean keep) {}

    /** The book's lines, the candidate terms for the choice and the held entries for the review. */
    record TermsCase(
            String id,
            String source,
            String target,
            List<String> sentences,
            List<TermVerdict> terms,
            List<HeldTerm> glossary) {

        TermsCase {
            sentences = List.copyOf(sentences);
            terms = List.copyOf(terms);
            glossary = List.copyOf(glossary);
        }
    }

    /**
     * A small FB2 book for the file-name and Book Brief suggestions, with the brief a reader would give it: the narrator
     * and its gender, the register, and the genre's class (lower-case words, any one of which a right genre holds).
     */
    record SetupCase(
            String id,
            String source,
            String target,
            String fileStem,
            String title,
            String author,
            List<String> paragraphs,
            NarratorPerson narrator,
            Gender narratorGender,
            Register register,
            List<String> genreClass) {

        SetupCase {
            paragraphs = List.copyOf(paragraphs);
            Objects.requireNonNull(narratorGender, "narratorGender");
            Objects.requireNonNull(register, "register");
            genreClass = List.copyOf(genreClass);
        }
    }

    /**
     * A source paragraph, the defective draft the book is given for it and the clean text a good model would give
     * (used by the offline tests' fake model only). The final text must not match {@code defect} and must match
     * {@code required}; either may be absent.
     */
    record RetryCase(
            String id,
            String text,
            String draft,
            String fixed,
            @Nullable String defect,
            @Nullable String required) {}

    /** A name the book's glossary holds, so the checks that read the glossary can see a lost name. */
    record Seed(String term, String target) {}

    /** A clean paragraph around the cases, with its good draft. */
    record Filler(String text, String draft) {}

    /** The book of one language pair: fillers interleaved with the defective cases. */
    record RetryBook(
            String id, String source, String target, List<Seed> glossary, List<Filler> fillers, List<RetryCase> cases) {

        RetryBook {
            glossary = List.copyOf(glossary);
            fillers = List.copyOf(fillers);
            cases = List.copyOf(cases);
        }
    }

    static List<PreScanCase> prescan() {
        return load("prescan", new TypeReference<>() {});
    }

    static List<TermsCase> terms() {
        return load("terms", new TypeReference<>() {});
    }

    static List<SetupCase> setup() {
        return load("setup", new TypeReference<>() {});
    }

    static List<RetryBook> retry() {
        return load("retry", new TypeReference<>() {});
    }

    private static <T> List<T> load(final String name, final TypeReference<List<T>> type) {
        final String resource = "/eval/stages/" + name + ".json";
        try (InputStream in = StageCases.class.getResourceAsStream(resource)) {
            Objects.requireNonNull(in, resource);
            return new ObjectMapper().readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
