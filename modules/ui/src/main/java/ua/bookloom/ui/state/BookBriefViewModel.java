package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.util.paths.DestinationPath;

/**
 * The parts of a {@link TranslationRequest} a person chooses before a run: the target language, where the
 * translation is written and whether an existing file may be replaced. Nothing else on the brief screen is read here,
 * because nothing else on it is available yet.
 *
 * <p>A singleton, like {@link ImportViewModel}, because the screen's controller is rebuilt on every visit while the
 * choices must survive it. It is first constructed when the brief is first shown, which can be long after the book
 * was opened, so it proposes a destination for a book that is already open. The proposed file name is never built
 * here: {@link DestinationPath} owns that rule, so the command line and this screen cannot drift apart.
 *
 * <p>Looking for a file at the destination is I/O, so it runs on the background executor and its answer is published
 * back with {@code Platform.runLater}. Each question bumps a counter and an answer is published only if the counter
 * is what it was when the question was asked, so a slow answer about a path typed earlier can never overwrite a newer
 * one. While an answer is in flight {@link #destinationExists()} keeps its previous value rather than flickering
 * through a guessed one. Everything else here is touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class BookBriefViewModel {

    /** The languages a translation can be written in: the four the reference draws, in the order it draws them. */
    public static final List<String> TARGET_LANGUAGES = List.of("uk", "en", "pl", "de");

    private static final String DEFAULT_TARGET = TARGET_LANGUAGES.get(0);

    private final ImportViewModel imports;
    private final ExecutorService executor;
    private final ReadOnlyStringWrapper targetLanguage = new ReadOnlyStringWrapper(DEFAULT_TARGET);
    private final ReadOnlyStringWrapper destination = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper overwrite = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper destinationExists = new ReadOnlyBooleanWrapper(false);
    // Whether the person, and not a proposal, put the text in the destination. FX thread only.
    private boolean destinationEdited;
    // Counts the questions asked about the destination; an answer for any other count is stale. FX thread only.
    private long epoch;

    /**
     * Follows the open book, and proposes a destination for one that is already open.
     *
     * @param imports the holder of the open book, which outlives this view model
     * @param executor the daemon executor the existence check runs on, never the FX thread
     */
    @Inject
    public BookBriefViewModel(final ImportViewModel imports, @BackgroundExecutor final ExecutorService executor) {
        this.imports = Objects.requireNonNull(imports, "imports");
        this.executor = Objects.requireNonNull(executor, "executor");
        imports.openedBook().addListener((observed, was, now) -> onBookChanged(now));
        log.debug(
                "book brief created, a book is already open: {}",
                imports.openedBook().get() != null);
        propose(imports.openedBook().get());
        refreshExists();
    }

    /**
     * The book the brief is about.
     *
     * @return a read-only property holding {@code null} while no book is open
     */
    public ReadOnlyObjectProperty<OpenedBook> openedBook() {
        return imports.openedBook();
    }

    /**
     * The language the translation is written in.
     *
     * @return a read-only property; always one of {@link #TARGET_LANGUAGES}
     */
    public ReadOnlyStringProperty targetLanguage() {
        return targetLanguage.getReadOnlyProperty();
    }

    /**
     * Where the translation is written, as the person sees and edits it.
     *
     * @return a read-only property holding the path text, empty while no book is open or after it was cleared by hand
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
     * Whether the run would be refused for an occupied destination, so the screen can say so before it starts.
     *
     * @return a read-only property, true only while a file exists at the destination and replacing is not allowed
     */
    public ReadOnlyBooleanProperty destinationExists() {
        return destinationExists.getReadOnlyProperty();
    }

    /**
     * Looks again for a file at the destination, because one can appear or vanish while nothing here changes and the
     * warning is only worth showing if it is current. Called whenever the brief is built. The answer arrives later on
     * the FX thread.
     */
    public void refresh() {
        log.debug("destination existence refreshed");
        refreshExists();
    }

    /**
     * The language the open book declares, which is shown and never edited.
     *
     * @return the declared language code, or empty when no book is open or it declares none
     */
    public Optional<String> sourceLanguage() {
        final OpenedBook book = imports.openedBook().get();
        if (book == null) {
            return Optional.empty();
        }
        final String declared = book.document().declaredLang();
        return declared == null || declared.isBlank() ? Optional.empty() : Optional.of(declared);
    }

    /**
     * Chooses the target language; a proposed destination follows it, a destination the person chose does not.
     *
     * @param code one of {@link #TARGET_LANGUAGES}; anything else is ignored, because the picker offers nothing else
     */
    public void selectTarget(final String code) {
        Objects.requireNonNull(code, "code");
        if (!TARGET_LANGUAGES.contains(code)) {
            log.debug("target {} ignored: it is not one of {}", code, TARGET_LANGUAGES);
            return;
        }
        targetLanguage.set(code);
        log.debug("target language is now {}, destination hand-edited: {}", code, destinationEdited);
        if (!destinationEdited) {
            propose(imports.openedBook().get());
        }
        refreshExists();
    }

    /**
     * Takes a path a person typed. From here on a target change leaves it alone.
     *
     * @param text the path as typed; blank or unparsable text is kept as typed and simply yields no request
     */
    public void editDestination(final String text) {
        Objects.requireNonNull(text, "text");
        log.debug("destination edited by hand to '{}'", text);
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
            log.debug("destination has no path: '{}' is not valid here ({})", text, invalid.getReason());
            return Optional.empty();
        }
    }

    /**
     * Assembles what a run needs from the choices made here.
     *
     * @return the request, or empty while no book is open or the destination names no usable path; the source
     *     language is left null so that the book's own declaration is used
     */
    public Optional<TranslationRequest> request() {
        final OpenedBook book = imports.openedBook().get();
        if (book == null) {
            log.debug("no request: no book is open");
            return Optional.empty();
        }
        return destinationPath()
                .map(path -> new TranslationRequest(book.source(), path, targetLanguage.get(), null, overwrite.get()));
    }

    private void onBookChanged(final @Nullable OpenedBook book) {
        log.debug(
                "the open book changed to {}; the destination is proposed afresh", book == null ? null : book.source());
        destinationEdited = false;
        overwrite.set(false);
        propose(book);
        refreshExists();
    }

    private void propose(final @Nullable OpenedBook book) {
        if (book == null) {
            log.debug("no proposal: no book is open");
            destination.set("");
            return;
        }
        final Document document = book.document();
        final Path proposed = DestinationPath.destinationFor(book.source(), document.format(), targetLanguage.get());
        log.debug("proposing destination {} for {} ({})", proposed, book.source(), document.format());
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
