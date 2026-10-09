package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.FileRevealer;

/** The export-complete card: what it reports about the written book and what its three buttons do. */
class ExportCompleteDialogTest extends ShellTestBase {

    private static final Path BOOK = Path.of("/books/Frankenstein.uk.epub");
    private static final int SIZE = 958_464;

    private void show() {
        show(List.of(), ConsistencySummary.NOT_RUN);
    }

    private void show(final List<Path> sideFiles, final ConsistencySummary consistency) {
        final ExportReport report = new ExportReport(BOOK, 10, 0, 0, 0, 8, 2, sideFiles, 10, consistency, 0);
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#" + ExportCompleteDialog.CARD_ID);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private RecordingFileRevealer revealer() {
        return (RecordingFileRevealer) injector.getInstance(FileRevealer.class);
    }

    @Test
    void show_outcome_namesTheFileFolderVerificationAndSizeWithThreeButtons() {
        show();

        assertThat(textsUnder(required(ExportCompleteDialog.CARD_ID)))
                .contains(
                        "Export complete",
                        "Frankenstein.uk.epub was written and validated.",
                        "/books",
                        "Re-opened and verified",
                        "936 KB",
                        "Close",
                        "Open folder",
                        "Open book");
    }

    // IF a long folder were only cut short, THEN a person could not tell where the book went.
    @Test
    void show_outcome_locationShowsTheWholeFolderOnHover() {
        show();

        assertThat(TooltipProbe.tipText(required("export-complete-location"))).isEqualTo("/books");
    }

    @Test
    void show_sideFilesAndAConsistencyPass_listsEachFileAndTheResult() {
        show(
                List.of(Path.of("/books/Frankenstein.uk.glossary.csv"), Path.of("/books/Frankenstein.uk.report.md")),
                new ConsistencySummary(ConsistencySummary.Status.RAN, 1, 0));

        assertThat(textsUnder(required(ExportCompleteDialog.CARD_ID)))
                .contains(
                        "Also written",
                        "Frankenstein.uk.glossary.csv",
                        "Frankenstein.uk.report.md",
                        "Consistency pass: 1 segment adjusted");
    }

    // More characters wait than the line names: the five most waiting are named and the rest counted.
    @Test
    void show_sevenCharactersAwaitGender_namesFiveAndCountsTheRest() {
        show(
                List.of(),
                new ConsistencySummary(
                        ConsistencySummary.Status.RAN,
                        0,
                        0,
                        java.util.Map.of(ua.bookloom.api.project.DeferralReason.GENDER_UNKNOWN, 20),
                        0,
                        ua.bookloom.api.pipeline.ConsistencyChecks.NONE,
                        List.of(
                                new ua.bookloom.api.pipeline.GenderWait("A", 7),
                                new ua.bookloom.api.pipeline.GenderWait("B", 6),
                                new ua.bookloom.api.pipeline.GenderWait("C", 5),
                                new ua.bookloom.api.pipeline.GenderWait("D", 4),
                                new ua.bookloom.api.pipeline.GenderWait("E", 3),
                                new ua.bookloom.api.pipeline.GenderWait("F", 2),
                                new ua.bookloom.api.pipeline.GenderWait("G", 1))));

        assertThat(textsUnder(required("export-complete-awaiting-gender")))
                .contains("20 segments wait on a character's gender: A ×7, B ×6, C ×5, D ×4, E ×3 and 2 more."
                        + " Set it in Names & style; the next export re-renders them.");
    }

    @Test
    void show_noSideFilesAndNoPass_mentionsNeither() {
        show();

        assertThat(textsUnder(required(ExportCompleteDialog.CARD_ID)))
                .noneMatch(text -> text.startsWith("Also written") || text.startsWith("Consistency pass"));
    }

