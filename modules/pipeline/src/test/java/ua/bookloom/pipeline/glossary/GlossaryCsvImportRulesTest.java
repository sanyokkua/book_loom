package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.GlossaryService;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** A glossary file from a spreadsheet or another person: columns reordered, cells padded, a term named twice. */
class GlossaryCsvImportRulesTest {

    private static final String HEADER = "term,target,type,gender,locked";

    @TempDir
    Path dir;

    private GlossaryService service;

    @BeforeEach
    void setUp() {
        service = Guice.createInjector(
                        new DocumentModule(),
                        new InMemoryPersistenceModule(),
                        binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()))
                .getInstance(GlossaryServiceImpl.class);
    }

    // IF a header in another order were read by position, THEN every target would land in the wrong field.
    @Test
    void importCsv_headerInAnotherOrder_readsEachColumnByItsName() throws IOException {
        final Path file = write("locked,gender,type,target,term", "true,male,character,Гейл,Hale");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of()));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::locked)
                .containsExactly(tuple("Hale", "Гейл", true));
    }

    // IF a header naming an unknown or a doubled column were read anyway, THEN fields would be guessed.
    @ParameterizedTest
    @ValueSource(
            strings = {"term,target,kind,gender,locked", "term,target,type,gender", "term,target,type,type,locked"})
    void importCsv_headerThatDoesNotNameTheFiveColumns_isRefusedAndStoresNothing(final String header)
            throws IOException {
        final Path file = write(header, "Hale,Гейл,character,male,true");

        final Result<GlossaryImportReport> failed = service.importCsv(PROJECT, file);

        assertThat(failed.error()).extracting(AppError::details).isEqualTo("reason=csv-header");
        assertThat(service.entries(PROJECT).data()).isEmpty();
    }

    // IF spaces around a value were kept, THEN "Hale " and "Hale" would be two names.
    @Test
    void importCsv_spacesAroundTermAndTarget_areTrimmed() throws IOException {
        final Path file = write(HEADER, "  Hale  ,  Гейл ,character,male,true");

        service.importCsv(PROJECT, file);

        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::target)
                .containsExactly(tuple("Hale", "Гейл"));
    }

    // IF a term named twice were imported twice, THEN the report would count a row that changed nothing.
    @Test
    void importCsv_termOnTwoRows_keepsTheLastAndCountsOneDuplicate() throws IOException {
        final Path file = write(HEADER, "Hale,Хейл,character,male,false", "hale,Гейл,character,male,true");

        final Result<GlossaryImportReport> report = service.importCsv(PROJECT, file);

        assertThat(report.data()).isEqualTo(new GlossaryImportReport(1, List.of(), List.of(), 1));
        assertThat(service.entries(PROJECT).data())
                .extracting(GlossaryEntry::target)
                .containsExactly("Гейл");
    }

    private Path write(final String... lines) throws IOException {
        return Files.writeString(dir.resolve("glossary.csv"), String.join("\n", lines) + "\n");
    }
}
