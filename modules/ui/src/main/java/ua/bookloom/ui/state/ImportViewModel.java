package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import ua.bookloom.api.pipeline.ProjectSummary;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.ToastOccasion;
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

    private final ProjectService projects;
    private final CurrentProject current;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ActivityTracker activities;
    private final ProjectDisposal disposal;
    private ActivityTracker.@Nullable Handle openingActivity;
    private final ReadOnlyObjectWrapper<ImportState> state = new ReadOnlyObjectWrapper<>(new ImportState.Idle());
    private final ReadOnlyBooleanWrapper opening = new ReadOnlyBooleanWrapper(false);
    // What was showing when the open began, for a fault that leaves the book already open untouched. FX thread only.
    private ImportState stateBeforeOpen = new ImportState.Idle();
    // Counts opens and cancels, so an answer that arrives after a cancel can tell it is no longer wanted.
    private int openSerial;

    /**
     * Wires the view model to the service it imports books through.
     *
     * @param projects the service a book is imported into a stored project and released through
     * @param current where the imported book is held for the screens that follow
     * @param toasts where a successful import is announced
     * @param errors where an unexpected failure is shown; a refusal the person can act on is shown in place instead
     * @param executor the daemon executor the service runs on, never the FX thread
     * @param activities where the opening is registered, so the window waits for it
     */
    @Inject
    public ImportViewModel(
            final ProjectService projects,
            final CurrentProject current,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor,
            final ActivityTracker activities) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.current = Objects.requireNonNull(current, "current");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.activities = Objects.requireNonNull(activities, "activities");
        this.disposal = new ProjectDisposal(projects, executor);
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
     * unanswered, so two imports can never race to be the open book. Never removes a stored project. FX thread only.
     *
     * @param source the file to import
     */
    public void open(final Path source) {
        Objects.requireNonNull(source, "source");
        begin(source, null);
    }

    /**
     * Removes a stored project, then imports a book: the one destructive path, taken only when the person chose
     * "Replace and delete this translation". Returns at once. FX thread only.
     *
     * @param source the file to import
     * @param projectId the stored project to delete first; it is no longer the open book when this returns
     */
    public void openReplacing(final Path source, final String projectId) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(projectId, "projectId");
        begin(source, projectId);
    }

    private void begin(final Path source, final @Nullable String replacedId) {
        log.debug("open requested for {}, replacing project {}", source, replacedId);
        if (opening.get()) {
            log.debug("open of {} ignored: a book is still being opened", source);
            return;
        }
        final String fileName = fileNameOf(source);
        stateBeforeOpen = state.get();
        final OpenedBook open = current.book().get();
        if (replacedId != null && open != null && open.projectId().equals(replacedId)) {
            log.debug("project {} is deleted by the replacement: it stops being the open book", replacedId);
            current.clear();
        }
        opening.set(true);
        openSerial++;
        final ActivityTracker.Handle registered = activities.begin(ActivityKind.IMPORT, null);
        registered.indeterminate(fileName);
        openingActivity = registered;
        state.set(new ImportState.Opening(fileName));
        log.info("opening {}", fileName);
        log.trace("opening the file at {}", source);
        submit(source, fileName, replacedId, openSerial);
    }

    /**
     * Shows that a chosen file is already stored as projects, without importing anything. FX thread only.
     *
     * @param source the chosen file
     * @param found the stored projects holding its content, in list order; at least one
     */
    void showAlready(final Path source, final List<ProjectSummary> found) {
        final List<String> names =
                found.stream().map(summary -> summary.project().name()).toList();
        log.debug("{} is already stored as {} project(s)", fileNameOf(source), names.size());
        state.set(new ImportState.AlreadyInProjects(
                fileNameOf(source), source, names, found.get(0).project().title()));
    }

    /**
     * Releases the open book and returns the screen to the drop zone. While an import is unanswered it instead
     * abandons that import: the screen goes idle at once, the book that was open stays open, and the project the
     * import goes on to create is removed and its answer ignored. FX thread only.
     */
    public void cancel() {
        if (opening.get()) {
            log.info("the opening of a book was cancelled before it was answered");
            openSerial++;
            opening.set(false);
            endActivity();
            state.set(new ImportState.Idle());
            return;
        }
        final OpenedBook previous = current.book().get();
        log.debug("cancel requested, a book is open: {}", previous != null);
        current.clear();
        state.set(new ImportState.Idle());
        disposal.release(previous);
    }

    private void submit(final Path source, final String fileName, final @Nullable String replacedId, final int serial) {
        try {
            executor.execute(() -> importOffThread(source, fileName, replacedId, serial));
        } catch (RuntimeException rejected) {
            log.error("importing {} could not be submitted", fileName, rejected);
            publish(fileName, Result.err(ImportAnswers.internalError(rejected)));
        }
    }

    private void importOffThread(
            final Path source, final String fileName, final @Nullable String replacedId, final int serial) {
        log.debug(
                "importing {} on {}, replacing project {}",
                fileName,
                Thread.currentThread().getName(),
                replacedId);
        final Set<String> before = disposal.storedIds();
        final Result<ImportAnswers.Answer> answer = importGuarded(source, replacedId);
        Platform.runLater(() -> deliver(fileName, answer, serial, before));
    }

    private Result<ImportAnswers.Answer> importGuarded(final Path source, final @Nullable String replacedId) {
        try {
            if (replacedId != null) {
                disposal.remove(replacedId);
            }
            return ImportAnswers.answerOf(source, projects.create(source));
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning a result while importing", thrown);
            return Result.err(ImportAnswers.internalError(thrown));
        }
    }

    // An answer that arrives after a cancel is no longer wanted: the cancel advanced the serial.
    private void deliver(
            final String fileName,
            final Result<ImportAnswers.Answer> result,
            final int serial,
            final @Nullable Set<String> before) {
        if (serial == openSerial) {
            publish(fileName, result);
        } else {
            discardCancelled(fileName, result, before);
        }
    }

    private void publish(final String fileName, final Result<ImportAnswers.Answer> result) {
        final AppError failure = result.error();
        final ImportAnswers.Answer answer = result.data();
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
            endActivity();
            if (replacesPrevious) {
                disposal.release(previous);
            }
        }
    }

    // The person cancelled while this import ran: a project it newly made is deleted and nothing is shown. A project
    // that was stored before (the import reused it) is never removed.
    private void discardCancelled(
            final String fileName, final Result<ImportAnswers.Answer> result, final @Nullable Set<String> before) {
        final ImportAnswers.Answer answer = result.data();
        final String created = answer == null ? null : answer.imported().projectId();
        log.info("{} finished after it was cancelled: its answer is ignored, project created {}", fileName, created);
        if (created == null) {
            return;
        }
        if (before == null || before.contains(created)) {
            log.warn("project {} was stored before the cancelled import: it is kept", created);
            return;
        }
        disposal.removeLater(created);
    }

    private void endActivity() {
        final ActivityTracker.Handle registered = openingActivity;
        openingActivity = null;
        if (registered != null) {
            registered.end();
        }
    }

    private void fail(final String fileName, final AppError failure, final boolean replacesPrevious) {
        if (replacesPrevious) {
            refuse(fileName, failure);
        } else {
            keepPrevious(fileName, failure);
        }
    }

    private void accept(final String fileName, final ImportAnswers.Answer answer) {
        final ImportedBook imported = answer.imported();
        final OpenedBook opened = openedOf(answer.source(), imported);
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
        toasts.raise(ToastOccasion.BOOK_OPENED, fileName);
    }

    private static OpenedBook openedOf(final Path source, final ImportedBook imported) {
        return new OpenedBook(
                Objects.requireNonNull(imported.projectId()),
                source,
                imported.inspection(),
                imported.profile(),
                Objects.requireNonNull(imported.brief()),
                imported.narratorHint());
    }

    /**
     * Makes a stored project's book the open one without importing anything, as the Projects screen does for
     * Continue. The project that was open is released unless it is the same one. FX thread only.
     *
     * @param source the project's book file
     * @param imported what opening the stored project answered; it must carry a project id and a brief
     */
    void adopt(final Path source, final ImportedBook imported) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(imported, "imported");
        final OpenedBook opened = openedOf(source, imported);
        final OpenedBook previous = current.book().get();
        log.debug(
                "adopting stored project {}, previous project {}",
                opened.projectId(),
                previous == null ? null : previous.projectId());
        current.open(opened);
        state.set(ImportStates.of(fileNameOf(source), imported));
        log.info("adopted stored project {}", opened.projectId());
        if (previous != null && !previous.projectId().equals(opened.projectId())) {
            disposal.release(previous);
        }
    }

    /**
     * Shows the drop zone again without closing the open project, so "New translation" starts from an empty screen
     * while the person can still go back. Ignored while an import is unanswered. FX thread only.
     */
    public void showEmpty() {
        if (opening.get()) {
            log.debug("showEmpty ignored: a book is still being opened");
            return;
        }
        log.debug(
                "showing the empty import screen, a project is open: {}",
                current.book().get() != null);
        state.set(new ImportState.Idle());
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
}