    // Segments written in the source for a broken translation are named, so a partial book is never a surprise.
    @Test
    void show_twoSourceFallbacks_namesTheirCountAndLocators() {
        final ExportReport report = new ExportReport(
                BOOK,
                8,
                2,
                0,
                0,
                8,
                0,
                List.of(),
                10,
                ConsistencySummary.NOT_RUN,
                0,
                List.of(
                        new SourceFallback("part0009.html:1", "ch12 · p02"),
                        new SourceFallback("part0009.html:5", "ch12 · p06")));
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));

        final Label line = (Label) scene.getRoot().lookup("#export-complete-source-fallbacks-text");
        assertThat(line.getText())
                .isEqualTo("2 segments were written in the source language because their translation broke the"
                        + " formatting: ch12 · p02, ch12 · p06");
    }

    // IF a flagged segment with no translation were written in the source without being named, THEN the book would hold
    // an untranslated paragraph nobody could find.
    @Test
    void show_flaggedSegmentsWithNoTarget_areNamedOnTheirOwnLine() {
        final ExportReport report = new ExportReport(
                BOOK,
                8,
                2,
                0,
                0,
                8,
                0,
                List.of(),
                10,
                ConsistencySummary.NOT_RUN,
                0,
                List.of(
                        new SourceFallback("part0009.html:1", "ch12 · p02", SourceFallback.Reason.NO_TARGET),
                        new SourceFallback("part0009.html:5", "ch12 · p06", SourceFallback.Reason.NO_TARGET)),
                List.of());
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));

        final Label line = (Label) scene.getRoot().lookup("#export-complete-no-target-text");
        assertThat(line.getText())
                .isEqualTo("2 flagged segments have no translation and were written in the source language:"
                        + " ch12 · p02, ch12 · p06");
        assertThat(scene.getRoot().lookup("#export-complete-source-fallbacks-text"))
                .isNull();
    }

    // IF a partial book were reported as only "written and validated", THEN the untranslated part would be a surprise;
    // the two segments written in the source for a broken translation are named on their own line, not counted here.
    @Test
    void show_pendingSegments_saysHowManyAreStillUntranslatedAndWrittenInTheSource() {
        final ExportReport report = new ExportReport(
                BOOK,
                8,
                58,
                0,
                0,
                8,
                0,
                List.of(),
                10,
                ConsistencySummary.NOT_RUN,
                0,
                List.of(
                        new SourceFallback("part0009.html:1", "ch12 · p02"),
                        new SourceFallback("part0009.html:5", "ch12 · p06")));
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));

        assertThat(((Label) required("export-complete-pending-text")).getText())
                .isEqualTo("56 segments are still untranslated and were written in the source language");
    }

    // IF nothing is pending, THEN the card says nothing about untranslated segments.
    @Test
    void show_nothingPending_hasNoUntranslatedLine() {
        show();

        assertThat(scene.getRoot().lookup("#export-complete-pending")).isNull();
    }

    // IF the validation value were drawn as a framed chip in a row of plain values, THEN it would look like a control;
    // it is the success colour's words after a check mark.
    @Test
    void show_outcome_validationIsSuccessColouredWordsAfterACheckMark() {
        show();

        final Label verified = (Label) required("export-complete-verified");
        assertThat(verified.getStyleClass()).contains("status-ok").doesNotContain("chip-ok");
        assertThat(verified.getGraphic()).isInstanceOf(Label.class);
        assertThat(((Label) verified.getGraphic()).getText()).isEqualTo("✓");
        assertThat(ThemeTestSupport.onFx(() -> verified.getTextFill()))
                .isEqualTo(ThemeTestSupport.onFx(() -> ((Label) verified.getGraphic()).getTextFill()));
    }

    @Test
    void footer_threeButtons_areContiguousAtTheRightInMockupOrder() {
        show();

        final Button close = (Button) required(ExportCompleteDialog.CLOSE_ID);
        final Button folder = (Button) required(ExportCompleteDialog.FOLDER_ID);
        final Button open = (Button) required(ExportCompleteDialog.OPEN_ID);
        final double[] edges = new double[5];
        onFx(() -> {
            final Node card = required(ExportCompleteDialog.CARD_ID);
            final var c = close.localToScene(close.getBoundsInLocal());
            final var f = folder.localToScene(folder.getBoundsInLocal());
            final var o = open.localToScene(open.getBoundsInLocal());
            final double cardRight = card.localToScene(card.getLayoutBounds()).getMaxX();
            edges[0] = f.getMinX() - c.getMaxX();
            edges[1] = o.getMinX() - f.getMaxX();
            edges[2] = cardRight - o.getMaxX();
            edges[3] = c.getMinX();
            edges[4] = f.getMinX();
        });

        assertThat(edges[0]).isBetween(0.0, 16.0);
        assertThat(edges[1]).isBetween(0.0, 16.0);
        assertThat(edges[0]).isEqualTo(edges[1]);
        assertThat(edges[2]).isLessThan(40.0);
        assertThat(edges[3]).isLessThan(edges[4]);
    }

    @Test
    void openFolder_pressed_revealsTheFile() {
        show();

        onFx(() -> ((Button) required(ExportCompleteDialog.FOLDER_ID)).fire());

        assertThat(revealer().revealed()).containsExactly(BOOK);
        assertThat(revealer().opened()).isEmpty();
    }

    @Test
    void openBook_pressed_opensTheFile() {
        show();

        onFx(() -> ((Button) required(ExportCompleteDialog.OPEN_ID)).fire());

        assertThat(revealer().opened()).containsExactly(BOOK);
        assertThat(revealer().revealed()).isEmpty();
    }

    @Test
    void close_pressed_hidesTheCardAndOpensNothing() {
        show();

        onFx(() -> ((Button) required(ExportCompleteDialog.CLOSE_ID)).fire());

        assertThat(isShowing()).isFalse();
        assertThat(revealer().revealed()).isEmpty();
        assertThat(revealer().opened()).isEmpty();
    }

    // IF the card did not point at the report, THEN a person could finish an export without learning that lines of the
    // book were written as they are and a file beside it says which.
    @Test
    void show_segmentsWrittenAsTheyAreAndAReportBesideTheBook_pointsAtTheReport() {
        final ExportReport report = new ExportReport(
                BOOK,
                8,
                1,
                0,
                0,
                8,
                0,
                List.of(Path.of("/books/Frankenstein.uk.report.md")),
                10,
                ConsistencySummary.NOT_RUN,
                0,
                List.of(new SourceFallback("part0009.html:1", "ch12 · p02", SourceFallback.Reason.NO_TARGET)));
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));

        final Label line = (Label) scene.getRoot().lookup("#export-complete-report");
        assertThat(line.getText())
                .isEqualTo("The report Frankenstein.uk.report.md beside the book lists every segment that was written"
                        + " as it is, not as an accepted translation.");
    }

    // A clean book has nothing to point at, even when the person asked for the report.
    @Test
    void show_cleanBookWithAReportBesideIt_hasNoReportLine() {
        final ExportReport report = new ExportReport(
                BOOK,
                10,
                0,
                0,
                0,
                8,
                2,
                List.of(Path.of("/books/Frankenstein.uk.report.md")),
                10,
                ConsistencySummary.NOT_RUN,
                0);
        onFx(() -> injector.getInstance(ExportCompleteDialog.class).show(new ExportOutcome(report, SIZE)));

        assertThat(scene.getRoot().lookup("#export-complete-report")).isNull();
    }
}
