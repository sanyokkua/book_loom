package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.ViewNames;

/**
 * "All calls…" over a real run: the production injector, engine and pseudo model, the dialog opened from the live card
 * in the window's scene, and every row read as a person reads it by clicking it.
 */
// `slow`: a whole run end to end; `test` and the gate run it, the `fastTest` inner loop leaves it out.
@Tag("slow")
class AllCallsEndToEndTest extends WorkspaceTestBase {

    private static final String SOURCE_TEXT =
            "It was a dark night.\n\nThe rain fell on the old house.\n\nNobody came to the door.\n";
    private static final String NOTHING_YET = "Nothing yet.";

    private final FxRobot robot = new FxRobot();

    // IF an answered translate call said "Nothing yet." (the real window showed it), THEN the dialog would hide what
    // the model wrote; each translate row says what it brought back, the pseudo model's capitals.
    @Test
    void allCalls_afterARealRun_eachAnsweredTranslateCallShowsTheTextItBroughtBack() throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Letter.txt"), SOURCE_TEXT, StandardCharsets.UTF_8);
        startWorkspace(ReviewMode.UNATTENDED);
        openBook(book, 3);
        chooseBrief("uk", booksDir.resolve("Letter.uk.txt"));
        chooseModel();
        onFx(() -> shell.activate(ViewNames.TRANSLATING));
        runToTheEnd();

        onFx(() -> ((Button) exportNode("#translating-live-all-calls")).fire());
        WaitForAsyncUtils.waitForFxEvents();
        final List<String> rows = readEveryRow();

        assertThat(rows).isNotEmpty().noneMatch(row -> row.endsWith("| " + NOTHING_YET));
        assertThat(rows)
                .filteredOn(row -> row.startsWith("Translate is done."))
                .isNotEmpty()
                .anyMatch(row -> row.contains("IT WAS A DARK NIGHT."));
    }

    // Each row as "<sentence> | <what it says>", newest first, clicked in turn as a person would.
    private List<String> readEveryRow() {
        final int count = onFx(() ->
                ((ListView<?>) exportNode("#calls-card .list-view")).getItems().size());
        return IntStream.range(0, count).mapToObj(this::readRow).toList();
    }

    private String readRow(final int index) {
        final Node row = onFx(() -> lookup("#progress-step-" + index));
        robot.clickOn(row);
        WaitForAsyncUtils.waitForFxEvents();
        return onFx(() -> ((Label) lookup("#progress-say")).getText() + " | "
                + ((TextArea) lookup("#progress-output")).getText());
    }

    private static Node lookup(final String selector) {
        return FxToolkit.toolkitContext().getRegisteredStage().getScene().lookup(selector);
    }
}
