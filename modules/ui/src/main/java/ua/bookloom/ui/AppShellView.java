package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.theme.ThemeBlock;
import ua.bookloom.ui.theme.ThemeController;

/**
 * The window's chrome: title bar, navigation column, breadcrumb, actions area, the host one screen at a time is shown
 * in, and the overlays for a modal dialog and for transient messages.
 *
 * <p>Built in code rather than FXML because the navigation entries are generated from {@link ViewNames}; screens
 * themselves stay FXML plus a controller. The shell never navigates on its own: it mirrors the {@link Navigator}'s
 * current view and content, so the mark and the breadcrumb follow a screen however the person arrived at it, and a
 * click in the column is only a request that the navigator may refuse. Every method must run on the JavaFX
 * Application Thread.
 */
@Slf4j
@Singleton
public final class AppShellView {

    /**
     * The smallest <em>outer</em> window width. The content area that leaves a person is smaller (borders and title
     * bar take part of it, about 944 by 600 at least), and every screen still fits its width there without sideways
     * scrolling.
     */
    public static final double MIN_WIDTH = 960;

    /**
     * The smallest <em>outer</em> window height; the content area it leaves is about 600 pixels on Windows and 612 on
     * macOS, and below it the title bar, toolbar and a screen's controls compete for room.
     */
    public static final double MIN_HEIGHT = 640;

    private static final int WORKFLOW_STEPS = Arrays.stream(ViewNames.values())
            .flatMapToInt(view -> view.step().stream())
            .max()
            .orElse(0);
    private static final String CRUMB_SEPARATOR = " / ";
    private static final String STEP_SEPARATOR = " · ";

    private final Navigator navigator;
    private final Messages messages;
    private final ThemeController themeController;
    private final String version;
    private final ModalHost modalHost;
    private final ToastStack toasts;
    private final NavColumn navColumn;
    private final Label breadcrumb = new Label();
    private final StackPane contentHost = new StackPane();
    private final ToggleButton themeToggle = new ToggleButton();
    private @Nullable StackPane root;

