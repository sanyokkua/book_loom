package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ImportState;

/**
 * The drop zone of the import screen, read from the real scene: the full box while no book is open, and one line with
 * Browse still reachable once a book is open, so that the structure below it has the room.
 */
class ImportDropzoneTest extends ImportBookTestBase {

    private static final String COMPACT = "dropzone-compact";

    /** Whether every shown caption and button of the drop zone sits in the vertical band of its Browse button. */
    private boolean isOneLine() {
        return ThemeTestSupport.onFx(() -> {
            final Node zone = required("import-dropzone");
            final Bounds browse = inSceneOf(required("import-browse"));
            final List<Node> parts = new ArrayList<>();
            collectShownParts(zone, parts);
            return parts.stream()
                    .map(this::inSceneOf)
                    .allMatch(part -> part.getMinY() < browse.getMaxY() && part.getMaxY() > browse.getMinY());
        });
    }

    private Bounds inSceneOf(final Node node) {
        return node.localToScene(node.getBoundsInLocal());
    }

    private static void collectShownParts(final Node node, final List<Node> parts) {
        if (node instanceof Labeled labeled
                && labeled.isVisible()
                && labeled.getText() != null
                && !labeled.getText().isEmpty()) {
            parts.add(node);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectShownParts(child, parts));
        }
    }

    private boolean isCompact() {
        return ThemeTestSupport.onFx(
                () -> required("import-dropzone").getStyleClass().contains(COMPACT));
    }

    // IF the drop zone were compact before any book is open, THEN a person arriving at the screen would have no
    // obvious place to drop a file.
    @Test
    void dropzone_noBookOpen_isTheFullBoxWithTitleAndBrowseStacked() {
        openImport();

        assertThat(isCompact()).isFalse();
        assertThat(isOneLine()).isFalse();
        assertThat(textOf("import-dropzone")).contains("Drag a book here").contains("Browse files...");
        assertThat(isShown("import-browse")).isTrue();
    }

    // IF the full box stayed once a book is open, THEN the book's structure would start below the fold.
    @Test
    void dropzone_bookOpen_isOneLineAboveTheVerdict() throws TimeoutException {
        openFactsBook();

        assertThat(isCompact()).isTrue();
        assertThat(required("import-dropzone").getStyleClass()).contains("dropzone");
        assertThat(isOneLine()).isTrue();
        assertThat(boundsOf("import-dropzone").getMaxY())
                .isLessThanOrEqualTo(boundsOf("import-verdict").getMinY());
    }

    // IF compacting the box hid or disabled the one way to pick another book, THEN a person could not change books.
    @Test
    void dropzone_bookOpen_keepsBrowseShownEnabledAndAcceptingDrops() throws TimeoutException {
        openFactsBook();

        assertThat(isShown("import-browse")).isTrue();
        assertThat(button("import-browse").isDisable()).isFalse();
        assertThat(button("import-browse").getText()).isEqualTo("Browse files...");
        assertThat(required("import-dropzone").getOnDragOver()).isNotNull();
        assertThat(required("import-dropzone").getOnDragDropped()).isNotNull();
    }

    // IF the compact box lingered after the book was forgotten, THEN the empty screen would have lost its drop target.
    @Test
    void dropzone_bookCancelled_returnsToTheFullBox() throws TimeoutException {
        openFactsBook();

        onFx(() -> viewModel().cancel());

        assertThat(isCompact()).isFalse();
        assertThat(isOneLine()).isFalse();
    }

    // IF a refused file left the box compact, THEN a person told to choose another file would find no box to use.
    @Test
    void dropzone_fileRefused_staysTheFullBox() throws TimeoutException {
        final Path source = dir.resolve("secret.epub");
        projects.on(
                source,
                Result.err(AppError.of(ErrorCode.validation, "This book is protected", "The book is encrypted.")));
        openImport();

        openBook(source);

        assertThat(isShown("import-verdict")).isTrue();
        assertThat(isCompact()).isFalse();
    }

    // IF a dropped folder were handed to the reader, THEN the person would wait for a refusal that does not say why.
    @Test
    void openDropped_aFolder_saysSoAtOnceAndOpensNothing() throws IOException {
        final Path folder = Files.createDirectory(dir.resolve("Books"));
        openImport();

        onFx(() -> injector.getInstance(ImportController.class).openDropped(List.of(folder)));

        assertThat(projects.imports()).isEmpty();
        assertThat(state()).isEqualTo(new ImportState.Idle());
        assertThat(ThemeTestSupport.onFx(() -> scene.getRoot().lookupAll(".toast-text").stream()
                        .map(text -> ((Label) text).getText())
                        .toList()))
                .contains("Drop one book file: EPUB, FB2, Markdown or TXT.");
    }
}
