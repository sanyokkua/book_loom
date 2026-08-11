package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.BookFormat;

/**
 * The corpus verification (design.md D6, D6a; task group 11): opens every book in a locally configured corpus
 * directory, reassembles each with zero edits, reassembles that output a second time, reassembles the book again
 * with target text set on every segment, re-opens each output, and records every book's outcome to a report
 * outside the repository.
 *
 * <p><strong>Environment-gated, like {@code liveLocal}/{@code promptEval}/{@code visual}.</strong> With
 * {@code BOOKLOOM_CORPUS_DIR} unset — the state of every CI runner and most local checkouts, since the corpus is
 * 314&nbsp;MB of third-party copyrighted material and git-ignored — {@link #verify_localCorpusConfigured_recordsEveryBooksOutcome}
 * skips via a JUnit assumption and the build stays green. That one method carries the {@code corpus} tag; the
 * scenarios that do not depend on a real corpus are proven below against small hand-built fixtures and DO run in
 * the standard test task, because a proof excluded from the gate is not a proof.
 */
@Slf4j
class CorpusVerificationTest {

    private static final String CORPUS_DIR_ENV = "BOOKLOOM_CORPUS_DIR";
    private static final String REPORT_DIR_ENV = "BOOKLOOM_CORPUS_REPORT_DIR";

    // Covers: FR-DOC-09 — WHEN the merge gate runs with a corpus directory configured, THEN the verification does
    // not run, because it is excluded from `check` by its own tag.
    //
    // `@Tag` sits on this ONE method rather than on the class. It is the only test here that needs a real corpus;
    // the other seven prove the requirement's remaining scenarios against hand-built `@TempDir` fixtures and need
    // nothing the checkout does not have. Tagging the class excluded those proofs from `check` too, which left the
    // requirement's distinguishing clause — that a run can tell "nothing threw" from "nothing changed" — with no
    // proof that any gate actually executes.
    @Test
    @Tag("corpus")
    void verify_localCorpusConfigured_recordsEveryBooksOutcome() throws IOException {
        final Optional<Path> corpusDir = resolveCorpusDir(System.getenv(CORPUS_DIR_ENV));
        Assumptions.assumeTrue(
                corpusDir.isPresent(), () -> CORPUS_DIR_ENV + " is not set; skipping the corpus verification");

        final Path reportDir = resolveReportDir(System.getenv(REPORT_DIR_ENV));
        log.info("Corpus verification: corpus={} report={}", corpusDir.get(), reportDir);

        final List<CorpusBookOutcome> outcomes = CorpusSweep.verifyCorpus(corpusDir.get(), reportDir);

        log.info("Corpus verification processed {} book(s); report written under {}", outcomes.size(), reportDir);
        assertThat(reportDir.resolve(CorpusReportSink.JSONL_FILE_NAME)).exists();
        assertThat(reportDir.resolve(CorpusReportSink.SUMMARY_FILE_NAME)).exists();
    }

