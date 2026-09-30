package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.util.paths.DestinationPath;

/**
 * Where the translation is written and whether an existing file may be replaced, for the open book.
 *
 * <p>A singleton, like {@link CurrentProject}, because the screens are rebuilt on every visit while the choices must
 * survive them. It is first constructed when a screen first needs it, which can be long after the book was opened, so
 * it proposes a destination for a book that is already open. The proposed file name is never built here:
 * {@link DestinationPath} owns that rule, so the command line and the window cannot drift apart. The proposal follows
 * the brief's target language until the person has typed or picked a path of their own.
 *
 * <p>Looking for a file at the destination is I/O, so it runs on the background executor and its answer is published
 * back with {@code Platform.runLater}. Each question bumps a counter and an answer is published only if the counter
 * is what it was when the question was asked, so a slow answer about a path typed earlier can never overwrite a newer
 * one. While an answer is in flight {@link #destinationExists()} keeps its previous value rather than flickering
 * through a guessed one. Everything else here is touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class ExportViewModel {

    private final CurrentProject project;
    private final ExecutorService executor;
    private final ReadOnlyStringWrapper destination = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper overwrite = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper destinationExists = new ReadOnlyBooleanWrapper(false);
    // Whether the person, and not a proposal, put the text in the destination. FX thread only.
    private boolean destinationEdited;
    // The target the last proposal was made for, so a brief change that leaves it alone proposes nothing. FX thread
    // only.
    private @Nullable String proposedFor;
    // Counts the questions asked about the destination; an answer for any other count is stale. FX thread only.
    private long epoch;

    /**
     * Follows the open book and its brief, and proposes a destination for a book that is already open.
     *
     * @param project the holder of the open book, which outlives this view model
     * @param executor the daemon executor the existence check runs on, never the FX thread
     */
    @Inject
    public ExportViewModel(final CurrentProject project, @BackgroundExecutor final ExecutorService executor) {
        this.project = Objects.requireNonNull(project, "project");
        this.executor = Objects.requireNonNull(executor, "executor");
        project.book().addListener((observed, was, now) -> onBookChanged(now));
        project.brief().addListener((observed, was, now) -> onBriefChanged(now));
        log.debug(
                "export view model created, a book is already open: {}",
                project.book().get() != null);
        propose();
        refreshExists();
    }

    /**
     * Where the translation is written, as the person sees and edits it.
     *
     * @return a read-only property holding the path text, empty while no book is open or no target language is chosen,
     *     or after it was cleared by hand
     */
    public ReadOnlyStringProperty destination() {
        return destination.getReadOnlyProperty();
    }

    /**
     * Whether an existing destination file may be replaced.
     *
     * @return a read-only property; off until a person allows it
     */
    public ReadOnlyBooleanProperty overwrite() {
        return overwrite.getReadOnlyProperty();
    }

    /**
     * Whether the run would be refused for an occupied destination, so a screen can say so before it starts.
     *
     * @return a read-only property, true only while a file exists at the destination and replacing is not allowed
     */
    public ReadOnlyBooleanProperty destinationExists() {
        return destinationExists.getReadOnlyProperty();
    }

    /**
     * Looks again for a file at the destination, because one can appear or vanish while nothing here changes and the
     * warning is only worth showing if it is current. The answer arrives later on the FX thread.
     */
    public void refresh() {
        log.debug("destination existence refreshed");
        refreshExists();
    }

    /**
     * Takes a path a person typed. From here on a target change leaves it alone.
     *
     * @param text the path as typed; blank or unparsable text is kept as typed and simply yields no path
     */
    public void editDestination(final String text) {
        Objects.requireNonNull(text, "text");
        log.debug("destination edited by hand");
        log.trace("destination edited by hand to '{}'", text);
        destinationEdited = true;
        destination.set(text);
        refreshExists();
    }

    /**
     * Takes a path picked in the file chooser, which counts as a choice like typing one does.
     *
     * @param chosen the file the person picked
     */
    public void chooseDestination(final Path chosen) {
        Objects.requireNonNull(chosen, "chosen");
        log.debug("destination chosen in the file chooser: {}", chosen);
        editDestination(chosen.toString());
    }

    /**
     * Allows or forbids replacing a file that already exists at the destination.
     *
     * @param allowed whether replacing is allowed
     */
    public void setOverwrite(final boolean allowed) {
        log.debug("overwrite is now {}", allowed);
        overwrite.set(allowed);
        refreshExists();
    }

    /**
     * The destination as a path.
     *
     * @return the parsed destination, or empty when the text is blank or is not a valid path on this system
     */
    public Optional<Path> destinationPath() {
        final String text = destination.get();
        if (text.isBlank()) {
            log.debug("destination has no path: the text is blank");
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(text));
        } catch (InvalidPathException invalid) {
            log.debug("destination has no path: the text is not valid here ({})", invalid.getReason());
            return Optional.empty();
        }
    }

    /**
     * What the interim export after a completed run needs.
     *
     * @return the destination with the overwrite choice, or empty while the destination names no usable path
     */
    public Optional<InterimRunRequest> interimExport() {
        return destinationPath().map(path -> new InterimRunRequest(path, overwrite.get()));
    }

    private void onBookChanged(final @Nullable OpenedBook book) {
        log.debug(
                "the open book changed to {}; the destination is proposed afresh",
                book == null ? null : book.projectId());
        destinationEdited = false;
        overwrite.set(false);
        propose();
        refreshExists();
    }

    private void onBriefChanged(final @Nullable BookBrief brief) {
        final String target = brief == null ? null : brief.targetLanguage();
        if (Objects.equals(target, proposedFor)) {
            return;
        }
        log.debug("the brief's target is now {}, destination hand-edited: {}", target, destinationEdited);
        if (!destinationEdited) {
            propose();
            refreshExists();
        }
    }

    private void propose() {
        final OpenedBook book = project.book().get();
        final BookBrief brief = project.brief().get();
        final String target = brief == null ? null : brief.targetLanguage();
        proposedFor = target;
        if (book == null || target == null) {
            log.debug("no proposal: a book is open {}, a target is chosen {}", book != null, target != null);
            destination.set("");
            return;
        }
        final BookFormat format = Objects.requireNonNull(book.inspection().format(), "format");
        final Path proposed = DestinationPath.destinationFor(book.source(), format, target);
        log.debug("proposing destination {} for project {} ({})", proposed, book.projectId(), format);
        destination.set(proposed.toString());
    }

    private void refreshExists() {
        epoch++;
        final long asked = epoch;
        final Optional<Path> path = destinationPath();
        if (overwrite.get() || path.isEmpty()) {
            log.debug("existence not asked: overwrite allowed {}, usable path {}", overwrite.get(), path.isPresent());
            destinationExists.set(false);
            return;
        }
        submit(path.get(), asked);
    }

    private void submit(final Path path, final long asked) {
        log.debug("existence of {} asked as question {}", path, asked);
        try {
            executor.execute(() -> answer(path, asked));
        } catch (RejectedExecutionException rejected) {
            log.warn("existence of {} could not be asked; the warning keeps its last value", path, rejected);
        }
    }

    private void answer(final Path path, final long asked) {
        final boolean exists = Files.exists(path);
        log.debug(
                "question {} answered on {}: exists {}",
                asked,
                Thread.currentThread().getName(),
                exists);
        Platform.runLater(() -> publish(asked, exists));
    }

    private void publish(final long asked, final boolean exists) {
        if (asked != epoch) {
            log.debug("answer to question {} discarded: the current one is {}", asked, epoch);
            return;
        }
        log.debug("answer to question {} published: destination occupied {}", asked, exists);
        destinationExists.set(exists);
    }
}