    /**
     * Creates the shell; nothing is built until {@link #root()} is first called.
     *
     * @param navigator the source of the current view and its content, and the only thing that changes them
     * @param messages the catalogue every label comes from
     * @param themeController what the theme control reads and drives, and what {@link #createScene} attaches
     * @param modalHost the overlay the About dialog is shown in; shared so other dialogs use the same one
     * @param toasts the transient-message surface whose host the shell places above everything else
     * @param version the build version the About dialog reports
     */
    @Inject
    AppShellView(
            final Navigator navigator,
            final Messages messages,
            final ThemeController themeController,
            final ModalHost modalHost,
            final ToastStack toasts,
            final @BuildVersion String version) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.themeController = Objects.requireNonNull(themeController, "themeController");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.version = Objects.requireNonNull(version, "version");
        this.navColumn = new NavColumn(messages, this::activate);
    }

    /**
     * The scene root, built on first use and the same node ever after.
     *
     * @return the overlay stack holding the frame, the modal host and the toast host
     */
    public StackPane root() {
        StackPane built = root;
        if (built == null) {
            log.debug("building the shell chrome");
            built = build();
            root = built;
        }
        return built;
    }

    /**
     * Builds the window's scene around the shell and attaches the theme to it, so the composition root needs no
     * theme type of its own.
     *
     * @param width the initial scene width in pixels
     * @param height the initial scene height in pixels
     * @return a scene whose root is {@link #root()}, themed for the mode the theme controller holds
     */
    public Scene createScene(final double width, final double height) {
        log.debug("creating the scene {}x{}", width, height);
        final Scene scene = new Scene(root(), width, height);
        themeController.attach(scene);
        return scene;
    }

    /**
     * Stops the window being shrunk below {@link #MIN_WIDTH} by {@link #MIN_HEIGHT}, the size at which the shell still
     * shows every part of itself.
     *
     * @param stage the window to limit
     */
    public void applyWindowLimits(final Stage stage) {
        Objects.requireNonNull(stage, "stage");
        log.debug("limiting the window to at least {}x{}", MIN_WIDTH, MIN_HEIGHT);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
    }

    /**
     * Asks the navigator to show a screen, as a click on its navigation entry does.
     *
     * @param target the entry activated; an inert entry is refused by the navigator and changes nothing
     */
    public void activate(final ViewNames target) {
        Objects.requireNonNull(target, "target");
        final ViewNames source = navigator.currentView().get();
        log.debug("activating {} from {}", target, source);
        final boolean accepted = navigator.navigate(target);
        log.debug("activation of {} {}", target, accepted ? "accepted" : "refused");
    }

    private StackPane build() {
        final BorderPane frame = new BorderPane();
        frame.setTop(titleBar());
        frame.setLeft(navColumn.view());
        frame.setCenter(main());
        // Top-left, not the StackPane default of centred: a frame that cannot shrink further would otherwise be pushed
        // above the top edge and left of the left edge equally, cutting off the title bar with no way to reach it.
        StackPane.setAlignment(frame, Pos.TOP_LEFT);
        final StackPane shell = new StackPane(frame, modalHost.view(), toasts.view());
        navigator.currentView().addListener((observed, old, current) -> showCurrent(current));
        navigator.content().addListener((observed, old, content) -> showContent(content));
        themeController.activeBlockProperty().addListener((observed, old, block) -> syncThemeToggle(block));
        showCurrent(navigator.currentView().get());
        showContent(navigator.content().get());
        syncThemeToggle(themeController.activeBlock());
        return shell;
    }

    private Node titleBar() {
        final Label product = new Label(messages.get(MessageKey.SHELL_TITLE));
        product.getStyleClass().add("shell-title");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        themeToggle.setId("shell-theme-toggle");
        themeToggle.getStyleClass().add("shell-title-button");
        themeToggle.setOnAction(event -> themeController.toggle());
        final Button about = new Button(messages.get(MessageKey.SHELL_ABOUT), new FontIcon(Feather.INFO));
        about.setId("shell-about");
        about.getStyleClass().add("shell-title-button");
        about.setOnAction(event -> openAbout());
        final HBox bar = new HBox(product, spacer, themeToggle, about);
        bar.setId("shell-title-bar");
        bar.getStyleClass().add("shell-title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node main() {
        breadcrumb.setId("shell-breadcrumb");
        breadcrumb.getStyleClass().add("shell-breadcrumb");
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox actions = new HBox();
        actions.setId("shell-actions");
        actions.getStyleClass().add("shell-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        final HBox toolbar = new HBox(breadcrumb, spacer, actions);
        toolbar.getStyleClass().add("shell-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        final ScrollPane scroll = contentScroll();
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return new VBox(toolbar, scroll);
    }

    private ScrollPane contentScroll() {
        contentHost.setId("shell-content");
        contentHost.getStyleClass().add("shell-content");
        contentHost.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(contentHost);
        scroll.setId("shell-content-scroll");
        scroll.getStyleClass().add("content-scroll");
        // Both dimensions fit, so a screen whose list grows still fills the viewport; it only scrolls once its own
        // minimum is taller or wider than the window.
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private void openAbout() {
        log.debug("opening the About dialog");
        modalHost.show(new AboutDialog(messages, version, modalHost::hide).card(), true);
    }

    private void showCurrent(final @Nullable ViewNames current) {
        log.debug("shell follows the current view to {}", current);
        navColumn.markCurrent(current);
        breadcrumb.setText(current == null ? "" : crumb(current));
    }

    private void showContent(final @Nullable Node content) {
        if (content == null) {
            contentHost.getChildren().clear();
        } else {
            contentHost.getChildren().setAll(content);
        }
    }

    private String crumb(final ViewNames view) {
        final String place = messages.get(view.group().heading()) + CRUMB_SEPARATOR + messages.get(view.messageKey());
        return view.step().isPresent()
                ? place
                        + STEP_SEPARATOR
                        + messages.get(
                                MessageKey.SHELL_BREADCRUMB_STEP, view.step().getAsInt(), WORKFLOW_STEPS)
                : place;
    }

    private void syncThemeToggle(final ThemeBlock block) {
        final boolean dark = block == ThemeBlock.DARK;
        themeToggle.setText(messages.get(dark ? MessageKey.SHELL_THEME_TO_LIGHT : MessageKey.SHELL_THEME_TO_DARK));
        themeToggle.setGraphic(new FontIcon(dark ? Feather.SUN : Feather.MOON));
        themeToggle.setSelected(dark);
    }
}
