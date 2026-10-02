package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The night's run, replayed in about a minute: a generated book of 3,700 paragraphs goes through the real job over a
 * fault-injecting pseudo model on a scripted clock, with a 25-minute outage in the middle, and must finish with every
 * segment decided, every fault answered, and its memory, threads and time bounded. The fixture formats then go
 * through the same harness and the repository's validator. Local only: {@code ./gradlew :pipeline:soak}.
 */
@Tag("soak")
class WholeBookSoakTest {

    private static final int PARAGRAPHS = 3_700;
    private static final long SEED = 7L;
    private static final int OUTAGE_AT_CALL = 2_000;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @EnumSource(SoakBooks.Shape.class)
    void run_syntheticBookUnderFaults_finishesDecidedAndBounded(final SoakBooks.Shape shape) {
        final Path book = SoakBooks.write(tempDir, shape, PARAGRAPHS, SEED);
        final Path exported = tempDir.resolve("Soak.uk." + shape.extension());

        final SoakRun.Outcome outcome = new SoakRun().run(book, FaultPlan.standard(SEED, OUTAGE_AT_CALL), exported);

        SoakReport.print(shape.name(), outcome);
        SoakAssertions.assertFinishedAndDecided(outcome);
        SoakAssertions.assertEveryFaultAnswered(outcome);
        SoakAssertions.assertBounded(outcome);
        SoakAssertions.assertRetainedPerSegment(outcome);
        SoakAssertions.assertNoLeftoverPlaceholders(exported);
    }

    // Faults three times as often on a book a twentieth the size, so each kind still strikes. FB2 is not validated:
    // its <code> is prose there (design D11 of the document round trip) and is translated, which the validator's
    // code and verbatim checks count as a change; FixtureBookSoakTest still runs and re-opens it.
    @ParameterizedTest
    @CsvSource({"txt,earth-gravity.txt", "md,earth-gravity.md", "epub,earth-gravity.epub"})
    void run_fixtureBookUnderFaults_passesTheStructureValidator(final String format, final String fixture) {
        final Path book = SoakAssertions.copyFixture(fixture, tempDir);
        final Path exported = tempDir.resolve("translated." + format);

        final SoakRun.Outcome outcome = new SoakRun().run(book, FixtureSoak.plan(), exported);

        SoakReport.print(fixture, outcome);
        SoakAssertions.assertFinishedAndDecided(outcome);
        final List<String> validated = SoakAssertions.validate(format, fixture, exported);
        assertThat(validated.getFirst()).as(validated.getLast()).isEqualTo("0");
    }
}
