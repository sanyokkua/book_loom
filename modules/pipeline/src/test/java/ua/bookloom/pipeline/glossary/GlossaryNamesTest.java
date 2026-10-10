package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** An entry's folded spellings name it wherever the glossary is matched against the book. */
class GlossaryNamesTest {

    private static final GlossaryEntry IKON = new GlossaryEntry(
                    "p1:ikon", "p1", "Ikon", "Ікона", TermType.TERM, Gender.UNKNOWN, false)
            .withAliases(List.of("Ikonn"));

    // IF a misspelt alias were no line, THEN the name checks could not hold "Ikonn" to the entry's target.
    @Test
    void pairs_entryWithAnAlias_listsTheAliasWithTheSameTarget() {
        assertThat(GlossaryNames.pairs(List.of(IKON))).containsExactly("Ikon → Ікона", "Ikonn → Ікона");
    }

    @Test
    void pairs_entryWithNoTarget_listsNothing() {
        final GlossaryEntry open =
                new GlossaryEntry("p1:wren", "p1", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false);

        assertThat(GlossaryNames.pairs(List.of(open))).isEmpty();
    }

    @Test
    void isNamedIn_textWithOnlyTheAlias_namesTheEntry() {
        assertThat(GlossaryNames.isNamedIn(IKON, "The Ikonn hummed.")).isTrue();
        assertThat(GlossaryNames.isNamedIn(IKON, "The engine hummed.")).isFalse();
    }

    @Test
    void terms_entries_listEveryNameTermFirst() {
        assertThat(GlossaryNames.terms(List.of(IKON))).containsExactly("Ikon", "Ikonn");
    }
}
