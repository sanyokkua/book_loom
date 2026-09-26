package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.MetadataKey;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the import screen shows and which book the application has open.
 *
 * <p>A singleton because the screen's controller is rebuilt on every visit while the open book must outlive it: the
 * brief and structure screens read it. Opening is a plain submission to the background executor, not a JavaFX
 * {@code Task}, because the port reports a refusal by <em>returning</em> it (D12 of the workspace design). Every
 * property is touched on the FX Application Thread only; the port alone runs elsewhere.
 */
@Slf4j
@Singleton
public final class ImportViewModel {

    private final DocumentPort documents;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<ImportState> state = new ReadOnlyObjectWrapper<>(new ImportState.Idle());
    private final ReadOnlyObjectWrapper<OpenedBook> openedBook = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper opening = new ReadOnlyBooleanWrapper(false);
    // What was showing when the open began, for a fault that leaves the book already open untouched. FX thread only.
    private ImportState stateBeforeOpen = new ImportState.Idle();

    /**
     * Wires the view model to the port it opens books through.
     *
     * @param documents the port a book is opened and released through
     * @param toasts where a successful open is announced
     * @param errors where an unexpected failure is shown; a refusal the person can act on is shown in place instead
     * @param executor the daemon executor the port runs on, never the FX thread
     */
    @Inject
    public ImportViewModel(
            final DocumentPort documents,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * What the screen shows now.
     *
     * @return a read-only property; {@link ImportState.Idle} until a book is chosen
     */
    public ReadOnlyObjectProperty<ImportState> state() {
        return state.getReadOnlyProperty();
    }

    /**
     * The book the application has open, for the screens that follow this one.
     *
     * @return a read-only property holding {@code null} while no book is open, including after a refusal; an
     *     unexpected failure leaves the book that was open open
     */
    public ReadOnlyObjectProperty<OpenedBook> openedBook() {
        return openedBook.getReadOnlyProperty();
    }

    /**
     * Whether a book has been handed to the port and the answer has not come back.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty opening() {
        return opening.getReadOnlyProperty();
    }

    /**
     * Opens a book. Returns at once; the answer arrives on the FX thread. Ignored while another open is unanswered,
     * so two parses can never race to be the open book. FX thread only.
     *
     * @param source the file to open
     */
    public void open(final Path source) {
        Objects.requireNonNull(source, "source");
        log.debug("open requested for {}", source);
        if (opening.get()) {
            log.debug("open of {} ignored: a book is still being opened", source);
            return;
        }
        final String fileName = fileNameOf(source);
        stateBeforeOpen = state.get();
        opening.set(true);
        state.set(new ImportState.Opening(fileName));
        log.info("opening {}", source);
        submit(source, fileName);
    }

    /**
     * Puts the screen in the language-mismatch state. Nothing detects a source language yet, so no input reaches this
     * through {@link #open}; it exists so the state the screen is specified to have can be shown and tested. FX
     * thread only.
     *
     * @param card what the parse found
     * @param detectedLang the language the text is in
     */
    public void showLanguageMismatch(final BookCard card, final String detectedLang) {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(detectedLang, "detectedLang");
        log.debug(
                "language mismatch shown for {}: declared {}, detected {}",
                card.fileName(),
                card.declaredLang(),
                detectedLang);
        state.set(new ImportState.LanguageMismatch(card, detectedLang));
    }

    private void submit(final Path source, final String fileName) {
        try {
            executor.execute(() -> openOffThread(source, fileName));
        } catch (RuntimeException rejected) {
            log.error("opening {} could not be submitted", fileName, rejected);
            publish(source, fileName, Result.err(internalError(rejected)));
        }
    }

    private void openOffThread(final Path source, final String fileName) {
        log.debug("parsing {} on {}", source, Thread.currentThread().getName());
        final Result<Parsed> answer = parse(source, fileName);
        Platform.runLater(() -> publish(source, fileName, answer));
    }

    // The card is built here, off the FX thread, so that a book whose card cannot be built is released before it was
    // ever published and the FX thread never walks a large book's units.
    private Result<Parsed> parse(final Path source, final String fileName) {
        final Result<Document> opened = openGuarded(source);
        final Document document = opened.data();
        if (document == null) {
            return Result.err(Objects.requireNonNull(opened.error()));
        }
        try {
            return Result.ok(new Parsed(document, cardOf(fileName, document)));
        } catch (RuntimeException thrown) {
            log.error("the card of {} could not be built", fileName, thrown);
            closeGuarded(document);
            return Result.err(internalError(thrown));
        }
    }

    private Result<Document> openGuarded(final Path source) {
        try {
            return documents.open(source);
        } catch (Throwable thrown) {
            log.error("the document port threw instead of returning a result while opening {}", source, thrown);
            return Result.err(internalError(thrown));
        }
    }

    private static AppError internalError(final Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The book could not be opened because of an unexpected error.",
                null,
                cause);
    }

