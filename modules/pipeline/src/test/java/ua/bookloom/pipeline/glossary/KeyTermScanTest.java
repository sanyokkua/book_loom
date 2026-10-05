package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.persistence.PersistenceModule;

/** The recurring-term scan: titles and dual-use words are proposed, names, function words and rare words are not. */
class KeyTermScanTest {

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }

    @SafeVarargs
    private static List<String> join(final List<String>... parts) {
        final List<String> all = new ArrayList<>();
        for (final List<String> part : parts) {
            all.addAll(part);
        }
        return all;
    }

    static Stream<Arguments> books() {
        return Stream.of(
                Arguments.of("a title seen three times", copies("Then Mr. Hale nodded.", 3), List.of("mr")),
                Arguments.of("a title seen twice", copies("Then Mr. Hale nodded.", 2), List.of()),
                Arguments.of(
                        "a word capitalised mid-sentence and also in lower case",
                        join(copies("We saw the Imp again.", 3), copies("the imp laughed.", 3)),
                        List.of("imp")),
                Arguments.of(
                        "a word the book capitalises only as a name", copies("We saw Hale at the door.", 8), List.of()),
                Arguments.of(
                        "a lower-case word however often it recurs", copies("She drew the pentacle.", 20), List.of()),
                Arguments.of(
                        "a function word written both ways",
                        join(copies("We saw It there.", 4), copies("it was late.", 4)),
                        List.of()),
                Arguments.of(
                        "the more frequent term comes first",
                        join(
                                copies("Sir Hale, a Lord, nodded.", 3),
                                copies("the lord sat.", 4),
                                copies("Sir wept.", 3)),
                        List.of("lord", "sir")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("books")
    void candidates_bookText_proposesExactlyTheTerms(
            final String description, final List<String> lines, final List<String> expected) {
        final List<String> terms = KeyTermScan.candidates(GlossaryTestSegments.of(lines), "en").stream()
                .map(NameCandidate::term)
                .toList();

        assertThat(terms).containsExactlyElementsOf(expected);
    }

    @Test
    void candidates_aLanguageWithoutATitleList_proposesOnlyDualUseWords() {
        final List<String> lines = copies("Mr. Hale nodded.", 5);

        assertThat(KeyTermScan.candidates(GlossaryTestSegments.of(lines), "xx")).isEmpty();
    }

    @Test
    void newTerms_termsTheGlossaryOrTheLexiconHold_areLeftOut() {
        final Injector injector = Guice.createInjector(new PersistenceModule());
        final GlossaryRepository glossary = injector.getInstance(GlossaryRepository.class);
        final LexiconRepository lexicon = injector.getInstance(LexiconRepository.class);
        glossary.add(new GlossaryEntry("p1:imp", "p1", "Imp", "біс", TermType.TERM, Gender.UNKNOWN, false));
        lexicon.put(LexiconEntry.of("p1", "MR"));
        final List<String> lines = join(
                copies("Then Mr. Hale nodded.", 3), copies("We saw the Imp again.", 3), copies("the imp laughed.", 3));
        final var segments = GlossaryTestSegments.of(join(lines, copies("Sir Hale left.", 3)));

        final var proposed = Objects.requireNonNull(
                KeyTermScan.newTerms("p1", segments, "en", glossary, lexicon).data());

        assertThat(proposed).extracting(LexiconEntry::term).containsExactly("sir");
    }
}
