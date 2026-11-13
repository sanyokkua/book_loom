package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/**
 * A small book of six invented paragraphs ({@code small-book-ru.txt}) through the name scan and the code-only gender
 * evidence: a few paragraphs are enough for entries and genders where the book's verbs, pronouns or declension show
 * them, and a name the book gives no evidence for keeps its gender unknown.
 */
class SmallBookScanTest {

    private static final String LANGUAGE = "ru";

    private static List<GlossaryEntry> scanned() {
        final List<String> paragraphs = Arrays.stream(read().split("\\R\\R+"))
                .map(String::strip)
                .filter(paragraph -> !paragraph.isEmpty())
                .toList();
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        final BookSlice slice = GlossaryTestSegments.of(paragraphs);
        return Objects.requireNonNull(FrequencyScan.proposals("p1", slice.index(), slice.ordinals(), LANGUAGE, glossary)
                        .data())
                .seededFor(slice, LANGUAGE, null);
    }

    private static String read() {
        try (InputStream in = SmallBookScanTest.class.getResourceAsStream("small-book-ru.txt")) {
            return new String(Objects.requireNonNull(in, "small-book-ru.txt").readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Five feminine votes after Ведана are strong evidence (the book's); three masculine verbs after Остромир decide
    // a likely gender.
    @Test
    void scan_smallBook_givesEntriesAndGendersWhereEvidenceHolds() {
        assertThat(scanned())
                .filteredOn(entry -> List.of("Ведана", "Остромир").contains(entry.term()))
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactlyInAnyOrder(
                        tuple("Ведана", Gender.FEMALE, Origin.BOOK), tuple("Остромир", Gender.MALE, Origin.LIKELY));
    }

    // Горята is named four times but no verb or pronoun follows the name, and its -а row is one both genders decline
    // by, so nothing is guessed.
    @Test
    void scan_nameWithTooLittleEvidence_staysUnknown() {
        assertThat(scanned())
                .filteredOn(entry -> "Горята".equals(entry.term()))
                .singleElement()
                .extracting(GlossaryEntry::gender, GlossaryEntry::forms)
                .containsExactly(Gender.UNKNOWN, List.of("Горяту", "Горяты"));
    }
}
