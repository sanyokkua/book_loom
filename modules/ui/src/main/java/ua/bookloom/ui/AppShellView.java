package ua.bookloom.ui;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.control.ActivityChip;
import ua.bookloom.ui.control.BannerSlot;
import ua.bookloom.ui.control.DisabledTips;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.control.Overrun;
import ua.bookloom.ui.control.RunBanner;
import ua.bookloom.ui.control.RunStatusBar;
import ua.bookloom.ui.control.ScrollAnchor;
import ua.bookloom.ui.control.ScrollHost;
import ua.bookloom.ui.control.SmoothScroll;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ToastStack;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.DiagnosticActions;
import ua.bookloom.ui.state.LocalModelProbe;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.ShellBanners;
import ua.bookloom.ui.state.StepLocks;
import ua.bookloom.ui.state.WorkflowProgress;
import ua.bookloom.ui.theme.MonoFont;
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

    static final String REDUCED_MOTION_CLASS = "reduce-motion";
    // Style classes a screen carries: its own minimum height instead of the height hold, and the content cap.
    static final String FILLS_CLASS = "fills-viewport";
    static final String CAPPED_CLASS = "content-capped";

    /** The smallest <em>outer</em> window width; every screen fits the content area it leaves (about 944 wide). */
    public static final double MIN_WIDTH = 960;
    /** The smallest <em>outer</em> window height; it leaves a content area of about 600 pixels on Windows. */
    public static final double MIN_HEIGHT = 640;

    private static final int WORKFLOW_STEPS = Arrays.stream(ViewNames.values())
            .flatMapToInt(view -> view.step().stream())
            .max()
            .orElse(0);
    private static final double ACTION_SPACING = 10;
    private static final double TOAST_INSET = 16;
    private static final String CRUMB_SEPARATOR = " / ";
    private static final String STEP_SEPARATOR = " · ";

    private final Navigator navigator;
    private final Messages messages;
    private final ThemeController themeController;
    private final String version;
    private final ModalHost modalHost;
    private final BusyHost busyHost;
    private final ActivityTracker activities;
    private final ToastStack toasts;
    private final RunStatusBar runStatus;
    private final ActivityChip activityChip;
    private final DiagnosticActions diagnostics;
    private final ShellBanners banners;
    private final NavColumn navColumn;
    private final Label breadcrumb = new Label();
    private final StackPane contentHost = new ContentHost();
    private final ScrollPane contentScroll = new ScrollHost(contentHost);
    private final ScrollAnchor contentAnchor = ScrollAnchor.install(contentScroll);
    private final ToggleButton themeToggle = new ToggleButton();
    private @Nullable StackPane root;

    /**
     * Creates the shell; nothing is built until {@link #root()} is first called.
     *
     * @param navigator the source of the current view and its content, and the only thing that changes them
     * @param messages the catalogue every label comes from
     * @param themeController what the theme control reads and drives, and what {@link #createScene} attaches
     * @param modalHost the overlay the About dialog is shown in; shared so other dialogs use the same one
     * @param busyHost the layer between the frame and the dialogs that shows the work the window waits for
     * @param activities the running work, whose blocking state disables the navigation and the toolbar actions
     * @param toasts the transient-message surface whose host the shell places over the content area
     * @param runStatus the run's status and control, placed between the product name and the theme control
     * @param activityChip the other model work under way, with its Stop, placed before the run's status
     * @param progress the completed steps the navigation marks
     * @param runs the run-state row and the chosen AI model the navigation footer names
     * @param locks which steps are closed, for the navigation to draw
     * @param diagnostics the log settings and actions the About dialog offers
     * @param banners the run's banners, pinned between the toolbar and the content
     * @param runBanner the run's own banner, built with the shell; it pins itself in {@code banners}
     * @param version the build version the About dialog reports
     * @param probe the look at the local AI programs, whose open model question the navigation footer waits for
     */
    @Inject
    AppShellView(
            final Navigator navigator,
            final Messages messages,
            final ThemeController themeController,
            final ModalHost modalHost,
            final BusyHost busyHost,
            final ActivityTracker activities,
            final ToastStack toasts,
            final RunStatusBar runStatus,
            final ActivityChip activityChip,
            final WorkflowProgress progress,
            final RunStateSource runs,
            final StepLocks locks,
            final DiagnosticActions diagnostics,
            final ShellBanners banners,
            final RunBanner runBanner,
            final @BuildVersion String version,
            final LocalModelProbe probe) {
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.themeController = Objects.requireNonNull(themeController, "themeController");
        this.modalHost = Objects.requireNonNull(modalHost, "modalHost");
        this.busyHost = Objects.requireNonNull(busyHost, "busyHost");
        this.activities = Objects.requireNonNull(activities, "activities");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.runStatus = Objects.requireNonNull(runStatus, "runStatus");
        this.activityChip = Objects.requireNonNull(activityChip, "activityChip");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.banners = Objects.requireNonNull(banners, "banners");
        Objects.requireNonNull(runBanner, "runBanner");
        this.version = Objects.requireNonNull(version, "version");
        this.navColumn = new NavColumn(messages, this::activate, progress, runs, locks, probe);
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
     * Builds the window's scene around the shell, attaches the theme and shows disabled controls' tooltips
     * ({@link DisabledTips}), so the composition root needs no theme type of its own.
     *
     * @param width the initial scene width in pixels
     * @param height the initial scene height in pixels
     * @return a scene whose root is {@link #root()}, themed for the mode the theme controller holds
     */
    public Scene createScene(final double width, final double height) {
        log.debug("creating the scene {}x{}", width, height);
        final Scene scene = new Scene(root(), width, height);
        themeController.attach(scene);
        DisabledTips.install(scene);
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
        // Short work the window waits for leaves nothing to navigate to; its progress dialog says why, and Navigator
        // refuses too. Long work does not lock: leaving its screen asks whether to let it run on in the background.
        navColumn.view().disableProperty().bind(activities.locking());
        frame.setCenter(main());
        // Top-left, not the StackPane default of centred: a frame that cannot shrink further would otherwise be pushed
        // above the top edge and left of the left edge equally, cutting off the title bar with no way to reach it.
        StackPane.setAlignment(frame, Pos.TOP_LEFT);
        final Node toastLayer = toasts.view();
        // Above the busy scrim, so an error raised while the window waits is seen; below the dialogs.
        final StackPane shell = new StackPane(frame, busyHost.view(), toastLayer, modalHost.view());
        LayoutClass.install(shell); // the window's width class: the navigation column and the title bar follow it (L9)
        keepOverTheContent(toastLayer);
        MonoFont.pick(Font.getFamilies()).ifPresent(face -> {
            log.debug("the activity log is set in {}", face.family());
            shell.getStyleClass().add(face.styleClass());
        });
        if (Motion.isReduced()) {
            // The switches' sliding thumb is the one transition the stylesheet plays; this class stills it.
            log.info("reduced motion: transitions are off");
            shell.getStyleClass().add(REDUCED_MOTION_CLASS);
        }
        // Once at the top: a wheel notch glides whichever pane, list, table or tree is under the pointer.
        SmoothScroll.install(shell);
        navigator.currentView().addListener((observed, old, current) -> showCurrent(current));
        navigator.content().addListener((observed, old, content) -> showContent(content));
        themeController.activeBlockProperty().addListener((observed, old, block) -> syncThemeToggle(block));
        showCurrent(navigator.currentView().get());
        showContent(navigator.content().get());
        syncThemeToggle(themeController.activeBlock());
        return shell;
    }

    // The toasts rise from the content area's bottom-right corner, 16 pixels in, so none covers the navigation column.
    private void keepOverTheContent(final Node toastLayer) {
        final Node nav = navColumn.view();
        StackPane.setMargin(toastLayer, corner(nav.getLayoutBounds().getWidth()));
        nav.layoutBoundsProperty()
                .addListener((observed, was, now) -> StackPane.setMargin(toastLayer, corner(now.getWidth())));
    }

    private static Insets corner(final double navigationWidth) {
        return new Insets(0, TOAST_INSET, TOAST_INSET, navigationWidth);
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
        Tips.install(messages, about, MessageKey.SHELL_ABOUT_TIP);
        about.getStyleClass().add("shell-title-button");
        about.setOnAction(event -> openAbout());
        // Only the run status gives way in a narrow window (it shortens the book name and hides its lesser parts);
        // the product name, the theme toggle and About keep their words whole.
        for (final Region whole : new Region[] {product, themeToggle, about}) {
            whole.setMinWidth(Region.USE_PREF_SIZE);
        }
        final HBox bar = new HBox(product, spacer, activityChip.view(), runStatus.view(), themeToggle, about);
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
        actions.setSpacing(ACTION_SPACING);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.disableProperty().bind(activities.locking());
        breadcrumb.setMinWidth(0);
        breadcrumb.setTextOverrun(OverrunStyle.ELLIPSIS);
        Overrun.install(breadcrumb);
        final HBox toolbar = new HBox(breadcrumb, spacer, actions);
        toolbar.getStyleClass().add("shell-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        final ScrollPane scroll = configuredContentScroll();
        final StackPane contentArea = new StackPane(scroll);
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        return new VBox(toolbar, new BannerSlot(banners), contentArea);
    }

    private ScrollPane configuredContentScroll() {
        contentHost.setId("shell-content");
        contentHost.getStyleClass().add("shell-content");
        contentHost.setAlignment(Pos.TOP_LEFT);
        LayoutClass.install(contentHost);
        final ScrollPane scroll = contentScroll;
        scroll.setId("shell-content-scroll");
        scroll.getStyleClass().add("content-scroll");
        // Both fit: a growing list fills the viewport; it scrolls once the screen's minimum (showContent) is taller.
        scroll.setFitToHeight(true);
        // A screen narrower than its minimum width is scrolled across rather than cut.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private void openAbout() {
        log.debug("opening the About dialog");
        modalHost.show(new AboutDialog(messages, version, diagnostics, modalHost::hide).card(), true);
    }

    private void showCurrent(final @Nullable ViewNames current) {
        log.debug("shell follows the current view to {}", current);
        navColumn.markCurrent(current);
        breadcrumb.setText(current == null ? "" : crumb(current));
    }

    // A screen is held at its preferred height (fitting the height would crush its lists to their minimum); one with
    // `fills-viewport` declares its own minimum, and one that scrolls itself fits the window. A capped one is centred.
    private void showContent(final @Nullable Node content) {
        if (content == null) {
            contentHost.getChildren().clear();
        } else {
            if (content instanceof Region region && !(content instanceof ScrollPane)) {
                holdHeight(region);
            }
            StackPane.setAlignment(
                    content, content.getStyleClass().contains(CAPPED_CLASS) ? Pos.TOP_CENTER : Pos.TOP_LEFT);
            contentHost.getChildren().setAll(content);
            Motion.fadeIn(content, Motion.QUICK);
        }
        contentAnchor.reset();
    }

    private static void holdHeight(final Region region) {
        final boolean fills = region.getStyleClass().contains(FILLS_CLASS);
        log.debug("screen {} {}", region.getId(), fills ? "declares its own minimum" : "is held at its height");
        if (!fills) {
            region.setMinHeight(Region.USE_PREF_SIZE);
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
        Tips.install(
                messages, themeToggle, dark ? MessageKey.SHELL_THEME_TO_LIGHT_TIP : MessageKey.SHELL_THEME_TO_DARK_TIP);
        themeToggle.setGraphic(new FontIcon(dark ? Feather.SUN : Feather.MOON));
        themeToggle.setSelected(dark);
    }

    /**
     * The padded host the shell scrolls. JavaFX's {@code StackPane.computeMinHeight(width)} hands its children the
     * full width instead of the width left inside the padding, so for a screen whose text wraps it answers a height for
     * a wider page than the one it fills; the scroll pane takes that answer as the floor of the content height, and
     * the screen was laid out shorter than it needs (the last rows crushed) whenever a wrap line fell in between.
     */
    private static final class ContentHost extends StackPane {
        @Override
        protected double computeMinHeight(final double width) {
            final Insets padding = getInsets();
            return super.computeMinHeight(width == -1 ? -1 : width - padding.getLeft() - padding.getRight());
        }
    }
}
