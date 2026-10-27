package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * An export that ends while the Export screen is not on show is still announced: a message offers the Export complete
 * card and nothing opens by itself; on the screen the card opens on its own.
 */
class ExportLeftScreenAnnouncementTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");

    private void startHeldExport() throws TimeoutException {
        stored.put(AppSetting.LAST_CHECK, true);
        desk.willAnswerCounts(counts(0));
        exportService()
                .announce(new ExportProgress(
                        ExportProgress.Step.CONSISTENCY_NEIGHBOUR,
                        1,
                        4,
                        "ch1:1",
                        "Вона втомилася",
                        null,
                        "unchanged",
                        new ExportProgress.Neighbours(
                                "She ran.", "Вона бігла.", "She was tired.", "She slept.", "Вона спала.")));
        exportService().holdRuns();
        exportService().reportWith(report(Path.of("Frankenstein.uk.epub"), List.of(), ConsistencySummary.NOT_RUN));
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
        onFx(() -> button("export-run").fire());
        awaitFx(() -> optional("busy-card") != null && optional("busy-card").isVisible());
    }

    private List<Node> toastActions() {
        return ThemeTestSupport.onFx(
                () -> List.copyOf(required("shell-toast-host").lookupAll(".toast-action")));
    }

    // IF a book written while the person was elsewhere went unannounced, THEN the file would exist and nobody know.
    @Test
    void export_endsWhileAnotherScreenShows_raisesAMessageAndOpensNothing() throws TimeoutException {
        startHeldExport();
        onFx(() -> button("progress-background").fire());
        onFx(() -> shell.activate(ViewNames.IMPORT));

        exportService().release();

        awaitFx(() -> !toastActions().isEmpty());
        assertThat(optional("export-complete-card")).isNull();
        assertThat(((Button) toastActions().getFirst()).getText()).isEqualTo("Review the results");
    }

    // IF the message's action did not come back to Export, THEN the card would open over a screen it does not belong
    // to.
    @Test
    void messageAction_pressed_returnsToExportAndOpensTheCardOnce() throws TimeoutException {
        startHeldExport();
        onFx(() -> button("progress-background").fire());
        onFx(() -> shell.activate(ViewNames.IMPORT));
        exportService().release();
        awaitFx(() -> !toastActions().isEmpty());

        onFx(() -> ((Button) toastActions().getFirst()).fire());

        awaitFx(() -> optional("export-complete-card") != null);
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.EXPORT);
        assertThat(textOf("export-complete-card")).contains("Frankenstein.uk.epub");
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
