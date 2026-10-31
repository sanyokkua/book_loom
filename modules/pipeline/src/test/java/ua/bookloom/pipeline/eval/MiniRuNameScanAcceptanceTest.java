package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.BookSlice;
import ua.bookloom.pipeline.glossary.FrequencyScan;

/** The invented Russian mini book through the deterministic name scan and the code-only gender evidence. */
class MiniRuNameScanAcceptanceTest {

    private static final String LANGUAGE = "ru";
    private static final String NAME = "Велемир";
    private static final String GENITIVE = "Велемира";

    // IF the book uses the genitive of an invented name, THEN that form is not proposed as an entry of its own.
    @Test
    void scan_inflectedFormOfInventedName_isNotItsOwnEntry() {
        assertThat(scanned().stream().map(GlossaryEntry::term).toList())
                .contains(NAME)
                .doesNotContain(GENITIVE);
    }

    // IF the name's genitive is declined as a man's, THEN the one base entry keeps gender male from code evidence
    // alone.
    @Test
    void scan_baseNameWithGenitiveOfItsOwnGender_keepsGenderMale() {
        assertThat(scanned())
                .filteredOn(entry -> NAME.equals(entry.term()))
                .singleElement()
                .extracting(GlossaryEntry::gender)
                .isEqualTo(Gender.MALE);
    }

    private static List<GlossaryEntry> scanned() {
        final List<String> texts = MiniBook.load(MiniBook.MINI_RU).blocks().stream()
                .filter(block -> block.kind() == MiniBook.Kind.P)
                .map(MiniBook.Block::text)
                .toList();
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        final BookSlice slice = StageFrames.slice(texts);
        return FrequencyScan.proposals("p1", slice.index(), slice.ordinals(), LANGUAGE, glossary)
                .data()
                .seededFor(slice, LANGUAGE, null);
    }
}
