package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import org.jspecify.annotations.Nullable;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ScriptedDocumentPort;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * What the import screen tests share: the scripted document port wired into the graph, the view model the screen
 * observes, and lookups that tell an absent node from one that is present but hidden.
 */
abstract class ImportScreenTestBase extends ShellTestBase {

    static final long WAIT_SECONDS = 10;

    /** The rows a card may ever carry; any other {@code import-row-*} node would be a figure the parse does not have. */
    static final List<String> CARD_ROWS =
            List.of("file", "format", "title", "author", "declaredLang", "units", "segments");

    final ScriptedDocumentPort port = ScriptedDocumentPort.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.create(locale, port);
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

    /** Every text shown at or below the node with this id, joined with spaces. */
    String textOf(final String id) {
        return String.join(" ", textsUnder(required(id)));
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
}
