package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * One name-matching rule for every reader of names (15i.N4): the review's windows ({@link NameWindows}), the term
 * evidence ({@link TermEvidence}) and the reading plan ({@link NamesPlan#candidates}, {@code NamesPlanTest}) all find
 * a name the way the book index does — a run of words separated by blanks only, a possessive ending the run, and an
 * ampersand only where the name writes one — so the count a prompt shows and the windows it reads are the same
 * mentions.
 */
class NameMatchingRuleTest {

    private static final List<String> BOOK = List.of(
            "Arlo Venn sat down.",
            "It was Arlo’s Venn that won the race.",
            "Arlo, Venn and Pell left early.",
            "Arlo Venn rose.");

    private static final List<String> SHOP =
            List.of("Orrin & Vale sold cameras.", "Orrin and Vale argued all day.", "Orrin, Vale and Pell waited.");

    private static GlossaryEntry character(final String term) {
        return new GlossaryEntry("p1:" + term, "p1", term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static NameWindows.Evidence windowsOf(final String term, final List<String> paragraphs) {
        final BookSlice book = GlossaryTestSegments.of(paragraphs);
        return NameWindows.of(book.index(), book.ordinals(), List.of(character(term)), "en")
                .getOrDefault(term, NameWindows.Evidence.NONE);
    }

    private static TermEvidence.Evidence evidenceOf(final String term, final List<String> paragraphs) {
        final BookSlice book = GlossaryTestSegments.of(paragraphs);
        return TermEvidence.of(book.index(), book.ordinals(), List.of(term))
                .getOrDefault(term, TermEvidence.Evidence.NONE);
    }

    // IF "Arlo, Venn" or "Arlo’s Venn" counted as "Arlo Venn", THEN the review would show a window of two people as a
    // use of one name.
    @Test
    void nameWindows_nameSplitByACommaOrAPossessive_isNoUse() {
        final NameWindows.Evidence evidence = windowsOf("Arlo Venn", BOOK);

        assertThat(evidence.count()).isEqualTo(2);
        assertThat(evidence.windows())
                .extracting(NameWindows.Window::text)
                .containsExactly("Arlo Venn sat down.", "Arlo Venn rose.");
    }

    @Test
    void termEvidence_nameSplitByACommaOrAPossessive_isNoUse() {
        final TermEvidence.Evidence evidence = evidenceOf("Arlo Venn", BOOK);

        assertThat(evidence.count()).isEqualTo(2);
        assertThat(evidence.examples()).containsExactly("Arlo Venn sat down.", "Arlo Venn rose.");
    }

    // A pair that is one name is written with its ampersand; "Orrin, Vale" is two names.
    @Test
    void nameWindows_ampersandName_isFoundOnlyWhereTheBookWritesTheAmpersand() {
        assertThat(windowsOf("Orrin & Vale", SHOP).count()).isEqualTo(1);
    }

    @Test
    void termEvidence_ampersandName_isFoundOnlyWhereTheBookWritesTheAmpersand() {
        final TermEvidence.Evidence evidence = evidenceOf("Orrin & Vale", SHOP);

        assertThat(evidence.count()).isEqualTo(1);
        assertThat(evidence.examples()).containsExactly("Orrin & Vale sold cameras.");
    }

    // The review and the term evidence still count every use in any case: a word the book mostly writes in lower case
    // is the clearest sign it is not a name. Only the reading plan counts case-sensitively.
    @Test
    void termEvidenceAndNameWindows_lowerCaseUse_stillCount() {
        final List<String> bar = List.of("Mark poured the beer.", "She left a mark on the glass.");

        assertThat(evidenceOf("Mark", bar).count()).isEqualTo(2);
        assertThat(windowsOf("Mark", bar).count()).isEqualTo(2);
    }
}
