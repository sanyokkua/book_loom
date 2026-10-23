package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.HiddenWork;

/**
 * The last check before saving as a progress dialog the person can leave or end: its rereads are grouped and shown next
 * to their neighbours, Escape hides it and the title bar's chip brings it back, Cancel asks first, and Write the file
 * now writes the file with the items checked so far.
 */
class ExportLastCheckDialogTest extends ExportScreenTestBase {

    private static final Path EPUB = Path.of("Frankenstein.epub");
    private static final int TOTAL = 18;

    private static ExportProgress[] items(final int count, final String firstVerdict) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(item -> reread(item, item == 1 ? firstVerdict : "unchanged"))
                .toArray(ExportProgress[]::new);
    }

    private static ExportProgress reread(final int item, final String verdict) {
        final boolean fixed = "fixed".equals(verdict);
        return new ExportProgress(
                ExportProgress.Step.CONSISTENCY_NEIGHBOUR,
                item,
                TOTAL,
                "ch1:" + item,
                fixed ? "Вона втомився" : "Вона втомилася",
                fixed ? "Вона втомилася" : null,
                verdict,
                new ExportProgress.Neighbours(
                        "She ran.", "Вона бігла.", "She was tired.", "She slept.", "Вона спала."));
    }

    private static ExportReport stoppedAtSix() {
        final ConsistencySummary summary = new ConsistencySummary(
                ConsistencySummary.Status.RAN, 0, 0, Map.of(), 1, new ConsistencyChecks(0, 0, 5, Map.of(), 0, 12));
        return report(Path.of("Frankenstein.uk.epub"), List.of(), summary);
    }

    private void startLastCheck(final ExportProgress... events) throws TimeoutException {
        stored.put(AppSetting.LAST_CHECK, true);
        desk.willAnswerCounts(counts(0));
        exportService().announce(events);
        exportService().holdRuns();
        exportService().reportWith(stoppedAtSix());
        openAndShowExport(EPUB);
        awaitFx(() -> exports().exportAvailable().get());
        onFx(() -> button("export-run").fire());
        awaitFx(this::isDialogShown);
    }

    private boolean isDialogShown() {
        return optional("busy-card") != null && optional("busy-card").isVisible();
    }

    private void escape() {
        onFx(() -> Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
    }

    // IF the rereads were a flat list of requests, THEN a person could not tell how far the check had got.
    @Test
    void dialog_sixOfEighteenReread_groupsTheItemsWithTheirCount() throws TimeoutException {
        startLastCheck(items(6, "unchanged"));

        awaitFx(() -> textOf("busy-card").contains("Reread next to the neighbours · 6 of 18"));

        assertThat(textOf("busy-card")).contains("Steps").contains("Finish");
    }

    // IF an unchanged item showed a diff, THEN the person would hunt for a change that was never made.
    @Test
    void grid_unchangedItem_readsUnchangedKeptAsItWas() throws TimeoutException {
        startLastCheck(items(6, "unchanged"));

        awaitFx(() -> optional("reread-grid") != null);

        assertThat(textOf("reread-grid"))
                .contains("Before", "After", "Segment before", "This segment", "Segment after")
                .contains("Unchanged — kept as it was");
    }

    // IF the old text were not struck through above the new, THEN a fix would be hard to tell from a rewrite.
    @Test
    void grid_fixedItem_showsTheOldTextStruckThroughAboveTheNewOne() throws TimeoutException {
        startLastCheck(items(1, "fixed"));

        awaitFx(() -> optional("reread-grid") != null);

        assertThat(ThemeTestSupport.onFx(
                        () -> scene.getRoot().lookupAll(".reread-old").size()))
                .isPositive();
        assertThat(textOf("reread-grid")).contains("Вона втомився", "Вона втомилася");
        assertThat(textOf("reread-grid")).doesNotContain("Unchanged — kept as it was");
    }

    // IF Escape asked "Cancel the export?", THEN hiding the check would cost the person a second answer.
    @Test
    void escape_onTheLastCheck_hidesItAndTheChipReopensIt() throws TimeoutException {
        startLastCheck(items(6, "unchanged"));

        escape();
        awaitFx(() -> !isDialogShown());

        assertThat(optional("confirm-card")).isNull();
        assertThat(injector.getInstance(HiddenWork.class).screen().get()).isEqualTo(ViewNames.EXPORT);
        onFx(() -> ((Hyperlink) required("shell-activity-open")).fire());
        awaitFx(this::isDialogShown);
        assertThat(textOf("busy-card")).contains("Reread next to the neighbours · 6 of 18");
    }

    // IF Cancel stopped the check at once, THEN one slip would throw away the whole last check.
    @Test
    void cancel_pressed_asksFirstThatNoFileIsWrittenAndStopsNothingYet() throws TimeoutException {
        startLastCheck(items(6, "unchanged"));

        onFx(() -> ((Button) required("progress-cancel")).fire());

        assertThat(textOf("confirm-card")).contains("Cancel the export?").contains("No file is written.");
        assertThat(exportService().cancels()).isZero();
    }

    @Test
    void cancel_confirmed_cancelsTheExport() throws TimeoutException {
        startLastCheck(items(6, "unchanged"));
        onFx(() -> ((Button) required("progress-cancel")).fire());

        onFx(() -> ((Button) required("confirm-yes")).fire());

        assertThat(exportService().cancels()).isEqualTo(1);
    }

    // IF Write now wrote at once, THEN a stray click would end a long check without the person seeing the cost.
    @Test
    void writeNow_pressed_asksWithTheTallyAndKeepCheckingWritesNothing() throws TimeoutException {
        startLastCheck(items(6, "fixed"));
        awaitFx(() -> textOf("busy-card").contains("6 of 18"));

        onFx(() -> ((Button) required("progress-write-now")).fire());

        assertThat(textOf("confirm-card")).contains("6 of 18 reread, 1 fixed; the other 12 stay as they are");
        onFx(() -> ((Button) required("confirm-cancel")).fire());
        assertThat(exportService().writeNows()).isZero();
    }

    // IF yes did not reach the job, THEN the file would wait for the whole check the person gave up on.
    @Test
    void writeNow_confirmed_tellsTheJobAndTheExportCompleteReportsTheUnreadItems() throws TimeoutException {
        startLastCheck(items(6, "fixed"));
        awaitFx(() -> textOf("busy-card").contains("6 of 18"));
        onFx(() -> ((Button) required("progress-write-now")).fire());

        onFx(() -> ((Button) required("confirm-yes")).fire());
        awaitFx(() -> exportService().writeNows() == 1 && optional("export-complete-card") != null);

        assertThat(textOf("export-complete-card")).contains("Last check").contains("6 segments reread · 1 fixed");
        assertThat(textOf("export-checks"))
                .contains("12 paragraphs were not reread: the file was written before the last check reached them");
    }

    // IF a hidden check ended unseen, THEN the person would never learn the file was written.
    @Test
    void finish_whileTheDialogIsHidden_opensTheExportCompleteDialog() throws TimeoutException {
        startLastCheck(items(2, "unchanged"));
        escape();
        awaitFx(() -> !isDialogShown());

        exportService().release();
        awaitFx(() -> optional("export-complete-card") != null);

        assertThat(textOf("export-complete-card")).contains("Frankenstein.uk.epub");
    }
}