    private void publish(final Path source, final String fileName, final Result<Parsed> result) {
        final AppError failure = result.error();
        final boolean replacesPrevious = failure == null || failure.code() == ErrorCode.validation;
        log.debug(
                "publishing the answer for {}: failure {}, replaces the open book {}",
                fileName,
                failure,
                replacesPrevious);
        final OpenedBook previous = openedBook.get();
        try {
            if (failure == null) {
                accept(source, fileName, Objects.requireNonNull(result.data()));
            } else if (replacesPrevious) {
                refuse(fileName, failure);
            } else {
                keepPrevious(fileName, failure);
            }
        } finally {
            opening.set(false);
            if (replacesPrevious) {
                release(previous);
            }
        }
    }

    private void accept(final Path source, final String fileName, final Parsed parsed) {
        final BookCard card = parsed.card();
        openedBook.set(new OpenedBook(source, parsed.document()));
        state.set(new ImportState.Detected(card));
        log.info(
                "opened {}: format {}, {} unit(s), {} segment(s)",
                source,
                card.format(),
                card.unitCount(),
                card.segmentCount());
        toasts.success(MessageKey.TOAST_BOOK_OPENED, fileName);
    }

    private void refuse(final String fileName, final AppError refusal) {
        openedBook.set(null);
        log.warn("{} was refused with {}: {}", fileName, refusal.code(), refusal.title());
        log.debug("{} is shown in place: a refusal the person can act on", fileName);
        state.set(new ImportState.Refused(fileName, refusal));
    }

    // A fault says nothing about the book already open, so it stays open and the screen goes back to showing it.
    private void keepPrevious(final String fileName, final AppError failure) {
        log.warn("{} failed with {}: {}", fileName, failure.code(), failure.title());
        log.debug("{} goes to the error presenter; the open book and the state before the open are kept", fileName);
        state.set(stateBeforeOpen);
        errors.present(failure);
    }

    private static BookCard cardOf(final String fileName, final Document document) {
        log.debug("building the card of {} ({})", fileName, document.format());
        final Map<String, String> metadata = document.metadata();
        final int segments = document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
        return new BookCard(
                fileName,
                document.format(),
                declaredOrNull(metadata.get(MetadataKey.TITLE.key())),
                declaredOrNull(metadata.get(MetadataKey.AUTHOR.key())),
                declaredOrNull(document.declaredLang()),
                document.units().size(),
                segments);
    }

    /** A blank value is no declaration: the row is omitted rather than shown empty. */
    private static @Nullable String declaredOrNull(final @Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String fileNameOf(final Path source) {
        final Path name = source.getFileName();
        return name == null ? source.toString() : name.toString();
    }

    private void release(final @Nullable OpenedBook previous) {
        if (previous == null) {
            log.debug("no previous book to release");
            return;
        }
        try {
            executor.execute(() -> closeGuarded(previous.document()));
            log.debug("releasing book {} submitted", previous.document().id());
        } catch (RuntimeException rejected) {
            log.warn("releasing the previous book could not be submitted", rejected);
        }
    }

    private void closeGuarded(final Document document) {
        log.debug(
                "releasing book {} on {}", document.id(), Thread.currentThread().getName());
        try {
            final Result<Boolean> released = documents.close(document);
            final AppError failure = released.error();
            if (failure != null) {
                log.warn("releasing book {} failed with {}", document.id(), failure.code());
            } else {
                log.debug("released book {}; it was open: {}", document.id(), released.data());
            }
        } catch (Throwable thrown) {
            log.error("the document port threw while releasing book {}", document.id(), thrown);
        }
    }

    /** A parsed book with the card built from it, so the FX thread only publishes. */
    private record Parsed(Document document, BookCard card) {}
}
