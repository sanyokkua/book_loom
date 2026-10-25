package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * What the Projects screen shows: the stored projects grouped by book, the search and filter over them, and the
 * actions its controls reach. Listing hashes book files and reads segments, so it runs off the FX thread and the
 * answer is published back on it; the properties are touched on the FX Application Thread only.
 */
@Slf4j
@Singleton
public final class ProjectsViewModel {

    private final ProjectService projects;
    private final ProviderConfigs providers;
    private final CurrentProject current;
    private final Messages messages;
    private final ProjectOpener opener;
    private final ProjectActions actions;
    private final FileRevealer revealer;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<ProjectsState> state = new ReadOnlyObjectWrapper<>(ProjectsState.LOADING);
    private final ReadOnlyObjectWrapper<List<BookGroup>> groups = new ReadOnlyObjectWrapper<>(List.of());
    private final StringProperty search = new SimpleStringProperty("");
    private final ObjectProperty<ProjectFilter> filter = new SimpleObjectProperty<>(ProjectFilter.ALL);
    private int generation;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the service the stored projects are listed through
     * @param providers where the providers' display names are read
     * @param current the holder that names the project open now
     * @param messages the catalogue provider names are worded from
     * @param opener what Continue and Open use
     * @param actions what the row menu and the book header lead to
     * @param revealer what Show book file asks of the system
     * @param errors where a failure to list is shown
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public ProjectsViewModel(
            final ProjectService projects,
            final ProviderConfigs providers,
            final CurrentProject current,
            final Messages messages,
            final ProjectOpener opener,
            final ProjectActions actions,
            final FileRevealer revealer,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.providers = Objects.requireNonNull(providers, "providers");
        this.current = Objects.requireNonNull(current, "current");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.revealer = Objects.requireNonNull(revealer, "revealer");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** {@return what the screen shows now} */
    public ReadOnlyObjectProperty<ProjectsState> state() {
        return state.getReadOnlyProperty();
    }

    /** {@return the books with their translations, all of them, before the search and filter} */
    public ReadOnlyObjectProperty<List<BookGroup>> groups() {
        return groups.getReadOnlyProperty();
    }

    /** {@return the search text; the screen filters by it as it changes} */
    public StringProperty search() {
        return search;
    }

    /** {@return the chosen All, In progress or Finished filter} */
    public ObjectProperty<ProjectFilter> filter() {
        return filter;
    }

    /**
     * The rows of a book that the search and the filter keep: a search matches the book's file name or title, or the
     * project's name, ignoring case.
     *
     * @param group the book
     * @return the matching rows in list order; empty when none matches
     */
    public List<ProjectRow> visibleRows(final BookGroup group) {
        Objects.requireNonNull(group, "group");
        final String query = search.get().strip().toLowerCase(Locale.ROOT);
        final ProjectFilter chosen = filter.get();
        final boolean bookMatches = contains(group.fileName(), query) || contains(group.title(), query);
        return group.rows().stream()
                .filter(row -> chosen.admits(row.status()))
                .filter(row -> bookMatches || contains(row.summary().project().name(), query))
                .toList();
    }

    private static boolean contains(final String text, final String query) {
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
    }

    /**
     * Empties the search, as each visit of the screen begins: a search left from the last visit would hide rows behind
     * "No project matches" the person no longer remembers typing. The filter is kept. FX thread only.
     */
    public void clearSearch() {
        log.debug(
                "the search is cleared for a new visit, it held {} characters",
                search.get().length());
        search.set("");
    }

    /** Lists the stored projects again. Returns at once; the answer arrives on the FX thread. FX thread only. */
    public void refresh() {
        final int mine = ++generation;
        log.debug("listing the projects, request {}", mine);
        state.set(ProjectsState.LOADING);
        executor.execute(() -> listOffThread(mine));
    }

    private void listOffThread(final int mine) {
        log.debug("listing the projects on {}", Thread.currentThread().getName());
        Result<List<ProjectSummary>> listed;
        Map<String, String> names = Map.of();
        try {
            listed = projects.list();
            names = providerNames();
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning a result while listing", thrown);
            listed = Result.err(AppError.of(
                    ErrorCode.internal,
                    "Unexpected error",
                    "The projects could not be listed because of an unexpected error.",
                    null,
                    thrown));
        }
        final Result<List<ProjectSummary>> answer = listed;
        final Map<String, String> providerNames = names;
        Platform.runLater(() -> publish(mine, answer, providerNames));
    }

    private Map<String, String> providerNames() {
        final Map<String, String> names = new HashMap<>();
        for (final ProviderConfig config : providers.all()) {
            final @Nullable String shown = config.displayName();
            names.put(
                    config.id(),
                    shown == null || shown.isBlank() ? ProviderNames.displayName(messages, config.id()) : shown);
        }
        return names;
    }

    private void publish(
            final int mine, final Result<List<ProjectSummary>> answer, final Map<String, String> providerNames) {
        if (mine != generation) {
            log.debug("listing request {} is stale, the answer is dropped", mine);
            return;
        }
        final List<ProjectSummary> listed = answer.data();
        if (listed == null) {
            final AppError failure = Objects.requireNonNull(answer.error(), "error");
            log.warn("the projects could not be listed: {}", failure.code());
            groups.set(List.of());
            state.set(ProjectsState.FAILED);
            errors.present(failure, this::refresh);
            return;
        }
        final @Nullable OpenedBook open = current.book().get();
        final List<BookGroup> books = ProjectRows.of(listed, open == null ? null : open.projectId(), providerNames);
        log.info("projects listed: {} project(s) in {} book(s)", listed.size(), books.size());
        groups.set(books);
        state.set(books.isEmpty() ? ProjectsState.EMPTY : ProjectsState.LIST);
    }

    /**
     * Opens a row's project at its step.
     *
     * @param row the row Continue or Open was pressed on
     */
    public void open(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        log.debug("open pressed on project {}", row.summary().project().id());
        opener.open(row);
    }

    /**
     * Shows a row's book file in the file manager, asked off the FX thread because a file manager may block.
     *
     * @param row the row Show book file was chosen on
     */
    public void showBookFile(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        final Path source = row.summary().project().source();
        log.debug("show book file chosen on project {}", row.summary().project().id());
        log.trace("showing {}", source);
        executor.execute(() -> reveal(source));
    }

    private void reveal(final Path source) {
        try {
            revealer.reveal(source);
        } catch (Throwable thrown) {
            log.warn("the book file could not be shown", thrown);
        }
    }

    /**
     * Asks to rename a row's project.
     *
     * @param row the row Rename… was chosen on
     */
    public void rename(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        log.debug("rename chosen on project {}", row.summary().project().id());
        actions.rename(row);
    }

    /**
     * Asks to remove a row's project.
     *
     * @param row the row Remove project… was chosen on
     */
    public void remove(final ProjectRow row) {
        Objects.requireNonNull(row, "row");
        log.debug("remove chosen on project {}", row.summary().project().id());
        actions.remove(row);
    }

    /**
     * Asks to start another translation of a book.
     *
     * @param group the book whose header's button was pressed
     */
    public void translateInto(final BookGroup group) {
        Objects.requireNonNull(group, "group");
        log.debug("translate into another language pressed on book {}", group.contentHash());
        actions.translateInto(group);
    }
}
