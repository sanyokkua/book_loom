package ua.bookloom.pipeline;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The soak harness in the gate: each fixture format goes through the real job over the fault-injecting model on a
 * scripted clock, with dense faults and a 21-minute outage, and must finish with every segment decided and exported,
 * within the run's memory, thread and time bounds. Seconds per format; the long run is {@link WholeBookSoakTest}.
 */
// `slow`: a whole run end to end; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class FixtureBookSoakTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @CsvSource({"txt,earth-gravity.txt", "md,earth-gravity.md", "fb2,earth-gravity.fb2", "epub,earth-gravity.epub"})
    void run_fixtureBookUnderFaults_finishesDecidedAndBounded(final String format, final String fixture) {
        final Path book = SoakAssertions.copyFixture(fixture, tempDir);
        final Path exported = tempDir.resolve("translated." + format);

        final SoakRun.Outcome outcome = new SoakRun().run(book, FixtureSoak.plan(), exported);

        SoakReport.print(fixture, outcome);
        SoakAssertions.assertFinishedAndDecided(outcome);
        SoakAssertions.assertBounded(outcome);
    }
}
