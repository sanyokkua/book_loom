package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** The scan holds the inflected forms of one invented name as one lemma entry, by the source's name-ending table. */
class FrequencyScanLemmaTest {

    private static final String PROJECT = "p1";

    @Test
    void scan_inflectedForms_makeOneEntry() {
        final List<GlossaryEntry> entries = newTerms(
                "ru",
                List.of(
                        "Утром пришёл Велемир.",
                        "Мы ждали Велемира весь день.",
                        "Потом позвали Велемира снова.",
                        "Мы дали Велемиру хлеба.",
                        "Вечером ушёл Велемир."));

        assertThat(entries)
                .extracting(GlossaryEntry::term, GlossaryEntry::forms, GlossaryEntry::lemmaInferred)
                .containsExactly(tuple("Велемир", List.of("Велемира", "Велемиру"), false));
    }

    @Test
    void scan_inflectedForms_countTheWholeGroup() {
        final BookSlice book = GlossaryTestSegments.of(List.of(
                "Утром пришёл Велемир.",
                "Мы ждали Велемир и Велемиром весь день.",
                "Потом позвали Велемир с Велемиром снова."));

        assertThat(FrequencyScan.candidates(book.index(), book.ordinals(), 3, "ru"))
                .extracting(NameCandidate::term, NameCandidate::count)
                .containsExactly(tuple("Велемир", 5));
    }

    @Test
    void scan_twoObliqueFormsThatAgree_inferTheNominative() {
        final List<GlossaryEntry> entries = newTerms(
                "ru",
                List.of(
                        "Мы дали Градимиру хлеба.",
                        "Мы шли за Градимиром весь день.",
                        "Потом дали Градимиру воды.",
                        "Мы говорили с Градимиром долго."));

        assertThat(entries)
                .extracting(GlossaryEntry::term, GlossaryEntry::forms, GlossaryEntry::lemmaInferred)
                .containsExactly(tuple("Градимир", List.of("Градимиру", "Градимиром"), true));
    }

    @Test
    void scan_oneObliqueFormAlone_staysAsWritten() {
        final List<GlossaryEntry> entries = newTerms(
                "ru",
                List.of(
                        "Мы шли за Градимиром весь день.",
                        "Мы говорили с Градимиром долго.",
                        "Потом стояли над Градимиром молча."));

        assertThat(entries)
                .extracting(GlossaryEntry::term, GlossaryEntry::lemmaInferred)
                .containsExactly(tuple("Градимиром", false));
    }

    @Test
    void scan_languageWithNoNameTable_keepsWrittenForms() {
        final List<GlossaryEntry> entries = newTerms(
                "en",
                List.of(
                        "We met Wren at noon.",
                        "They saw Wren again.",
                        "He told Wren the news.",
                        "We met Wrens at noon.",
                        "They saw Wrens again.",
                        "He told Wrens the news."));

        assertThat(entries).extracting(GlossaryEntry::term).containsExactly("Wren", "Wrens");
    }

    private static List<GlossaryEntry> newTerms(final String language, final List<String> paragraphs) {
        final BookSlice book = GlossaryTestSegments.of(paragraphs);
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        return Objects.requireNonNull(
                FrequencyScan.newTerms(PROJECT, book.index(), book.ordinals(), language, glossary)
                        .data(),
                "entries");
    }
}
