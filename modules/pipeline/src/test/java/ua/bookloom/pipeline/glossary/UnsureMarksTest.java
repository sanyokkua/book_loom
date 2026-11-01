package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * The Unsure mark (glossary "A mention with no evidence is unsure"): a mention of {@code Велемира} that the clause cannot
 * give to the man {@code Велемир} (genitive) or the woman {@code Велемира} counts for both, and each entry is marked.
 * Invented names only.
 */
class UnsureMarksTest {

    private static GlossaryEntry entry(final String term, final String... forms) {
        return GlossaryEntry.of("p1:" + term, "p1", term, null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withForms(List.of(forms));
    }

    @Test
    void marked_mentionWithNoEvidence_countsForBothEntries() {
        final List<String> texts = List.of("Утром Велемир ушёл.", "Это был портрет Велемира.");

        final List<GlossaryEntry> marked =
                UnsureMarks.marked(List.of(entry("Велемир", "Велемира"), entry("Велемира")), texts, "ru");

        assertThat(marked)
                .extracting(GlossaryEntry::term, GlossaryEntry::unsureMentions)
                .containsExactly(tuple("Велемир", 1), tuple("Велемира", 1));
    }

    @Test
    void marked_verbDecidesTheMention_marksNeither() {
        final List<String> texts = List.of("Утром Велемир ушёл.", "Велемира сказала тихо.");

        final List<GlossaryEntry> marked =
                UnsureMarks.marked(List.of(entry("Велемир", "Велемира"), entry("Велемира")), texts, "ru");

        assertThat(marked).extracting(GlossaryEntry::unsureMentions).containsExactly(0, 0);
    }

    @Test
    void marked_languageWithoutNameTable_marksNothing() {
        final List<String> texts = List.of("Hale left.", "The portrait of Hale.");

        assertThat(UnsureMarks.marked(List.of(entry("Hale")), texts, "en"))
                .extracting(GlossaryEntry::unsureMentions)
                .containsExactly(0);
    }
}
