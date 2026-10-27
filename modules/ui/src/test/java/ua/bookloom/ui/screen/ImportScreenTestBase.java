package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import javafx.scene.text.Text;
import org.jspecify.annotations.Nullable;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.RecordingErrorPresenter;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.dialog.RecordingOpenAnotherBookPrompt;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * What the import screen tests share: the scripted project service wired into the graph, the view model the screen
 * observes, and lookups that tell an absent node from one that is present but hidden.
 */
abstract class ImportScreenTestBase extends ShellTestBase {

    static final long WAIT_SECONDS = 10;

    /** The rows a card may ever carry; any other {@code import-row-*} node would be a figure the parse does not have. */
    static final List<String> CARD_ROWS = List.of("file", "format", "titleAuthor", "declaredLang", "chapters", "drm");

    final ScriptedProjectService projects = new ScriptedProjectService();
    final RecordingOpenAnotherBookPrompt prompt = new RecordingOpenAnotherBookPrompt();
    final RecordingErrorPresenter errors = new RecordingErrorPresenter();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .prompt(prompt)
                .errorPresenter(errors)
                .build();
    }

    void openImport() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
    }

    ImportViewModel viewModel() {
        return injector.getInstance(ImportViewModel.class);
    }

    ImportState state() {
        return ThemeTestSupport.onFx(() -> viewModel().state().get());
    }

    /** Opens {@code source} through the view model and waits until the answer has been published to the screen. */
    void openBook(final Path source) throws TimeoutException {
        onFx(() -> viewModel().open(source));
        awaitFx(() -> !viewModel().opening().get());
    }

    /** Chooses Ukrainian as the target of the open book's brief, the one input a start needs that an import leaves empty. */
    void chooseTarget() {
        onFx(() -> injector.getInstance(BookBriefViewModel.class).setTargetLanguage("uk"));
    }

    /** Waits, polling on the FX thread, until {@code condition} holds. */
    static void awaitFx(final BooleanSupplier condition) throws TimeoutException {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> ThemeTestSupport.onFx(condition::getAsBoolean));
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** The node with this id, or {@code null} when the screen holds none. */
    @Nullable
    Node optional(final String id) {
        return scene.getRoot().lookup("#" + id);
    }

    /** Whether the screen holds a node with this id that a person could see: present, visible and laid out. */
    boolean isShown(final String id) {
        final Node node = optional(id);
        return node != null && node.isVisible() && node.isManaged();
    }

    /**
     * Every text shown at or below the node with this id, joined with spaces. A labelled control's own text is read
     * once: the {@code Text} its skin draws it with is not counted again, so an exact comparison is possible.
     */
    String textOf(final String id) {
        final List<String> texts = new ArrayList<>();
        ThemeTestSupport.onFx(() -> {
            collectTexts(required(id), texts);
            return null;
        });
        return String.join(" ", texts);
    }

    private static void collectTexts(final Node node, final List<String> texts) {
        if (node instanceof Labeled labeled && !(node instanceof TitledPane)) {
            if (labeled.getText() != null && !labeled.getText().isEmpty()) {
                texts.add(labeled.getText());
            }
            return;
        }
        if (node instanceof Text text) {
            texts.add(text.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectTexts(child, texts));
        }
    }

    /** Opens the closed "About this book" fold, so the rows inside it are on show. */
    void openFold() {
        onFx(() -> ((TitledPane) required("import-about")).setExpanded(true));
    }

    Button button(final String id) {
        return (Button) required(id);
    }

    /** The ids of every node in the scene that starts with {@code prefix}. */
    List<String> idsStartingWith(final String prefix) {
        final List<String> ids = new ArrayList<>();
        collectIds(scene.getRoot(), prefix, ids);
        return ids;
    }

    private static void collectIds(final Node node, final String prefix, final List<String> ids) {
        final String id = node.getId();
        if (id != null && id.startsWith(prefix)) {
            ids.add(id);
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectIds(child, prefix, ids));
        }
    }

    /** Gives the window the whole headless screen's corner and the scene this size, for sizes beyond 1000 pixels. */
    void showAtSceneSize(final double width, final double height) {
        interact(() -> {
            scene.getWindow().setX(0);
            scene.getWindow().setY(0);
        });
        resizeScene(width, height);
        awaitLayout();
    }

    /** Lets the pulse that lays the resized scene out run. */
    void awaitLayout() {
        onFx(() -> {});
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Where the node with this id is in the scene. */
    Bounds boundsOf(final String id) {
        return ThemeTestSupport.onFx(() -> {
            final Node node = required(id);
            return node.localToScene(node.getBoundsInLocal());
        });
    }

    /** Whether the {@link TwoColumns} with this id laid its columns out stacked the last time. */
    boolean isStacked(final String id) {
        return ThemeTestSupport.onFx(() -> ((TwoColumns) required(id)).isStacked());
    }

    /** Whether the node with this id is a descendant of the node with the other id. */
    boolean isInside(final String id, final String ancestorId) {
        return ThemeTestSupport.onFx(() -> {
            final Node ancestor = required(ancestorId);
            for (Node walk = required(id); walk != null; walk = walk.getParent()) {
                if (walk.equals(ancestor)) {
                    return true;
                }
            }
            return false;
        });
    }
}
