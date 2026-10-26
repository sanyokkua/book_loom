package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.IdSource;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.SqlitePersistenceModule;
import ua.bookloom.pipeline.UuidIdSource;
import ua.bookloom.pipeline.project.ProjectServiceImpl;

/**
 * A glossary file of 5,000 rows imports into the SQLite store in seconds, all of it or nothing. Each row was read and
 * written on its own, so such a file took about two and a half minutes in the window.
 */
// `slow`: a whole SQLite store and 5,000 rows.
@Slf4j
@Tag("slow")
class GlossaryLargeCsvImportTest {

    private static final int ROWS = 5000;
    private static final long WITHIN_MILLIS = 10_000;
    private static final String[] GENDERS = {"female", "unknown"};

    @TempDir
    private Path dir;

    @Test
    void importCsv_fiveThousandRows_storesThemAllWithinSeconds() throws Exception {
        final Injector injector = Guice.createInjector(
                new DocumentModule(),
                new SqlitePersistenceModule(dir.resolve("bookloom.db"), Clock.systemUTC()),
                binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new UuidIdSource());
                });
        final Path book = Files.writeString(dir.resolve("Harbour.txt"), "Mira saw the ships.\n", StandardCharsets.UTF_8);
        final Result<ImportedBook> imported = injector.getInstance(ProjectServiceImpl.class).create(book);
        final String project = Objects.requireNonNull(Objects.requireNonNull(imported.data(), "imported").projectId());
        final Path csv = Files.writeString(dir.resolve("names.csv"), csvOf(ROWS), StandardCharsets.UTF_8);
        final GlossaryServiceImpl glossary = injector.getInstance(GlossaryServiceImpl.class);

        final long started = System.nanoTime();
        final Result<GlossaryImportReport> report = glossary.importCsv(project, csv);
        final long millis = (System.nanoTime() - started) / 1_000_000;

        log.info("{} glossary rows imported in {} ms", ROWS, millis);
        assertThat(report.isOk()).as("%s", report.error()).isTrue();
        assertThat(Objects.requireNonNull(report.data()).imported()).isEqualTo(ROWS);
        assertThat(glossary.entries(project).data()).hasSize(ROWS);
        assertThat(millis).isLessThan(WITHIN_MILLIS);
    }

    private static String csvOf(final int rows) {
        return "term,target,type,gender,locked\n"
                + IntStream.range(0, rows)
                        .mapToObj(i -> "Name" + i + ",Імʼя" + i + ",character," + GENDERS[i % 2] + ",false")
                        .collect(Collectors.joining("\n", "", "\n"));
    }
}