    // Covers: FR-DOC-09 — WHEN the standard test task runs with no corpus directory configured, THEN the
    // verification is reported as skipped and the build succeeds.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void resolveCorpusDir_noCorpusDirectoryConfigured_resolvesToEmpty(@Nullable String envValue) {
        assertThat(resolveCorpusDir(envValue)).isEmpty();
    }

    // Covers trap 2 (design.md D6): the test JVM's working directory is the module, not the repository root, so a
    // relative configured path throws `NoSuchFileException` naming the wrong thing. Resolving to an absolute path
    // first and failing loudly with THAT path is what this proves.
    @Test
    void resolveCorpusDir_configuredDirectoryDoesNotExist_failsNamingTheResolvedAbsolutePath() {
        final String configured = "this-directory-does-not-exist-anywhere";
        final String resolved = Path.of(configured).toAbsolutePath().normalize().toString();

        assertThatThrownBy(() -> resolveCorpusDir(configured))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(resolved);
    }

    // Covers: FR-DOC-09 — WHEN the verification runs over a corpus in which one book fails to open, THEN that
    // book's failure is recorded with its error code, AND every other book in the corpus is still processed and
    // recorded.
    @Test
    void verifyCorpus_oneBookFailsToOpen_recordsItAndStillProcessesTheRest(@TempDir Path tempDir) throws IOException {
        final Path corpusDir = tempDir.resolve("corpus");
        final Path reportDir = tempDir.resolve("report");
        Files.createDirectories(corpusDir);
        Files.writeString(
                corpusDir.resolve("good.txt"), "First paragraph.\n\nSecond paragraph.\n", StandardCharsets.UTF_8);
        Files.write(corpusDir.resolve("bad.epub"), "not a zip archive".getBytes(StandardCharsets.UTF_8));

        final List<CorpusBookOutcome> outcomes = CorpusSweep.verifyCorpus(corpusDir, reportDir);

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.fileName().equals("bad.epub"))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.open()).isInstanceOf(CorpusOpenOutcome.Failed.class));
        assertThat(outcomes)
                .filteredOn(outcome -> outcome.fileName().equals("good.txt"))
                .singleElement()
                .satisfies(outcome -> assertThat(outcome.open()).isInstanceOf(CorpusOpenOutcome.Opened.class));
    }

    // Covers: FR-DOC-09 — WHEN the verification runs with a corpus directory that exists and contains no book
    // files, THEN the run completes, records zero books processed, and the build succeeds.
    @Test
    void verifyCorpus_emptyCorpusDirectory_recordsZeroBooksAndSucceeds(@TempDir Path tempDir) throws IOException {
        final Path corpusDir = tempDir.resolve("empty-corpus");
        final Path reportDir = tempDir.resolve("report");
        Files.createDirectories(corpusDir);

        final List<CorpusBookOutcome> outcomes = CorpusSweep.verifyCorpus(corpusDir, reportDir);

        assertThat(outcomes).isEmpty();
        assertThat(reportDir.resolve(CorpusReportSink.JSONL_FILE_NAME)).exists();
    }

    // Covers: FR-DOC-09 — WHEN the verification runs over a corpus containing a book that opens and writes
    // without error, but whose zero-edit output is not structurally equal to the source, THEN that book's outcome
    // is recorded as failed, distinctly from a book that round-tripped faithfully.
    @Test
    void compareCanonical_distinguishesFaithfulFromUnfaithfulMarkdownOutput(@TempDir Path tempDir) throws IOException {
        final Path source = tempDir.resolve("source.md");
        final Path faithfulOutput = tempDir.resolve("faithful.md");
        final Path unfaithfulOutput = tempDir.resolve("unfaithful.md");
        Files.writeString(source, "# Title\n\nOne paragraph.\n", StandardCharsets.UTF_8);
        Files.writeString(faithfulOutput, "# Title\n\nOne paragraph.\n", StandardCharsets.UTF_8);
        Files.writeString(
                unfaithfulOutput,
                "# Title\n\nOne paragraph.\n\nAn extra paragraph the source never had.\n",
                StandardCharsets.UTF_8);

        final CorpusSweep.CanonicalComparison faithful =
                CorpusSweep.compareCanonical(BookFormat.MARKDOWN, source, faithfulOutput, "uk");
        final CorpusSweep.CanonicalComparison unfaithful =
                CorpusSweep.compareCanonical(BookFormat.MARKDOWN, source, unfaithfulOutput, "uk");

        assertThat(faithful.ok()).isTrue();
        assertThat(unfaithful.ok()).isFalse();
    }

    /**
     * Resolves {@code BOOKLOOM_CORPUS_DIR} to an absolute, confirmed-to-exist directory, or {@link Optional#empty}
     * when it is unset/blank — the signal that skips the whole verification.
     *
     * @param envValue the raw environment variable value, or {@code null} when unset
     * @return the resolved directory, or empty when unconfigured
     * @throws AssertionError if a directory was configured but does not exist at the resolved absolute path
     */
    static Optional<Path> resolveCorpusDir(@Nullable String envValue) {
        if (envValue == null || envValue.isBlank()) {
            return Optional.empty();
        }
        final Path resolved = Path.of(envValue).toAbsolutePath().normalize();
        if (!Files.isDirectory(resolved)) {
            throw new AssertionError("Configured " + CORPUS_DIR_ENV + " does not exist: " + resolved);
        }
        return Optional.of(resolved);
    }

    /**
     * Resolves {@code BOOKLOOM_CORPUS_REPORT_DIR}, or falls back to a fresh temp directory so a run always leaves
     * evidence even when the report location was never configured.
     *
     * @param envValue the raw environment variable value, or {@code null} when unset
     * @return the resolved, absolute report directory
     * @throws IOException if the fallback temp directory cannot be created
     */
    static Path resolveReportDir(@Nullable String envValue) throws IOException {
        if (envValue == null || envValue.isBlank()) {
            final Path fallback = Files.createTempDirectory("bookloom-corpus-report-");
            log.info("{} not set; writing the corpus verification report under {}", REPORT_DIR_ENV, fallback);
            return fallback;
        }
        return Path.of(envValue).toAbsolutePath().normalize();
    }
}
