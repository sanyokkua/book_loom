package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.OpenAnotherBookPrompt;
import ua.bookloom.ui.notify.ToastOccasion;
import ua.bookloom.ui.notify.Toasts;
import ua.bookloom.util.hash.HashUtil;

/**
 * What a chosen file passes through before {@link ImportViewModel#open} sees it.
 *
 * <p>While a project is open the person is asked first ("Open another book?"), whatever its run is doing, because the
 * answer decides whether that project is kept. Only once the question is answered, or when no project is open, is the
 * file's content looked up among the stored projects: a book that is already stored is named, never imported again,
 * because importing resets a stored project's segments. A run that is still going is stopped and waited for before
 * either answer carries on, since one translation exists per application run; it can be continued from Projects
 * later. Neither the wait nor the lookup blocks the FX thread: the first follows the mirror's run state, the second
 * runs on the background executor. FX thread only.
 */
@Slf4j
@Singleton
public final class ImportGuard {

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ImportViewModel imports;
    private final CurrentProject current;
    private final OpenAnotherBookPrompt prompt;
    private final ProjectService projects;
    private final ExecutorService executor;
    private final Toasts toasts;
    private boolean checking;

    /**
     * Wires the guard to the run it protects and the import it gates.
     *
     * @param mirror where the run's state is read and cleared
     * @param runner what stops a run that is still going
     * @param imports where the file is finally opened
     * @param current the open book, which the question names
     * @param prompt the question put to the person
     * @param projects what is asked for the stored projects, to name the open one and to find a book already stored
     * @param executor the daemon executor those lookups run on, never the FX thread
     * @param toasts where a file that could not be checked is announced
     */
    @Inject
    public ImportGuard(
            final StateMirror mirror,
            final TranslationRunner runner,
            final ImportViewModel imports,
            final CurrentProject current,
            final OpenAnotherBookPrompt prompt,
            final ProjectService projects,
            @BackgroundExecutor final ExecutorService executor,
            final Toasts toasts) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.current = Objects.requireNonNull(current, "current");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
    }

    /**
     * Asks first when a project is open, otherwise checks whether the book is already stored and opens it. FX thread
     * only.
     *
     * @param source the file the person chose
     */
    public void requestImport(final Path source) {
        Objects.requireNonNull(source, "source");
        final RunState state = mirror.runState().get();
        final OpenedBook book = current.book().get();
        log.debug("import of {} requested: project open {}, run {}", fileNameOf(source), book != null, state);
        if (book == null) {
            log.debug("no project is open: {} is checked against the stored projects", fileNameOf(source));
            final boolean isOver = state == RunState.COMPLETED || state == RunState.FAILED;
            openChecked(source, null, isOver ? mirror::publishRunCleared : () -> {});
            return;
        }
        log.debug("project {} is open: asking before {} is opened", book.projectId(), fileNameOf(source));
        ask(source, book, state);
    }

    private void ask(final Path source, final OpenedBook book, final RunState state) {
        try {
            executor.execute(() -> {
                final String name = projectName(book);
                Platform.runLater(() -> asked(source, book, state, name));
            });
        } catch (RuntimeException rejected) {
            log.warn("the open project's name could not be looked up: using its file name", rejected);
            asked(source, book, state, fileNameOf(book.source()));
        }
    }

    // The stored project's own display name; the file's name when it cannot be found.
    private String projectName(final OpenedBook book) {
        try {
            final List<ProjectSummary> all = projects.list().data();
            if (all != null) {
                return all.stream()
                        .filter(summary -> summary.project().id().equals(book.projectId()))
                        .map(summary -> summary.project().name())
                        .findFirst()
                        .orElse(fileNameOf(book.source()));
            }
        } catch (Throwable thrown) {
            log.warn("the project service threw while naming the open project", thrown);
        }
        return fileNameOf(book.source());
    }

    private void asked(final Path source, final OpenedBook book, final RunState state, final String name) {
        log.debug("asking about opening {} beside {} (run {})", fileNameOf(source), book.projectId(), state);
        prompt.ask(name, fileNameOf(source), state, () -> openNew(source, book, state), () -> replace(source, book));
    }

    private void openNew(final Path source, final OpenedBook book, final RunState state) {
        log.info("open-another answered: open as a new project, beside project {}", book.projectId());
        final Runnable proceed = () -> {
            prompt.dismiss();
            openChecked(source, null, this::clearEndedRun);
        };
        switch (state) {
            case RUNNING -> whenReady(RunStates::hasEnded, true, proceed);
            case PAUSING, STOPPING -> whenReady(RunStates::isSettled, false, proceed);
            case IDLE, PAUSED, STOPPED, COMPLETED, FAILED -> proceed.run();
        }
    }

    // A run that has ended belongs to the project left behind, so the new one does not show it; a paused run stays.
    private void clearEndedRun() {
        final RunState state = mirror.runState().get();
        final boolean ended = state != RunState.IDLE && RunStates.hasEnded(state);
        log.debug("the run of the project left behind is {}: cleared {}", state, ended);
        if (ended) {
            mirror.publishRunCleared();
        }
    }

    private void replace(final Path source, final OpenedBook book) {
        log.info("open-another answered: replace project {}", book.projectId());
        final Runnable proceed = () -> {
            prompt.dismiss();
            openChecked(source, book.projectId(), mirror::publishRunCleared);
        };
        if (RunStates.hasEnded(mirror.runState().get())) {
            proceed.run();
        } else {
            whenReady(RunStates::hasEnded, true, proceed);
        }
    }

    // Carries on from the first state that satisfies done; the run is told to stop first when cancel is set.
    private void whenReady(final Predicate<RunState> done, final boolean cancel, final Runnable then) {
        final boolean waits = !done.test(mirror.runState().get());
        log.debug("open-another waits for the run {}, cancelling {}", waits, cancel);
        RunWaits.when(mirror, done, then);
        if (waits && cancel) {
            runner.cancel();
        }
    }

    // The file is read and looked up off the FX thread; the run is cleared only when the import goes ahead. A project
    // that is about to be replaced does not count as a duplicate, any other stored copy does: creating over it would
    // reset its segments.
    private void openChecked(final Path source, final @Nullable String replacedId, final Runnable clearRun) {
        if (checking) {
            log.warn("import of {} dropped: another file is still being checked", fileNameOf(source));
            return;
        }
        checking = true;
        try {
            executor.execute(() -> lookUp(source, replacedId, clearRun));
        } catch (RuntimeException rejected) {
            log.warn("{} could not be looked up: opening without checking", fileNameOf(source), rejected);
            checking = false;
            clearRun.run();
            proceed(source, replacedId);
        }
    }

    private void proceed(final Path source, final @Nullable String replacedId) {
        if (replacedId == null) {
            imports.open(source);
        } else {
            imports.openReplacing(source, replacedId);
        }
    }

    // Whatever the check meets, even an Error, the answer goes back to the FX thread, which clears the busy flag: a
    // check that never answered would drop every later import.
    private void lookUp(final Path source, final @Nullable String replacedId, final Runnable clearRun) {
        log.debug(
                "looking up {} on {}",
                fileNameOf(source),
                Thread.currentThread().getName());
        @Nullable List<ProjectSummary> found = null;
        try {
            found = matchesOf(source);
        } catch (Throwable failed) {
            log.warn("{} could not be checked against the stored projects: opening it anyway", fileNameOf(source));
            log.debug("the check failed", failed);
        } finally {
            final @Nullable List<ProjectSummary> answered = found;
            Platform.runLater(() -> checked(source, replacedId, answered, clearRun));
        }
    }

    // The stored copies of the file other than the one being replaced; null when the file could not be checked, which
    // never stops a book from opening.
    private @Nullable List<ProjectSummary> matchesOf(final Path source) {
        try {
            final String hash = HashUtil.sha256HexOfFile(source);
            final Result<List<ProjectSummary>> listed = projects.list();
            final List<ProjectSummary> all = listed.data();
            if (all == null) {
                log.warn("the stored projects could not be listed for {}: {}", fileNameOf(source), listed.error());
                return null;
            }
            return EarlierProjects.matching(all, hash);
        } catch (RuntimeException failed) {
            log.warn("{} could not be checked against the stored projects: opening it anyway", fileNameOf(source));
            log.debug("the check failed", failed);
            return null;
        }
    }

    private void checked(
            final Path source,
            final @Nullable String replacedId,
            final @Nullable List<ProjectSummary> matches,
            final Runnable clearRun) {
        checking = false;
        if (matches == null) {
            log.debug("{} was not checked: the person is told and it is opened", fileNameOf(source));
            toasts.raise(ToastOccasion.IMPORT_UNCHECKED, fileNameOf(source));
        }
        final List<ProjectSummary> found = matches == null
                ? List.of()
                : matches.stream()
                        .filter(summary -> !summary.project().id().equals(replacedId))
                        .toList();
        if (found.isEmpty()) {
            log.debug("{} matches no stored project: opening it", fileNameOf(source));
            clearRun.run();
            proceed(source, replacedId);
            return;
        }
        log.info("{} is already stored as {} project(s)", fileNameOf(source), found.size());
        imports.showAlready(source, found);
    }

    private static String fileNameOf(final Path source) {
        final Path name = source.getFileName();
        return name == null ? source.toString() : name.toString();
    }
}
