package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.state.ExportOutcome;
import ua.bookloom.ui.state.FileRevealer;

/** The export-complete card: what it reports about the written book and what its three buttons do. */
class ExportCompleteDialogTest extends ShellTestBase {

    private static final Path BOOK = Path.of("/books/Frankenstein.uk.epub");
    private static final int SIZE = 958_464;

    private void show() {
        final ExportReport report = new ExportReport(BOOK, 10, 0, 0, 0, 8, 2, List.of(), 10);
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
