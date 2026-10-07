package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
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
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the import screen shows, and the gateway through which a book becomes {@link CurrentProject}.
 *
 * <p>A singleton because the screen's controller is rebuilt on every visit while the state must outlive it. Importing
 * is a plain submission to the background executor, not a JavaFX {@code Task}, because the service reports a refusal
 * by <em>returning</em> it. Every property is touched on the FX Application Thread only; the service alone runs
 * elsewhere.
 */
@Slf4j
@Singleton
public final class ImportViewModel {

    private record Answer(Path source, ImportedBook imported) {}

    private final ProjectService projects;
    private final CurrentProject current;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<ImportState> state = new ReadOnlyObjectWrapper<>(new ImportState.Idle());
    private final ReadOnlyBooleanWrapper opening = new ReadOnlyBooleanWrapper(false);
    // What was showing when the open began, for a fault that leaves the book already open untouched. FX thread only.
    private ImportState stateBeforeOpen = new ImportState.Idle();

    /**
     * Wires the view model to the service it imports books through.
     *
     * @param projects the service a book is imported into a stored project and released through
     * @param current where the imported book is held for the screens that follow
     * @param toasts where a successful import is announced
     * @param errors where an unexpected failure is shown; a refusal the person can act on is shown in place instead
     * @param executor the daemon executor the service runs on, never the FX thread
     */
    @Inject
    public ImportViewModel(
            final ProjectService projects,
            final CurrentProject current,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.current = Objects.requireNonNull(current, "current");
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
     * Whether a book has been handed to the service and the answer has not come back.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty opening() {
        return opening.getReadOnlyProperty();
    }

    /**
     * Imports a book. Returns at once; the answer arrives on the FX thread. Ignored while another import is
     * unanswered, so two imports can never race to be the open book. FX thread only.
     *
     * @param source the file to import
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
        log.info("opening {}", fileName);
        log.trace("opening the file at {}", source);
        submit(source, fileName);
    }

    /**
     * Releases the open book and returns the screen to the drop zone. Ignored while an import is unanswered, because
     * its answer decides what is open. FX thread only.
     */
    public void cancel() {
        if (opening.get()) {
            log.debug("cancel ignored: a book is still being opened");
            return;
        }
        final OpenedBook previous = current.book().get();
        log.debug("cancel requested, a book is open: {}", previous != null);
        current.clear();
        state.set(new ImportState.Idle());
        release(previous);
    }

    private void submit(final Path source, final String fileName) {
        try {
            executor.execute(() -> importOffThread(source, fileName));
        } catch (RuntimeException rejected) {
            log.error("importing {} could not be submitted", fileName, rejected);
            publish(fileName, Result.err(internalError(rejected)));
        }
    }

    private void importOffThread(final Path source, final String fileName) {
        log.debug("importing {} on {}", fileName, Thread.currentThread().getName());
        final Result<Answer> answer = importGuarded(source);
        Platform.runLater(() -> publish(fileName, answer));
    }

    private Result<Answer> importGuarded(final Path source) {
        try {
            return answerOf(source, projects.importBook(source));
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning a result while importing", thrown);
            return Result.err(internalError(thrown));
        }
    }

    // A refusal and a malformed answer both become errors here, so the FX thread only routes by code.
    private static Result<Answer> answerOf(final Path source, final Result<ImportedBook> answer) {
        final AppError failure = answer.error();
        if (failure != null) {
            return Result.err(failure);
        }
        final ImportedBook imported = Objects.requireNonNull(answer.data(), "imported book");
        final String projectId = imported.projectId();
        if (projectId != null
                && (imported.brief() == null || imported.inspection().format() == null)) {
            log.error("project {} was answered without its brief or format", projectId);
            return Result.err(internalError(null));
        }
        return Result.ok(new Answer(source, imported));
    }

    private static AppError internalError(final @Nullable Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The book could not be opened because of an unexpected error.",
                null,
                cause);
    }

    private void publish(final String fileName, final Result<Answer> result) {
        final AppError failure = result.error();
        final Answer answer = result.data();
        final boolean stored = answer != null && answer.imported().projectId() != null;
        final boolean replacesPrevious = failure == null || failure.code() == ErrorCode.validation;
        log.debug(
                "publishing the answer for {}: failure {}, book stored {}, replaces the open book {}",
                fileName,
                failure == null ? null : failure.code(),
                stored,
                replacesPrevious);
        final OpenedBook previous = current.book().get();
        try {
            if (failure != null) {
                fail(fileName, failure, replacesPrevious);
            } else if (stored) {
                accept(fileName, Objects.requireNonNull(answer));
            } else {
                refuseByVerdict(fileName, Objects.requireNonNull(answer).imported());
            }
        } finally {
            opening.set(false);
            if (replacesPrevious) {
                release(previous);
            }
        }
    }

    private void fail(final String fileName, final AppError failure, final boolean replacesPrevious) {
        if (replacesPrevious) {
            refuse(fileName, failure);
        } else {
            keepPrevious(fileName, failure);
        }
    }

    private void accept(final String fileName, final Answer answer) {
        final ImportedBook imported = answer.imported();
        final OpenedBook opened = new OpenedBook(
                Objects.requireNonNull(imported.projectId()),
                answer.source(),
                imported.inspection(),
                imported.profile(),
                Objects.requireNonNull(imported.brief()),
                imported.narratorHint());
        final ImportState detected = ImportStates.of(fileName, imported);
        current.open(opened);
        state.set(detected);
        if (detected instanceof ImportState.Detected shown) {
            log.info(
                    "imported project {}: format {}, {} segment(s)",
                    opened.projectId(),
                    shown.card().format(),
                    imported.profile() == null ? 0 : imported.profile().stats().segments());
            log.trace(
                    "imported {} by {} from {}",
                    shown.card().title(),
                    shown.card().author(),
                    opened.source());
        }
        toasts.success(MessageKey.TOAST_BOOK_OPENED, fileName);
    }

    // The inspection refused the file before any project was made, so the verdict, not an error, names the state.
    private void refuseByVerdict(final String fileName, final ImportedBook imported) {
        current.clear();
        final ImportState refusal = ImportStates.of(fileName, imported);
        log.warn(
                "{} was refused: verdict {}, scheme {}",
                fileName,
                imported.inspection().verdict(),
                imported.inspection().encryptionScheme());
        state.set(refusal);
    }

    private void refuse(final String fileName, final AppError refusal) {
        current.clear();
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
            executor.execute(() -> closeGuarded(previous.projectId()));
            log.debug("releasing project {} submitted", previous.projectId());
        } catch (RuntimeException rejected) {
            log.warn("releasing the previous project could not be submitted", rejected);
        }
    }

    private void closeGuarded(final String projectId) {
        log.debug(
                "releasing project {} on {}", projectId, Thread.currentThread().getName());
        try {
            final Result<Boolean> released = projects.close(projectId);
            final AppError failure = released.error();
            if (failure != null) {
                log.warn("releasing project {} failed with {}", projectId, failure.code());
            } else {
                log.info("released project {}", projectId);
                log.debug("released project {}; it was open: {}", projectId, released.data());
            }
        } catch (Throwable thrown) {
            log.error("the project service threw while releasing project {}", projectId, thrown);
        }
    }
}
