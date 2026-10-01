package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ShellTestBase;
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

    @Test
    void show_noSideFilesAndNoPass_mentionsNeither() {
        show();

        assertThat(textsUnder(required(ExportCompleteDialog.CARD_ID)))
                .noneMatch(text -> text.startsWith("Also written") || text.startsWith("Consistency pass"));
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
}
