package ua.bookloom.ui.screen;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.RecordingFileRevealer;
import ua.bookloom.ui.RecordingProjectActions;
import ua.bookloom.ui.ScriptedProjectService;
import ua.bookloom.ui.ScriptedTranslationEngine;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.ViewNames;

/**
 * What the Projects screen tests share: the scripted project service listing invented projects, recording fakes for the
 * row actions and the file manager, an engine that records any run started, and lookups by exact node id (a project id
 * may hold characters a CSS selector cannot).
 */
abstract class ProjectsScreenTestBase extends ShellTestBase {

    static final Path EARTH = ProjectFixtures.source(ProjectFixtures.BOOK_FILE);
    static final String EARTH_ID = ProjectFixtures.idOf(ProjectFixtures.BOOK_FILE);
    static final String TIDE_FILE = "tide-tables.epub";
    static final Path TIDE = ProjectFixtures.source(TIDE_FILE);
    static final String TIDE_ID = ProjectFixtures.idOf(TIDE_FILE);
    static final String TIDE_HASH = "fedcba9876543210fedcba9876543210";
    static final String GERMAN_ID = "p-de";
    private static final Instant YESTERDAY = Instant.parse("2026-10-21T10:00:00Z");
    private static final int DECIDED = 633;

    final ScriptedProjectService projects = new ScriptedProjectService();
    final RecordingProjectActions actions = new RecordingProjectActions();
    final RecordingFileRevealer revealer = new RecordingFileRevealer();
    final ScriptedTranslationEngine engine = ScriptedTranslationEngine.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .projectActions(actions)
                .revealer(revealer)
                .engine(engine)
                .gated()
                .build();
    }

    /** English to Ukrainian, a run under way, 633 of 1,240 segments decided, last opened yesterday. */
    static ProjectSummary ukrainianRunning() {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        EARTH_ID,
                        ProjectFixtures.BOOK_FILE,
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        NameOrigin.AUTO,
                        "Earth Gravity · EN → UK",
                        YESTERDAY),
                DECIDED,
                ProjectFixtures.run(EARTH_ID, JobState.PAUSED),
                null);
    }

    /** The same book into German under a name the person typed, never run. */
    static ProjectSummary germanNamed() {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        GERMAN_ID,
                        ProjectFixtures.BOOK_FILE,
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "de",
                        NameOrigin.USER,
                        "Gravity for my sister",
                        YESTERDAY),
                0,
                null,
                null);
    }

    /** Another book, English to Ukrainian, exported. */
    static ProjectSummary tideExported() {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        TIDE_ID, TIDE_FILE, TIDE_HASH, "en", "uk", NameOrigin.AUTO, "Tide Tables · EN → UK", YESTERDAY),
                ProjectFixtures.BOOK_SEGMENTS,
                ProjectFixtures.run(TIDE_ID, JobState.COMPLETED),
                ProjectFixtures.export(YESTERDAY));
    }

    /** Another book that was never run. */
    static ProjectSummary tideNotStarted() {
        return ProjectFixtures.summary(
                ProjectFixtures.project(
                        TIDE_ID, TIDE_FILE, TIDE_HASH, "en", "uk", NameOrigin.AUTO, "Tide Tables · EN → UK", YESTERDAY),
                0,
                null,
                null);
    }

    /** Lists these projects, shows Projects and waits until the first of them is on the screen. */
    void showListing(final ProjectSummary... summaries) throws TimeoutException {
        projects.willList(List.of(summaries));
        onFx(() -> shell.activate(ViewNames.PROJECTS));
        ImportScreenTestBase.awaitFx(
                () -> find("projects-row-" + summaries[0].project().id()) != null);
    }

    /** Lists nothing, shows Projects and waits for the first-launch card. */
    void showEmptyListing() throws TimeoutException {
        projects.willList(List.of());
        onFx(() -> shell.activate(ViewNames.PROJECTS));
        ImportScreenTestBase.awaitFx(() -> find("projects-empty-card") != null);
    }

    /** The node whose id is exactly {@code id}, or {@code null}. Call on the FX thread. */
    @Nullable
    Node find(final String id) {
        return findIn(scene.getRoot(), id);
    }

    private static @Nullable Node findIn(final Node node, final String id) {
        if (id.equals(node.getId())) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (final Node child : parent.getChildrenUnmodifiable()) {
                final Node found = findIn(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The node whose id is exactly {@code id}; fails the test when the screen holds none. */
    Node node(final String id) {
        final Node found = ThemeTestSupport.onFx(() -> find(id));
        org.assertj.core.api.Assertions.assertThat(found)
                .as("the screen must hold a node with id %s", id)
                .isNotNull();
        return found;
    }

    boolean isPresent(final String id) {
        return ThemeTestSupport.onFx(() -> find(id) != null);
    }

    /** Every text at or below the node with this id, joined with spaces. */
    String textOf(final String id) {
        return ThemeTestSupport.onFx(() -> String.join(" ", textsUnder(node(id))));
    }

    /** The first node of {@code type} at or below {@code root}, or {@code null}. */
    static <T extends Node> @Nullable T firstOf(final Node root, final Class<T> type) {
        final List<T> all = new ArrayList<>();
        collect(root, type, all);
        return all.isEmpty() ? null : all.getFirst();
    }

    private static <T extends Node> void collect(final Node node, final Class<T> type, final List<T> into) {
        if (type.isInstance(node)) {
            into.add(type.cast(node));
        }
        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> collect(child, type, into));
        }
    }

    /** The captions of the row's ⋯ menu, in order. */
    List<String> menuCaptions(final String projectId) {
        return ThemeTestSupport.onFx(() -> {
            final MenuButton more = firstOf(node("projects-row-" + projectId), MenuButton.class);
            org.assertj.core.api.Assertions.assertThat(more)
                    .as("the row's more menu")
                    .isNotNull();
            return more.getItems().stream().map(MenuItem::getText).toList();
        });
    }

    /** Chooses the menu item with this caption in the row's ⋯ menu, as a click on it would. */
    void chooseMenuItem(final String projectId, final String caption) {
        onFx(() -> {
            final MenuButton more = firstOf(node("projects-row-" + projectId), MenuButton.class);
            org.assertj.core.api.Assertions.assertThat(more)
                    .as("the row's more menu")
                    .isNotNull();
            more.getItems().stream()
                    .filter(item -> caption.equals(item.getText()))
                    .findFirst()
                    .orElseThrow()
                    .fire();
        });
    }

    /** The "＋ Translate into another language" button of the book card whose id ends in {@code hash8}. */
    Button translateIntoButton(final String hash8) {
        return ThemeTestSupport.onFx(() -> {
            final List<Button> buttons = new ArrayList<>();
            collect(node("projects-book-" + hash8), Button.class, buttons);
            return buttons.stream()
                    .filter(button -> button.getText().contains("Translate into another language"))
                    .findFirst()
                    .orElseThrow();
        });
    }

    Button continueButton(final String projectId) {
        return (Button) node("projects-continue-" + projectId);
    }

    void press(final Button button) {
        onFx(button::fire);
    }
}
