package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxToolkit;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.StructureSegmentRow;
import ua.bookloom.ui.state.StructureSegmentsViewModel;

/**
 * The Import screen's three-pane browser over a real Markdown book, through the production document pipeline and
 * project service: the segment a person picks shows its text, and keeps showing it while the listing is asked again and
 * while the window narrows past the browser's stacked layout.
 */
class ImportBrowserTextTest extends WorkspaceTestBase {

    private static final String SOURCE =
            "# The Garden\n\nThe gate was open in the morning.\n\nA bird sat on the wall and sang.\n";
    private static final String PICKED = "The gate was open in the morning.";
    private static final double NARROW_WIDTH = 700;

    // IF picking a segment of a real Markdown book left the text pane empty, THEN a person could not read it on Import.
    @Test
    void textPane_markdownSegmentPicked_showsItsTextWithASize() throws Exception {
        openMarkdownAndPick();

        assertThat(onFx(() -> segmentList().getItems().stream()
                        .map(row -> row.preview().displaySource())
                        .toList()))
                .containsExactly("The Garden", PICKED, "A bird sat on the wall and sang.");
        assertThat(shownText()).isEqualTo(PICKED);
        assertThat(onFx(this::hasVisibleSize)).isTrue();
    }

    // IF a listing asked again (a brief save) blanked the text pane, THEN the picked segment would vanish by itself.
    @Test
    void textPane_listingAskedAgainAfterAPick_keepsThePickedText() throws Exception {
        openMarkdownAndPick();

        onFx(() -> injector.getInstance(StructureSegmentsViewModel.class).refresh());
        waitUntil(() -> !segmentList().getItems().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(shownText()).isEqualTo(PICKED);
        assertThat(onFx(this::hasVisibleSize)).isTrue();
    }

    // IF the stacked layout under 760 px lost the text pane's content, THEN a narrow window would show no text.
    @Test
    void textPane_windowNarrowedAfterAPick_keepsThePickedText() throws Exception {
        openMarkdownAndPick();

        onFx(() -> stage().setWidth(NARROW_WIDTH));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> ((SplitPane) lookup("#import-browser")).getItems().size()))
                .as("the text sits under the segments")
                .isEqualTo(2);

        assertThat(shownText()).isEqualTo(PICKED);
        assertThat(onFx(this::hasVisibleSize)).isTrue();
    }

    private void openMarkdownAndPick() throws Exception {
        final Path book = Files.writeString(booksDir.resolve("Garden.md"), SOURCE, StandardCharsets.UTF_8);
        startWorkspace(ReviewMode.UNATTENDED);
        openBook(book, 3);
        waitUntil(() -> lookup("#structure-segments") != null
                && !segmentList().getItems().isEmpty());
        // Lets the brief's own saves after opening, and the listings they ask again, settle before the pick.
        waitUntil(() -> !injector.getInstance(BookBriefViewModel.class).saving().get());
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> segmentList().getSelectionModel().select(rowOf(PICKED)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    @SuppressWarnings("unchecked")
    private ListView<StructureSegmentRow> segmentList() {
        return (ListView<StructureSegmentRow>) Objects.requireNonNull(lookup("#structure-segments"));
    }

    private StructureSegmentRow rowOf(final String text) {
        return segmentList().getItems().stream()
                .filter(row -> row.preview().displaySource().equals(text))
                .findFirst()
                .orElseThrow();
    }

    private String shownText() {
        return onFx(() -> ((TextFlow) Objects.requireNonNull(lookup("#structure-preview-text")))
                .getChildren().stream()
                        .filter(Text.class::isInstance)
                        .map(node -> ((Text) node).getText())
                        .collect(Collectors.joining()));
    }

    private boolean hasVisibleSize() {
        final Node flow = Objects.requireNonNull(lookup("#structure-preview-text"));
        final Bounds bounds = flow.localToScene(flow.getLayoutBounds());
        return flow.getScene() != null && flow.isVisible() && bounds.getWidth() > 0 && bounds.getHeight() > 0;
    }

    private static Stage stage() {
        return FxToolkit.toolkitContext().getRegisteredStage();
    }

    private static Node lookup(final String selector) {
        return stage().getScene().lookup(selector);
    }
}
