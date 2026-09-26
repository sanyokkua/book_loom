package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.testfx.framework.junit5.ApplicationTest;
import ua.bookloom.ui.theme.ThemeController;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * A shown window whose scene root is the real {@link AppShellView}, built by the real {@link UiModule} graph with the
 * theme attached the way the composition root attaches it, light block in force and nothing navigated to yet.
 *
 * <p>Shared by the shell, About and conformance tests so that "the shell as the application builds it" has one
 * definition. Every field is assigned on the FX Application Thread in {@link #start(Stage)}, which ApplicationTest
 * runs before each test, so no test shares a shell with another.
 */
@SuppressWarnings("NullAway.Init")
public abstract class ShellTestBase extends ApplicationTest {

    /**
     * The smallest content area the outer {@code AppShellView.MIN_WIDTH} by {@code MIN_HEIGHT} window minimum leaves a
     * person: Windows borders take 16 pixels of the width and a title bar 40 of the height (macOS: 28 of the height),
     * so tests that prove a screen fits at the minimum size their scene to this, never to the outer 960 by 640.
     */
    protected static final double CONTENT_AT_MINIMUM_WIDTH = 944;

    /** The height counterpart of {@link #CONTENT_AT_MINIMUM_WIDTH}; the smaller (Windows) figure. */
    protected static final double CONTENT_AT_MINIMUM_HEIGHT = 600;

    private static final double SCENE_WIDTH = 1024;
    private static final double SCENE_HEIGHT = 700;

    protected Injector injector;
    protected AppShellView shell;
    protected Navigator navigator;
    protected ThemeController themeController;
    protected Scene scene;

    @Override
    public void start(final Stage stage) {
        injector = createInjector(Locale.ENGLISH);
        shell = injector.getInstance(AppShellView.class);
        navigator = injector.getInstance(Navigator.class);
        themeController = injector.getInstance(ThemeController.class);
        themeController.setMode(ThemeMode.LIGHT);
        scene = new Scene(shell.root(), SCENE_WIDTH, SCENE_HEIGHT);
        themeController.attach(scene);
        stage.setScene(scene);
        stage.show();
        // The framework shares one stage between tests, so a test that resized it must not hand its size on.
        stage.sizeToScene();
    }

    /** The graph the shell is built from; a test that needs scripted collaborators overrides this. */
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.create(locale);
    }

    /**
     * Replaces the shell under test with one built by a fresh graph in another display language, in the same scene,
     * so a test can ask what a surface says in that language without a second window.
     */
    protected void useLocale(final Locale locale) {
        interact(() -> {
            injector = createInjector(locale);
            shell = injector.getInstance(AppShellView.class);
            navigator = injector.getInstance(Navigator.class);
            themeController = injector.getInstance(ThemeController.class);
            themeController.setMode(ThemeMode.LIGHT);
            scene.setRoot(shell.root());
            themeController.attach(scene);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        });
    }

    /**
     * Resizes the shown window so its scene is exactly {@code width} by {@code height}, whatever the platform's
     * window decoration adds, and lays the shell out at that size.
     */
    protected void resizeScene(final double width, final double height) {
        interact(() -> {
            final Window window = scene.getWindow();
            window.setWidth(width + window.getWidth() - scene.getWidth());
            window.setHeight(height + window.getHeight() - scene.getHeight());
        });
        onFx(() -> {});
        assertThat(scene.getWidth()).as("scene width after the resize").isEqualTo(width);
        assertThat(scene.getHeight()).as("scene height after the resize").isEqualTo(height);
    }

    /** Looks a node up by the id the shell publishes, failing the test with that id if it is not in the scene. */
    protected Node required(final String id) {
        final Node found = scene.getRoot().lookup("#" + id);
        assertThat(found).as("the shell must contain a node with id #%s", id).isNotNull();
        return found;
    }

    protected Button navButton(final String id) {
        return (Button) required(id);
    }

    protected Label breadcrumb() {
        return (Label) required("shell-breadcrumb");
    }

    /** The ids of every navigation entry carrying the current mark, in navigation order. */
    protected List<String> currentEntryIds() {
        return scene.getRoot().lookupAll(".nav-item-current").stream()
                .map(Node::getId)
                .toList();
    }

    /** Every piece of text shown at or below a node, in tree order (a labelled control's text may appear twice). */
    protected static List<String> textsUnder(final Node root) {
        final List<String> texts = new ArrayList<>();
        collectTexts(root, texts);
        return texts;
    }

    private static void collectTexts(final Node node, final List<String> texts) {
        if (node instanceof Labeled labeled && labeled.getText() != null) {
            texts.add(labeled.getText());
        }
        if (node instanceof TextInputControl input && input.getText() != null) {
            texts.add(input.getText());
        }
        if (node instanceof Text text) {
            texts.add(text.getText());
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectTexts(child, texts));
        }
    }

    /** Runs a shell interaction on the FX Application Thread and waits until the resulting pulse has been laid out. */
    protected void onFx(final Runnable action) {
        interact(() -> {
            action.run();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        });
    }
}
