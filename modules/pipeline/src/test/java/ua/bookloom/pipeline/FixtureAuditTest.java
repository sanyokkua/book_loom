package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.JobState;

/**
 * The audit holds no grudge against clean work: each fixture format goes through the real job with a model that never
 * fails, and the export's audit lists nothing, whatever the format's markup does to the stored targets.
 */
// `slow`: a whole run end to end for four formats.
@Tag("slow")
class FixtureAuditTest {

    private static final long SEED = 3L;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @CsvSource({"txt,earth-gravity.txt", "md,earth-gravity.md", "fb2,earth-gravity.fb2", "epub,earth-gravity.epub"})
    void export_cleanFixtureRun_auditListsNothing(final String format, final String fixture) {
        final Path book = SoakAssertions.copyFixture(fixture, tempDir);

        final SoakRun.Outcome outcome =
                new SoakRun().run(book, new FaultPlan(SEED, 0, Duration.ZERO, 0), tempDir.resolve("out." + format));

        assertThat(outcome.report().end()).isEqualTo(JobState.COMPLETED);
        assertThat(outcome.export().suspicious()).isEmpty();
    }
}
