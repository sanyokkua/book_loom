package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.dialog.ReplaceRunPrompt;

/**
 * What a chosen file passes through before {@link ImportViewModel#open} sees it: one translation exists per
 * application run, so importing another book while the current run can still continue asks first, and only a
 * confirmed answer stops the run and lets the import replace it.
 *
 * <p>The wait for a stopping run never blocks the FX thread: it watches the mirror's run state and carries on from the
 * change that ends it. FX thread only.
 */
@Slf4j
@Singleton
public final class ImportGuard {

    private final StateMirror mirror;
    private final TranslationRunner runner;
    private final ImportViewModel imports;
    private final CurrentProject current;
    private final ReplaceRunPrompt prompt;

    /**
     * Wires the guard to the run it protects and the import it gates.
     *
     * @param mirror where the run's state and file name are read and cleared
     * @param runner what stops a run that is still going
     * @param imports where the file is finally opened, and the open book released
     * @param current the open book, named in the log when its run is discarded
     * @param prompt the question put to the person
     */
    @Inject
    public ImportGuard(
            final StateMirror mirror,
            final TranslationRunner runner,
            final ImportViewModel imports,
            final CurrentProject current,
            final ReplaceRunPrompt prompt) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.imports = Objects.requireNonNull(imports, "imports");
        this.current = Objects.requireNonNull(current, "current");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
    }

    /**
     * Opens {@code source} at once when no run can continue, otherwise asks first. FX thread only.
     *
     * @param source the file the person chose
     */
    public void requestImport(final Path source) {
        Objects.requireNonNull(source, "source");
        final RunState state = mirror.runState().get();
        log.debug("import of {} requested while the run is {}", source, state);
        switch (state) {
            case IDLE -> {
                log.debug("no run exists: opening without a question");
                imports.open(source);
            }
            case COMPLETED, FAILED -> {
                log.debug("the run is over: clearing it and opening without a question");
                mirror.publishRunCleared();
                imports.open(source);
            }
            case RUNNING, PAUSING, PAUSED, STOPPING, STOPPED -> ask(source, state);
        }
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
        final OpenedBook book = current.book().get();
        log.info(
                "run of {} discarded for an import: project {}, new file {}",
                mirror.runFileName().get(),
                book == null ? null : book.projectId(),
                fileNameOf(source));
        mirror.publishRunCleared();
        imports.cancel();
        imports.open(source);
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
