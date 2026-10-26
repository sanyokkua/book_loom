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
import ua.bookloom.api.pipeline.CsvProblem;
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
        final Injector injector = injector();
        final String project = project(injector);
        final Path csv = Files.writeString(dir.resolve("names.csv"), csvOf(ROWS, "character"), StandardCharsets.UTF_8);
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

    // IF each of 5,000 rows typed "person" were reported on its own, THEN the person would get a wall of line numbers
    // and no reason (G1-4); the report names the one cause, still within seconds.
    @Test
    void importCsv_fiveThousandRowsOfAnUnknownType_namesTheCauseOnceWithinSeconds() throws Exception {
        final Injector injector = injector();
        final String project = project(injector);
        final Path csv = Files.writeString(dir.resolve("names.csv"), csvOf(ROWS, "person"), StandardCharsets.UTF_8);
        final GlossaryServiceImpl glossary = injector.getInstance(GlossaryServiceImpl.class);

        final long started = System.nanoTime();
        final GlossaryImportReport report =
                Objects.requireNonNull(glossary.importCsv(project, csv).data(), "report");
        final long millis = (System.nanoTime() - started) / 1_000_000;

        assertThat(report.imported()).isZero();
        assertThat(report.malformedLines()).hasSize(ROWS).startsWith(2).endsWith(ROWS + 1);
        assertThat(report.problems()).containsExactly(CsvProblem.UNKNOWN_TYPE);
        assertThat(millis).isLessThan(WITHIN_MILLIS);
    }

    private Injector injector() {
        return Guice.createInjector(
                new DocumentModule(),
                new SqlitePersistenceModule(dir.resolve("bookloom.db"), Clock.systemUTC()),
                binder -> {
                    binder.bind(Clock.class).toInstance(Clock.systemUTC());
                    binder.bind(IdSource.class).toInstance(new UuidIdSource());
                });
    }

    private String project(final Injector injector) throws java.io.IOException {
        final Path book =
                Files.writeString(dir.resolve("Harbour.txt"), "Mira saw the ships.\n", StandardCharsets.UTF_8);
        final Result<ImportedBook> imported =
                injector.getInstance(ProjectServiceImpl.class).create(book);
        return Objects.requireNonNull(
                Objects.requireNonNull(imported.data(), "imported").projectId());
    }

    private static String csvOf(final int rows, final String type) {
        return "term,target,type,gender,locked\n"
                + IntStream.range(0, rows)
                        .mapToObj(i -> "Name" + i + ",Імʼя" + i + "," + type + "," + GENDERS[i % 2] + ",false")
                        .collect(Collectors.joining("\n", "", "\n"));
    }
}
