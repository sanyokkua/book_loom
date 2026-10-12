package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;

/** The eval's access to the production names services over an imported project's own stores (15i.E1). */
class NamesWiringTest {

    @TempDir
    private Path dir;

    private Prepared prepared() {
        return SequenceJobs.importBook(
                NamesBook.byId("stray-he").write(dir),
                SequenceJobs.brief("en", "uk", QualityDial.FAST, Narrator.unspecified()));
    }

    // IF the services read other stores than the project's, THEN the scan would find nothing in the imported book.
    @Test
    void names_preparedBook_glossaryScanFindsTheBooksNamesInTheProjectsOwnStore() {
        final Prepared prepared = prepared();
        final SequenceJobs.Names names = SequenceJobs.names(prepared);

        assertThat(names.glossary().scan(prepared.projectId()).isOk()).isTrue();

        assertThat(names.glossary().entries(prepared.projectId()).data())
                .extracting(GlossaryEntry::term)
                .contains("Perpetua", "Anselm");
        assertThat(prepared.stores().glossary().all(prepared.projectId()).data())
                .extracting(GlossaryEntry::term)
                .contains("Perpetua", "Anselm");
    }

    @Test
    void names_preparedBook_lexiconServiceReadsTheProjectsOwnLexicon() {
        final Prepared prepared = prepared();
        final SequenceJobs.Names names = SequenceJobs.names(prepared);

        assertThat(names.lexicon().scan(prepared.projectId()).isOk()).isTrue();

        assertThat(names.lexicon().entries(prepared.projectId()).data())
                .isEqualTo(prepared.stores().lexicon().all(prepared.projectId()).data());
    }
}
