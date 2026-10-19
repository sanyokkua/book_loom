package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.ReplaceRunPrompt;

/**
 * What a chosen file passes through before {@link ImportViewModel#open} sees it: one translation exists per
 * application run, so importing another book while the current run can still continue asks first, and only a
 * confirmed answer stops the run and lets the import replace it.
 *
 * <p>A file whose stored project already holds decided segments is asked about once more ("continue the earlier
 * translation or start over"), because importing it again resets them. The wait for a stopping run and the lookup of
 * that count never block the FX thread: the first watches the mirror's run state and carries on from the change that
 * ends it, the second runs on the background executor. FX thread only.
 */
@Slf4j
@Singleton
public final class ImportGuard {

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ImportViewModel imports;
    private final CurrentProject current;
    private final ReplaceRunPrompt prompt;
    private final ProjectService projects;
    private final ExecutorService executor;
    private boolean checking;

    /**
     * Wires the guard to the run it protects and the import it gates.
     *
     * @param mirror where the run's state and file name are read and cleared
     * @param runner what stops a run that is still going
     * @param imports where the file is finally opened, and the open book released
     * @param current the open book, named in the log when its run is discarded
     * @param prompt the question put to the person
     * @param projects what is asked how many segments a file's stored project has decided
     * @param executor the daemon executor that question runs on, never the FX thread
     */
    @Inject
    public ImportGuard(
            final StateMirror mirror,
            final TranslationRunner runner,
            final ImportViewModel imports,
            final CurrentProject current,
            final ReplaceRunPrompt prompt,
            final ProjectService projects,
            @BackgroundExecutor final ExecutorService executor) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.current = Objects.requireNonNull(current, "current");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Opens {@code source} when no run can continue and its stored project holds no decided segments, otherwise asks
     * first. FX thread only.
     *
     * @param source the file the person chose
     */
    public void requestImport(final Path source) {
        Objects.requireNonNull(source, "source");
        final RunState state = mirror.runState().get();
        log.debug("import of {} requested while the run is {}", source, state);
        switch (state) {
            case IDLE -> {
                log.debug("no run exists: nothing to clear");
                openChecked(source, () -> {});
            }
            case COMPLETED, FAILED -> {
                log.debug("the run is over: it is cleared when the import goes ahead");
                openChecked(source, mirror::publishRunCleared);
            }
            case RUNNING, PAUSING, PAUSED, STOPPING, STOPPED -> ask(source, state);
        }
    }

    // The stored project is asked off the FX thread; the run is cleared only when the import goes ahead.
    private void openChecked(final Path source, final Runnable clearRun) {
        if (checking) {
            log.warn("import of {} dropped: another file is still being checked", fileNameOf(source));
            return;
        }
        checking = true;
        try {
            executor.execute(() -> lookUp(source, clearRun));
        } catch (RuntimeException rejected) {
            log.warn("the earlier work of {} could not be looked up: opening without asking", fileNameOf(source));
            checking = false;
            clearRun.run();
            imports.open(source);
        }
    }

    private void lookUp(final Path source, final Runnable clearRun) {
        log.debug(
                "looking up the decided segments of {} on {}",
                fileNameOf(source),
                Thread.currentThread().getName());
        final int decided = decidedOf(source);
        Platform.runLater(() -> answered(source, decided, clearRun));
    }

    // A failed lookup never stops a book from opening: it counts as nothing decided.
    private int decidedOf(final Path source) {
        try {
            final Result<Integer> counted = projects.decidedSegments(source);
            final Integer count = counted.data();
            if (count == null) {
                log.debug("the decided segments of {} could not be counted: {}", fileNameOf(source), counted.error());
                return 0;
            }
            return count;
        } catch (Throwable thrown) {
            log.warn("the project service threw while counting the decided segments", thrown);
            return 0;
        }
    }

    private void answered(final Path source, final int decided, final Runnable clearRun) {
        checking = false;
        if (decided <= 0) {
            log.debug("{} has no decided segments: opening without a question", fileNameOf(source));
            clearRun.run();
            imports.open(source);
            return;
        }
        log.debug("{} has {} decided segment(s): asking whether to continue", fileNameOf(source), decided);
        prompt.askEarlierWork(
                fileNameOf(source),
                decided,
                () -> {
                    log.info("the earlier translation of {} is continued", fileNameOf(source));
                    clearRun.run();
                    imports.continueWith(source);
                },
                () -> {
                    log.info("the earlier translation of {} is discarded: starting over", fileNameOf(source));
                    clearRun.run();
                    imports.open(source);
                });
    }

    private void ask(final Path source, final RunState state) {
        final String runFile = Objects.requireNonNullElse(mirror.runFileName().get(), "");
        log.debug("asking before replacing the {} run of {}", state, runFile);
        prompt.ask(runFile, fileNameOf(source), state, () -> confirmed(source));
    }

    private void confirmed(final Path source) {
        final RunState state = mirror.runState().get();
        if (hasEnded(state)) {
            log.debug("replacement confirmed with the run {}: nothing to stop", state);
            prompt.dismiss();
            replace(source);
            return;
        }
        log.debug("replacement confirmed with the run {}: stopping it", state);
        final EndWatcher watcher = new EndWatcher(source);
        mirror.runState().addListener(watcher);
        runner.cancel();
        watcher.changed(mirror.runState(), state, mirror.runState().get());
    }

    private void replace(final Path source) {
        openChecked(source, () -> discardRun(source));
    }

    private void discardRun(final Path source) {
        final OpenedBook book = current.book().get();
        log.info(
                "run of {} discarded for an import: project {}, new file {}",
                mirror.runFileName().get(),
                book == null ? null : book.projectId(),
                fileNameOf(source));
        mirror.publishRunCleared();
        imports.cancel();
    }

    private static boolean hasEnded(final RunState state) {
        return switch (state) {
            case IDLE, STOPPED, COMPLETED, FAILED -> true;
            case RUNNING, PAUSING, PAUSED, STOPPING -> false;
        };
    }

    private static String fileNameOf(final Path source) {
        final Path name = source.getFileName();
        return name == null ? source.toString() : name.toString();
    }

    /** Carries on with the replacement from the first state change that ends the run, then stops watching. */
    private final class EndWatcher implements ChangeListener<@Nullable RunState> {

        private final Path source;

        EndWatcher(final Path source) {
            this.source = source;
        }

        @Override
        public void changed(
                final ObservableValue<? extends @Nullable RunState> observed,
                final @Nullable RunState was,
                final @Nullable RunState now) {
            if (now == null || !hasEnded(now)) {
                return;
            }
            mirror.runState().removeListener(this);
            log.debug("the run ended as {}: replacing it", now);
            prompt.dismiss();
            replace(source);
        }
    }
}
