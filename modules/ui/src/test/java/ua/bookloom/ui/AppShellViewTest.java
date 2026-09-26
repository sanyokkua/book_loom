package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * The real shell chrome on the headless toolkit: its regions, the navigation generated from {@link ViewNames}, the
 * inert entries, the breadcrumb, the empty actions area and the theme control. Expected texts and ids are written
 * out by hand from the specification and the message catalogue, never derived from the code under test.
 */
class AppShellViewTest extends ShellTestBase {

    private static final String NAV_ITEM_CLASS = "nav-item";
    private static final String NAV_LABEL_CLASS = "nav-label";
    private static final String HEADING_PREFIX = "heading:";

    /** Walks the navigation column in reading order, noting each group heading and each entry by id. */
    private static void collectSequence(final Node node, final List<String> sequence) {
        if (node.getStyleClass().contains(NAV_LABEL_CLASS) && node instanceof Label heading) {
            sequence.add(HEADING_PREFIX + heading.getText());
            return;
        }
        if (node.getStyleClass().contains(NAV_ITEM_CLASS)) {
            sequence.add(node.getId());
            return;
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collectSequence(child, sequence));
        }
    }

    private List<String> navigationSequence() {
        final List<String> sequence = new ArrayList<>();
        collectSequence(required("shell-nav"), sequence);
        return sequence;
    }

    private StackPane contentHost() {
        return (StackPane) required("shell-content");
    }

    private Bounds sceneBounds(final String id) {
        final Node node = required(id);
        return node.localToScene(node.getLayoutBounds());
    }

    // IF a region were missing from the shell, THEN a screen would have nowhere to land or a dialog nowhere to show.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "shell-title-bar",
                "shell-nav",
                "shell-breadcrumb",
                "shell-actions",
                "shell-content",
                "shell-modal-host",
                "shell-scrim",
                "shell-toast-host",
                "shell-theme-toggle",
                "shell-about"
            })
    void root_freshShell_containsEveryRegionAsADescendant(final String id) {
        assertThat(required(id).getScene()).isSameAs(scene);
    }

    @Test
    void root_calledRepeatedly_returnsTheSceneRootEveryTime() {
        // IF root() rebuilt the chrome on each call, THEN the window would show a different tree from the one the
        // listeners and the navigator are wired to.
        assertThat(shell.root()).isSameAs(scene.getRoot());
        assertThat(shell.root()).isSameAs(shell.root());
    }

    @Test
    void createScene_onAFreshShell_wrapsTheRootAndAttachesTheThemeStylesheetOnce() {
        // IF the composition root had to attach the theme itself, THEN it would need a theme type the module hides.
        final AppShellView fresh = UiTestInjector.create(Locale.ENGLISH).getInstance(AppShellView.class);

        final Scene created = fresh.createScene(800, 600);

        assertThat(created.getRoot()).isSameAs(fresh.root());
        assertThat(created.getStylesheets()).containsExactly(Theme.stylesheet());
    }

    @Test
    void root_titleBar_carriesTheProductNameTheThemeControlAndTheAboutAction() {
        // IF the title bar lacked one of its three parts, THEN the window would not open as the spec describes.
        final Node titleBar = required("shell-title-bar");

        assertThat(textsUnder(titleBar)).contains("BookLoom");
        assertThat(titleBar.lookup("#shell-theme-toggle")).isInstanceOf(ToggleButton.class);
        assertThat(titleBar.lookup("#shell-about")).isInstanceOf(Button.class);
        assertThat(((Button) titleBar.lookup("#shell-about")).getText()).isEqualTo("About");
    }

    @Test
    void root_toolbar_holdsTheBreadcrumbAndTheActionsArea() {
        // IF the breadcrumb or the actions area sat outside the toolbar row, THEN the reference layout would drift.
        final Node toolbar = scene.getRoot().lookup(".shell-toolbar");

        assertThat(toolbar).isNotNull();
        assertThat(toolbar.lookup("#shell-breadcrumb")).isInstanceOf(Label.class);
        assertThat(toolbar.lookup("#shell-actions")).isInstanceOf(HBox.class);
    }

    @Test
    void root_hosts_areTheRightKindOfNodeAndOnlyTheScrimStartsHidden() {
        // IF the scrim were visible with no modal shown, THEN the whole window would be dimmed and unclickable.
        assertThat(required("shell-content")).isInstanceOf(StackPane.class);
        assertThat(required("shell-scrim").isVisible()).isFalse();
        assertThat(required("shell-toast-host").isVisible()).isTrue();
    }

    @Test
    void navigation_isGroupedInTwoNamedGroupsInReadingOrder() {
        // IF a group were missing, mis-ordered or mis-filled, THEN the column would not read as the spec's two
        // groups.
        assertThat(navigationSequence())
                .containsExactly(
                        "heading:Workflow",
                        "nav-projects",
                        "nav-import",
                        "nav-book-brief",
                        "nav-structure",
                        "nav-names-style",
                        "nav-translating",
                        "nav-review",
                        "nav-export",
                        "heading:Application",
                        "nav-settings");
    }

    @Test
    void navigation_hasNoDesignReferenceGroup() {
        // IF the mockup's specimen sheets shipped as navigation entries, THEN a person would see three entries that
        // lead nowhere and belong to no product screen.
        final List<String> headings = navigationSequence().stream()
                .filter(entry -> entry.startsWith(HEADING_PREFIX))
                .toList();

        assertThat(headings).containsExactly("heading:Workflow", "heading:Application");
        assertThat(textsUnder(required("shell-nav")))
                .doesNotContain("Design reference", "Component library", "Dialogs & alerts", "Notifications");
    }

    @Test
    void navigation_entryCount_equalsTheNumberOfViewNamesConstants() {
        // IF the column were a second hand-kept list, THEN a screen added to ViewNames would not appear in it.
        assertThat(scene.getRoot().lookupAll("." + NAV_ITEM_CLASS)).hasSize(ViewNames.values().length);
    }

    @Test
    void navigation_workflowEntries_areNumberedOneToSevenInOrder() {
        // IF the badges were mis-numbered or mis-ordered, THEN the sequence would not read one to seven.
        assertThat(required("shell-nav").lookupAll(".nav-step"))
                .extracting(step -> ((Label) step).getText())
                .containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    // IF an entry outside the numbered workflow carried a number, THEN projects or the application group would
    // read as a workflow step.
    @ParameterizedTest
    @ValueSource(strings = {"nav-projects", "nav-settings"})
    void navigation_unnumberedEntry_hasNoStepBadge(final String id) {
        assertThat(required(id).lookup(".nav-step")).isNull();
    }

    // IF a numbered entry carried the wrong number, THEN step 4 (names and style) would not line up with the design.
    @ParameterizedTest
    @CsvSource({
        "nav-import, 1",
        "nav-book-brief, 2",
        "nav-structure, 3",
        "nav-names-style, 4",
        "nav-translating, 5",
        "nav-review, 6",
        "nav-export, 7"
    })
    void navigation_numberedEntry_showsItsOwnStepNumber(final String id, final String number) {
        assertThat(((Label) required(id).lookup(".nav-step")).getText()).isEqualTo(number);
    }

    // IF an entry's unavailable mark disagreed with ViewNames.isAvailable, THEN a working screen would look
    // greyed out or an inert one would look usable.
    @ParameterizedTest
    @CsvSource({
        "nav-projects, true",
        "nav-import, false",
        "nav-book-brief, false",
        "nav-structure, false",
        "nav-names-style, true",
        "nav-translating, false",
        "nav-review, true",
        "nav-export, false",
        "nav-settings, false"
    })
    void navigation_entry_isMarkedUnavailableExactlyWhenItIsInert(final String id, final boolean unavailable) {
        assertThat(required(id).getStyleClass().contains("nav-item-unavailable"))
                .isEqualTo(unavailable);
    }

    @Test
    void navigation_beforeTheFirstNavigation_marksNoEntryCurrent() {
        // IF an entry were marked before anyone navigated, THEN the mark would disagree with an empty screen area.
        assertThat(currentEntryIds()).isEmpty();
    }

    // IF activating an inert entry (through the shell or by pressing its button) moved the screen or the mark, THEN a
    // screen that does not exist would be shown or claimed as current.
    @ParameterizedTest
    @CsvSource({"PROJECTS, nav-projects", "NAMES_STYLE, nav-names-style", "REVIEW, nav-review"})
    void activate_inertEntry_changesNeitherTheScreenAreaNorTheCurrentMark(final ViewNames inert, final String id) {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        final List<Node> contentBefore = List.copyOf(contentHost().getChildren());
        final String breadcrumbBefore = breadcrumb().getText();

        onFx(() -> shell.activate(inert));
        onFx(() -> navButton(id).fire());

        assertThat(contentHost().getChildren()).containsExactlyElementsOf(contentBefore);
        assertThat(currentEntryIds()).containsExactly("nav-import");
        assertThat(breadcrumb().getText()).isEqualTo(breadcrumbBefore);
        assertThat(navigator.currentView().get()).isEqualTo(ViewNames.IMPORT);
        assertThat(navButton(id).isDisabled())
                .as("an inert entry is greyed, not disabled, so the click reaches the shell and is refused there")
                .isFalse();
    }

    // IF activating an available entry did not mark exactly that entry and write the breadcrumb, THEN the shell would
    // disagree with the screen shown.
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            IMPORT      | nav-import      | Workflow / Import book · step 1 of 7
            BOOK_BRIEF  | nav-book-brief  | Workflow / Book Brief · step 2 of 7
            STRUCTURE   | nav-structure   | Workflow / Structure · step 3 of 7
            TRANSLATING | nav-translating | Workflow / Translating · step 5 of 7
            EXPORT      | nav-export      | Workflow / Export · step 7 of 7
            SETTINGS    | nav-settings    | Application / Settings
            """)
    void activate_availableEntry_marksOnlyThatEntryAndWritesTheBreadcrumb(
            final ViewNames view, final String id, final String crumb) {
        onFx(() -> shell.activate(view));

        assertThat(currentEntryIds()).containsExactly(id);
        assertThat(breadcrumb().getText()).isEqualTo(crumb);
        assertThat(navigator.currentView().get()).isEqualTo(view);
    }

    @Test
    void activate_availableEntry_showsTheNavigatorsContentInTheContentHost() {
        // IF the host did not follow Navigator.content(), THEN the mark would move but the screen would not.
        onFx(() -> shell.activate(ViewNames.IMPORT));
        final Parent first = navigator.content().get();
        assertThat(contentHost().getChildren()).contains(first);

        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));

        assertThat(contentHost().getChildren())
                .contains(navigator.content().get())
                .doesNotContain(first);
    }

    @Test
    void currentMark_movesToTheNewEntryAndLeavesTheOldOne() {
        // IF the mark accumulated, THEN two entries would be current at once.
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.EXPORT));

        assertThat(currentEntryIds()).containsExactly("nav-export");
        assertThat(navButton("nav-import").getStyleClass()).doesNotContain("nav-item-current");
    }

    // IF the mark and breadcrumb followed only clicks in the column, THEN a Continue-driven jump would leave the
    // navigation lying about what is on screen.
    @Test
    void currentMark_followsAProgrammaticNavigationNotDrivenByTheColumn() {
        onFx(() -> navigator.navigate(ViewNames.STRUCTURE));
        assertThat(currentEntryIds()).containsExactly("nav-structure");

        onFx(() -> navigator.navigate(ViewNames.TRANSLATING));

        assertThat(currentEntryIds()).containsExactly("nav-translating");
        assertThat(breadcrumb().getText()).isEqualTo("Workflow / Translating · step 5 of 7");
    }

    @Test
    void actionsArea_onAScreenWithoutShellActions_isPresentEmptyAndDoesNotCollapse() {
        // IF the area were absent, non-empty or resized by navigation, THEN it would move the screen area with it.
        onFx(() -> {});
        final Bounds actionsBefore = sceneBounds("shell-actions");
        onFx(() -> shell.activate(ViewNames.IMPORT));
        final Bounds contentAfterFirst = sceneBounds("shell-content");

        onFx(() -> shell.activate(ViewNames.STRUCTURE));

        final HBox actions = (HBox) required("shell-actions");
        assertThat(actions.getChildren()).isEmpty();
        assertThat(actions.isVisible()).isTrue();
        assertThat(actions.isManaged()).isTrue();
        assertThat(sceneBounds("shell-actions").getWidth()).isEqualTo(actionsBefore.getWidth());
        assertThat(sceneBounds("shell-actions").getHeight()).isEqualTo(actionsBefore.getHeight());
        assertThat(sceneBounds("shell-content")).isEqualTo(contentAfterFirst);
    }

    @Test
    void themeToggle_inTheLightBlock_readsDarkAndSwitchesTheRootToTheDarkBlock() {
        // IF the toggle did not flip the root's dark class and its own label, THEN the person could not switch theme.
        final ToggleButton toggle = (ToggleButton) required("shell-theme-toggle");
        assertThat(toggle.getText()).isEqualTo("Dark");
        assertThat(scene.getRoot().getStyleClass()).doesNotContain("theme-dark");

        onFx(toggle::fire);

        assertThat(scene.getRoot().getStyleClass()).contains("theme-dark");
        assertThat(toggle.getText()).isEqualTo("Light");
    }

    @Test
    void themeToggle_inTheDarkBlock_readsLightAndSwitchesTheRootBackToLight() {
        // IF the toggle were one-way, THEN a person who chose dark could never return to light.
        final ToggleButton toggle = (ToggleButton) required("shell-theme-toggle");
        onFx(() -> themeController.setMode(ThemeMode.DARK));
        assertThat(toggle.getText()).isEqualTo("Light");

        onFx(toggle::fire);

        assertThat(scene.getRoot().getStyleClass()).doesNotContain("theme-dark");
        assertThat(toggle.getText()).isEqualTo("Dark");
    }
}
