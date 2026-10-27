package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** An import is the whole file or none of it. */
class GlossaryCsvFilesTest {

    private static final GlossaryEntry MILTON =
            new GlossaryEntry("p1:milton", PROJECT, "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false);

    @TempDir
    private Path dir;

    // IF a storage failure part-way kept the rows stored before it, THEN the glossary would be half one file, half the
    // other, with no word of which rows took.
    @Test
    void importFrom_storageFailsOnTheSecondRow_putsTheGlossaryBackAsItWas() throws IOException {
        final GlossaryRepository repository = Guice.createInjector(
                        new InMemoryPersistenceModule(),
                        binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()))
                .getInstance(GlossaryRepository.class);
        repository.add(MILTON);
        final AppError storageFailure = AppError.of(ErrorCode.internal, "Not stored", "The store failed.");
        final GlossaryCsvFiles files = new GlossaryCsvFiles(
                repository,
                rows -> repository.update(MILTON.withTarget("Мільтон")).flatMap(stored -> Result.err(storageFailure)));
        final Path file = Files.writeString(
                dir.resolve("glossary.csv"),
                "term,target,type,gender,locked\nMilton,Мільтон,place,unknown,false\nHale,Гейл,character,male,true\n");

        final Result<?> imported = files.importFrom(PROJECT, file);

        assertThat(imported.error()).isSameAs(storageFailure);
        assertThat(repository.all(PROJECT).data()).containsExactly(MILTON);
    }

    // An empty or header-only file is no failure: it imports nothing, and the window says the file holds no rows.
    @ParameterizedTest
    @ValueSource(strings = {"", "term,target,type,gender,locked\n"})
    void importFrom_fileWithNoRows_importsNothingAndRefusesNothing(final String text) throws IOException {
        final GlossaryRepository repository = Guice.createInjector(
                        new InMemoryPersistenceModule(),
                        binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()))
                .getInstance(GlossaryRepository.class);
        final GlossaryCsvFiles files = new GlossaryCsvFiles(repository, rows -> Result.ok(List.of()));

        final Result<GlossaryImportReport> imported =
                files.importFrom(PROJECT, Files.writeString(dir.resolve("glossary.csv"), text));

        assertThat(imported.data()).isEqualTo(new GlossaryImportReport(0, List.of(), List.of(), 0));
    }
}
