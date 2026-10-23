package ua.bookloom.ui.state;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SideFile;

/**
 * Whether the book or a chosen side file already exists, asked off the FX thread.
 *
 * <p>Each question bumps a counter and an answer is published only if the counter is unchanged, so a slow answer for
 * an earlier question never overwrites a newer one. Touched on the FX thread only, except the existence check itself.
 */
@Slf4j
final class ExportOccupancy {

    private final ExecutorService executor;
    private final Runnable changed;
    private boolean bookTaken;
    private long epoch;

    ExportOccupancy(final ExecutorService executor, final Runnable changed) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    /** Whether the book itself, the first path asked about, exists. */
    boolean isBookTaken() {
        return bookTaken;
    }

    /** Forgets the last answer and discards any still on its way. */
    void clear() {
        epoch++;
        bookTaken = false;
    }

    /** Asks about the book's path and each chosen side file's; {@code book} is null when nothing is open. */
    void ask(final @Nullable OpenedBook book, final Path path, final Set<SideFile> sides) {
        ask(
                book == null
                        ? List.of(path)
                        : ExportPathRules.writtenPaths(
                                path, Objects.requireNonNull(book.inspection().format(), "format"), sides));
    }

    /** Asks whether any of {@code paths} exists, the book first; the answer arrives later and calls back. */
    void ask(final List<Path> paths) {
        epoch++;
        final long asked = epoch;
        log.debug("existence of {} asked as question {}", paths, asked);
        try {
            executor.execute(() -> answer(paths, asked));
        } catch (RejectedExecutionException rejected) {
            log.warn("existence of {} could not be asked; the warning keeps its last value", paths, rejected);
        }
    }

    private void answer(final List<Path> paths, final long asked) {
        final List<Path> taken = paths.stream().filter(Files::exists).toList();
        log.debug(
                "question {} answered on {}: occupied {}",
                asked,
                Thread.currentThread().getName(),
                taken);
        Platform.runLater(() -> publish(asked, paths.get(0), taken));
    }

    private void publish(final long asked, final Path book, final List<Path> answered) {
        if (asked != epoch) {
            log.debug("answer to question {} discarded: the current one is {}", asked, epoch);
            return;
        }
        log.debug("answer to question {} published: occupied {}", asked, answered);
        bookTaken = answered.contains(book);
        changed.run();
    }
}
