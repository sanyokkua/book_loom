package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewCounts;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.ui.BookFixtures;

/** What the export screen states before a book is written: refusals, availability and the partial-export lines. */
class ExportViewModelTest extends ExportViewModelTestBase {

    private static final String PAUSE_NOTE = "Pause the run to export";

    @TempDir
    private Path dir;

    @BeforeEach
    void openTheBook() {
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankensteinImport());
    }

    @Test
    void refusal_destinationExistsAndReplaceOff_namesThePathAndBlocksExport() throws IOException {
        final Path taken = Files.writeString(dir.resolve("Frankenstein.uk.epub"), "here");
        refresh();

        assertThat(refusal()).contains(taken.toString());
        assertThat(available()).isFalse();
        setOverwrite(true);
        assertThat(refusal()).isEmpty();
        assertThat(available()).isTrue();
    }

    @Test
    void refusal_chosenGlossaryFileExists_blocksExportAndUnchosenDoesNot() throws IOException {
        final Path glossary = Files.writeString(dir.resolve("Frankenstein.uk.glossary.csv"), "here");
        refresh();

        assertThat(refusal()).contains(glossary.toString());
        assertThat(available()).isFalse();
        setSideFile(SideFile.GLOSSARY_CSV, false);
        assertThat(refusal()).isEmpty();
        assertThat(available()).isTrue();
    }

    @Test
    void refusal_destinationIsTheSource_isRefusedWithoutTouchingTheDisk() {
        editDestination(dir.resolve("Frankenstein.epub").toString());

        assertThat(refusal()).isNotEmpty();
        assertThat(available()).isFalse();
    }

    @Test
    void refusal_changedFileType_isRefused() {
        editDestination(dir.resolve("Frankenstein.uk.fb2").toString());

        assertThat(refusal()).isNotEmpty();
        assertThat(available()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"IDLE", "PAUSED", "STOPPED", "FAILED", "COMPLETED"})
    void exportAvailable_runNotTranslating_isTrueWithNoNote(final RunState state) {
        publish(state);

        assertThat(available()).isTrue();
        assertThat(runNote()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(
            value = RunState.class,
            names = {"RUNNING", "PAUSING", "STOPPING"})
    void exportAvailable_runTranslating_isFalseWithTheNote(final RunState state) {
        publish(state);

        assertThat(available()).isFalse();
        assertThat(runNote()).isEqualTo(PAUSE_NOTE);
    }

    @ParameterizedTest
    @CsvSource({
        "1240, 900, 25, 3, 0, 312, 0, 0, 0, '312 segments will be written in the source language|3 flagged segments will be written with their machine translation'",
        "12, 8, 0, 3, 0, 1, 0, 1, 0, '2 segments will be written in the source language|2 flagged segments will be written with their machine translation'",
        "12, 8, 0, 1, 0, 3, 0, 1, 0, '4 segments will be written in the source language'",
        "1260, 913, 25, 3, 0, 312, 7, 0, 0, '312 segments will be written in the source language|3 flagged segments will be written with their machine translation|7 segments are kept as source by choice'",
        "1247, 1215, 25, 0, 0, 0, 7, 0, 0, '7 segments are kept as source by choice'",
        "10, 9, 0, 1, 0, 0, 1, 0, 0, '1 flagged segment will be written with its machine translation|1 segment is kept as source by choice'",
        "40, 37, 0, 0, 0, 0, 1, 0, 2, '1 segment is kept as source by choice|2 segments are kept as is: they have nothing to translate (numbers, symbols)'",
        "40, 39, 0, 0, 0, 0, 0, 0, 1, '1 segment is kept as is: it has nothing to translate (numbers, symbols)'"
    })
    void statement_countsFromTheDesk_statesEachNonZeroLine(
            final int total,
            final int auto,
            final int repaired,
            final int flagged,
            final int reviewed,
            final int pending,
            final int kept,
            final int noTarget,
            final int verbatim,
            final String expected) {
        desk.willAnswerCounts(
                new ReviewCounts(total, auto, repaired, flagged, reviewed, pending, kept, noTarget, verbatim));

        onFx(() -> {
            exports.refreshStatement();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(statement()).isEqualTo(List.of(expected.split("\\|")));
    }

    @ParameterizedTest
    @CsvSource({"MAX, true", "BALANCED, false", "FAST, false"})
    void consistencyPass_followsTheDial(final QualityDial dial, final boolean expected) {
        onFx(() -> {
            brief.setDial(dial);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> exports.consistencyPass().get())).isEqualTo(expected);
    }

    private void publish(final RunState state) {
        onFx(() -> {
            mirror.publishRunState(state);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void setSideFile(final SideFile file, final boolean chosen) {
        onFx(() -> {
            exports.setSideFile(file, chosen);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private String refusal() {
        return onFx(() -> exports.refusal().get());
    }

    private boolean available() {
        return onFx(() -> exports.exportAvailable().get());
    }

    private String runNote() {
        return onFx(() -> exports.runNote().get());
    }

    private List<String> statement() {
        return onFx(() -> List.copyOf(exports.statement()));
    }
}
