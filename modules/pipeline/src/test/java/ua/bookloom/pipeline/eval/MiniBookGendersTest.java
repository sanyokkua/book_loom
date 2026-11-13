package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.BookSlice;
import ua.bookloom.pipeline.glossary.FrequencyScan;

/**
 * Both invented mini books are small, and since dataset version 2 the code alone gives each named person of their gold
 * maps the gold gender: from verbs and pronouns after the name, or from the name's declension by rule.
 */
class MiniBookGendersTest {

    private static List<GlossaryEntry> scanned(final String dataset, final String language) {
        final List<String> texts = MiniBook.load(dataset).blocks().stream()
                .filter(block -> block.kind() == MiniBook.Kind.P)
                .map(MiniBook.Block::text)
                .toList();
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        final BookSlice slice = StageFrames.slice(texts);
        return Objects.requireNonNull(FrequencyScan.proposals("p1", slice.index(), slice.ordinals(), language, glossary)
                        .data())
                .seededFor(slice, language, null);
    }

    // Each row: the dataset, its language, a scanned term, the gold gender and the origin the code gives it.
    @ParameterizedTest
    @CsvSource({
        "mini-ru, ru, Велемир, MALE, LIKELY",
        "mini-ru, ru, Мирена, FEMALE, LIKELY",
        "mini-ru, ru, Рудакова, FEMALE, LIKELY",
        "mini-ru, ru, Тарн, MALE, RULE",
        "mini-en, en, Dorrin, MALE, LIKELY",
        "mini-en, en, Ilsabet, FEMALE, LIKELY",
        "mini-en, en, Tobin Reed, MALE, LIKELY"
    })
    void scan_miniBook_givesTheGoldGenderFromTheBookAlone(
            final String dataset, final String language, final String term, final Gender gender, final Origin origin) {
        assertThat(scanned(dataset, language))
                .filteredOn(entry -> term.equals(entry.term()))
                .singleElement()
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(gender, origin);
    }
}
