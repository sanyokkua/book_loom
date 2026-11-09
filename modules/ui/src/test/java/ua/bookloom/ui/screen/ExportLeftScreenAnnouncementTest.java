package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ProofreadSummary;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * An export ends on the Export screen, which it keeps in front (navigation is locked while it runs and it cannot be put
 * in the background): the card opens on its own and no message is raised.
 */
class ExportLeftScreenAnnouncementTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void startHeldExport() throws TimeoutException {
        desk.willAnswerCounts(counts(0));
        exportService().announce(new ExportProgress(ExportProgress.Step.WRITING, 0, 1));
        exportService().holdRuns();
        exportService().reportWith(report(Path.of("Frankenstein.uk.epub"), List.of(), ProofreadSummary.NONE));
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
        onFx(() -> button("export-run").fire());
        awaitFx(() -> optional("busy-card") != null && optional("busy-card").isVisible());
    }

    private List<Node> toastActions() {
        return ThemeTestSupport.onFx(
                () -> List.copyOf(required("shell-toast-host").lookupAll(".toast-action")));
    }

    // IF the card also raised a message on the screen, THEN the person would be told twice.
    @Test
    void export_endsOnTheExportScreen_opensTheCardAndRaisesNoMessage() throws TimeoutException {
        startHeldExport();

        exportService().release();

        awaitFx(() -> optional("export-complete-card") != null);
        assertThat(toastActions()).isEmpty();
    }
}
