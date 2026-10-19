package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.pipeline.FaultyModel.Fault;

/** What every soak run must show, whatever the book: it finished, decided everything, and stayed within its bounds. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SoakAssertions {

    /** The fixture's own repository path, relative to the module the tests run in. */
    static final Path FIXTURES = Path.of("..", "app", "src", "test", "resources", "fixtures", "earth-gravity");

    private static final int EXTRA_THREADS = 2;
    private static final long MAX_HEAP_MIB = 400;
    private static final long MAX_BYTES_PER_SEGMENT = 16 * 1024;
    private static final Duration MAX_ELAPSED = Duration.ofMinutes(5);

    /** The run completed, every segment was decided once and exported, and nothing unexplained was logged. */
    static void assertFinishedAndDecided(final SoakRun.Outcome outcome) {
        final SegmentCounts counts = outcome.counts();
        assertThat(outcome.report().end()).as(outcome.events().toString()).isEqualTo(JobState.COMPLETED);
        assertThat(counts.pending()).isZero();
        assertThat(counts.accepted() + counts.revised() + counts.flagged() + counts.sourceKept())
                .isEqualTo(outcome.documentSegments());
        assertThat(outcome.report().accepted() + outcome.report().flagged())
                .isEqualTo(outcome.report().segments());
        assertThat(outcome.report().flaggedSegments()).hasSize(outcome.report().flagged());
        assertThat(outcome.events().finished()).isEqualTo(1);
        // Only a segment flagged with no machine target at all (an empty reply) is written in its source.
        assertThat(outcome.export().pending())
                .isLessThanOrEqualTo(outcome.report().flagged());
        assertThat(outcome.exportedSegments()).isEqualTo(outcome.documentSegments());
        assertThat(outcome.errors()).isEmpty();
    }

    /** Memory, threads and time stayed inside what a night's run can afford. */
    static void assertBounded(final SoakRun.Outcome outcome) {
        final HeapSamples heap = outcome.heap();
        assertThat(heap.endMib()).as(heap.toString()).isLessThan(MAX_HEAP_MIB);
        assertThat(outcome.threadsAfter()).isLessThanOrEqualTo(outcome.threadsBefore() + EXTRA_THREADS);
        assertThat(outcome.elapsed()).isLessThan(MAX_ELAPSED);
    }

    /**
     * What the run retains grows only by what each decided segment keeps — its record, targets and memory entry — and
     * stays far below a size a night could fill. Measured on a long book only: on a short one the noise of a collection
     * is larger than the book.
     */
    static void assertRetainedPerSegment(final SoakRun.Outcome outcome) {
        final HeapSamples heap = outcome.heap();
        assertThat(heap.bytesPerSegment(outcome.documentSegments()))
                .as(heap.toString())
                .isLessThan(MAX_BYTES_PER_SEGMENT);
    }

    /** Each injected kind of failure was met and answered the way the run answers it. */
    static void assertEveryFaultAnswered(final SoakRun.Outcome outcome) {
        final EnumSet<Fault> faults = EnumSet.allOf(Fault.class);
        assertThat(outcome.injected()).as("every fault was injected").containsOnlyKeys(faults);
        assertThat(outcome.events().pauses())
                .as(outcome.events().toString())
                .containsKeys(
                        ErrorCode.timeout,
                        ErrorCode.upstream,
                        ErrorCode.unreachable,
                        ErrorCode.modelUnavailable,
                        ErrorCode.internal);
        assertThat(outcome.events().stalls())
                .as("the watchdog ended hanging calls")
                .isPositive();
        assertThat(outcome.events().longestOutage())
                .as("the long outage was waited through")
                .isGreaterThanOrEqualTo(7);
        assertThat(outcome.virtual()).isGreaterThan(Duration.ofMinutes(25));
        assertThat(reasons(outcome.report().flaggedSegments()))
                .contains(ErrorCode.emptyCompletion, ErrorCode.validation, ErrorCode.timeout);
    }

    /** No placeholder of the masking survived into the exported text. */
    static void assertNoLeftoverPlaceholders(final Path exported) {
        try {
            assertThat(Files.readString(exported, StandardCharsets.UTF_8)).doesNotContain("⟦g");
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /** Copies one fixture book, and the images a Markdown book links to, into {@code dir}. */
    static Path copyFixture(final String fileName, final Path dir) {
        try {
            final Path images = dir.resolve("images");
            Files.createDirectories(images);
            try (var files = Files.list(FIXTURES.resolve("images"))) {
                files.forEach(image -> copy(image, images.resolve(image.getFileName())));
            }
            return Files.copy(FIXTURES.resolve(fileName), dir.resolve(fileName));
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    /**
     * Runs the repository's validator over an export, structure checks only: the pseudo model's Cyrillic capitals are no
     * language a language check could judge.
     *
     * @return the validator's exit code followed by its output
     */
    static List<String> validate(final String format, final String fixture, final Path exported) {
        final Path repository = Path.of("..", "..").toAbsolutePath().normalize();
        final ProcessBuilder builder = new ProcessBuilder(
                        "python3",
                        repository
                                .resolve("scripts/validate-translated-book.py")
                                .toString(),
                        format,
                        FIXTURES.resolve(fixture).toAbsolutePath().normalize().toString(),
                        exported.toString(),
                        "--lang",
                        "none")
                .redirectErrorStream(true);
        try {
            final Process process = builder.start();
            final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.waitFor(2, TimeUnit.MINUTES)).isTrue();
            return List.of(String.valueOf(process.exitValue()), output);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while validating", cause);
        }
    }

    private static Set<ErrorCode> reasons(final List<FlaggedSegment> flagged) {
        return Set.copyOf(
                Arrays.asList(flagged.stream().map(FlaggedSegment::reason).toArray(ErrorCode[]::new)));
    }

    private static void copy(final Path from, final Path to) {
        try {
            Files.copy(from, to);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }
}
