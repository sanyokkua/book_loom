package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.GlossaryServiceFixtures.PROJECT;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
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
        final AtomicInteger calls = new AtomicInteger();
        final GlossaryCsvFiles files = new GlossaryCsvFiles(
                repository,
                row -> calls.incrementAndGet() == 1
                        ? repository.update(MILTON.withTarget("Мільтон")).map(stored -> Boolean.TRUE)
                        : Result.err(storageFailure));
        final Path file = Files.writeString(
                dir.resolve("glossary.csv"),
                "term,target,type,gender,locked\nMilton,Мільтон,place,unknown,false\nHale,Гейл,character,male,true\n");

        final Result<?> imported = files.importFrom(PROJECT, file);

        assertThat(imported.error()).isSameAs(storageFailure);
        assertThat(repository.all(PROJECT).data()).containsExactly(MILTON);
    }
}
