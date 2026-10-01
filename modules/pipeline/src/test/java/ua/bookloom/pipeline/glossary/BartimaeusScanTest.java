package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The name scan on a small invented excerpt that reproduces what the scan got wrong on a real book (Jonathan Stroud's
 * "The Amulet of Samarkand"): interjections and contractions after a dash or a quote, common nouns written with a
 * capital, a hyphenated place cut in two, and the forename or surname of a longer name proposed on its own.
 */
class BartimaeusScanTest {

    private static final int THRESHOLD = 3;

    @Test
    void candidates_excerpt_proposesTheNames() {
        assertThat(terms()).contains("Nathaniel", "Underwood", "Simon Lovelace", "Lovelace", "Sholto Pinn", "Al-Arish");
    }

    @Test
    void candidates_excerpt_leavesOutCommonWordsAndPartsOfLongerNames() {
        assertThat(terms())
                .doesNotContain(
                        "Well", "Don", "Don’t", "Words", "Seal", "Shield", "Al", "Arish", "Simon", "Sholto", "Pinn");
    }

    @Test
    void candidates_sentenceInitialRunWhoseHeadIsSeenMidSentence_countsTheWholeRun() {
        final List<NameCandidate> found = FrequencyScan.candidates(GlossaryTestSegments.of(excerpt()), THRESHOLD, "en");

        assertThat(found)
                .filteredOn(candidate -> candidate.term().equals("Sholto Pinn"))
                .singleElement()
                .extracting(NameCandidate::count)
                .isEqualTo(7);
    }

    private static List<String> terms() {
        return FrequencyScan.candidates(GlossaryTestSegments.of(excerpt()), THRESHOLD, "en").stream()
                .map(NameCandidate::term)
                .toList();
    }

    static List<String> excerpt() {
        final List<String> lines = new ArrayList<>();
        lines.addAll(copies("The boy Nathaniel ran up the stairs.", 5));
        lines.addAll(copies("His master, Underwood, was waiting.", 4));
        lines.add("They called on Arthur Underwood at noon.");
        lines.addAll(copies("The magician Simon Lovelace smiled.", 6));
        lines.addAll(copies("He looked at Lovelace again.", 6));
        lines.addAll(copies("Even Simon was afraid.", 3));
        lines.addAll(copies("They visited Sholto Pinn at his shop.", 4));
        lines.addAll(copies("Sholto Pinn bowed low.", 3));
        lines.addAll(copies("He paused—Well, perhaps not.", 3));
        lines.addAll(copies("He knew the house well.", 3));
        lines.addAll(copies("She said, \"Don’t go.\"", 3));
        lines.addAll(copies("You don’t understand.", 4));
        lines.addAll(copies("He spoke the Words aloud.", 3));
        lines.addAll(copies("Those words meant nothing.", 2));
        lines.addAll(copies("He broke the Seal at once.", 3));
        lines.addAll(copies("The seal cracked.", 2));
        lines.addAll(copies("He raised a Shield around him.", 3));
        lines.add("His shield held.");
        lines.addAll(copies("They took the road to Al-Arish that day.", 3));
        return lines;
    }

    private static List<String> copies(final String line, final int times) {
        return Collections.nCopies(times, line);
    }
}
