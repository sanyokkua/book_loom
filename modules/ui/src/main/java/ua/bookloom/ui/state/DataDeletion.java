package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.StorageInfo;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.dialog.DeleteDataPrompt;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;

/**
 * Delete all data of Settings › Storage &amp; logs: asks first (offering a backup), stops a translation at work and
 * waits for it to end, deletes the database files and restarts, so the next launch starts on a fresh database with its
 * first-launch defaults. A delete that fails keeps the data and says which folder could not be changed. FX thread only.
 */
@Slf4j
@Singleton
public final class DataDeletion {

    private final StorageInfo storage;
    private final StorageSettings settings;
    private final ProjectOpener.RunSwitch runs;
    private final DeleteDataPrompt prompt;
    private final AppRestart restart;
    private final ErrorPresenter errors;
    private final Messages messages;
    private final OffThread calls;
    private final BooleanProperty deleting = new SimpleBooleanProperty(false);

    /**
     * Receives the collaborators the injector owns.
     *
     * @param storage deletes the stored work
     * @param settings names the database file and writes the backup the question offers
     * @param runs the window's run, stopped before anything is deleted
     * @param prompt the question asked first
     * @param restart starts BookLoom again on the fresh database
     * @param errors where a failed delete is shown
     * @param messages the catalogue the failure is worded from
     * @param executor the daemon executor the delete runs on, never the FX thread
     */
    @Inject
    public DataDeletion(
            final StorageInfo storage,
            final StorageSettings settings,
            final ProjectOpener.RunSwitch runs,
            final DeleteDataPrompt prompt,
            final AppRestart restart,
            final ErrorPresenter errors,
            final Messages messages,
            @BackgroundExecutor final ExecutorService executor) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.prompt = Objects.requireNonNull(prompt, "prompt");
        this.restart = Objects.requireNonNull(restart, "restart");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.calls = new OffThread(Objects.requireNonNull(executor, "executor"));
    }

    /** Asks whether to delete all data; Make a backup first writes the copy and then asks again. */
    public void ask() {
        if (deleting.get()) {
            log.debug("delete all data ignored: a delete is already under way");
            return;
        }
        final boolean atWork = isRunAtWork();
        log.debug("delete all data asked, a translation at work={}", atWork);
        prompt.ask(atWork, () -> settings.backup(this::ask), this::confirmed);
    }

    /** {@return whether the data is being deleted now, so the button that asks for it should not be used again} */
    public ReadOnlyBooleanProperty deletingProperty() {
        return deleting;
    }

    private boolean isRunAtWork() {
        return !runs.mirror().runState().get().isIdleOrEnded();
    }

    // The run writes its record and its decisions until it ends; deleting the file under it would fail those writes.
    private void confirmed() {
        deleting.set(true);
        if (!isRunAtWork()) {
            delete();
            return;
        }
        log.info("stopping the translation before all data is deleted");
        RunWaits.when(runs.mirror(), RunState::isIdleOrEnded, this::delete);
        runs.runner().cancel();
    }

    private void delete() {
        final @Nullable Path folder = dataFolder();
        log.info("deleting all data");
        calls.call("delete all data", storage::deleteAll, result -> {
            if (result.isErr()) {
                log.debug("all data not deleted: the data is kept and the folder is named");
                deleting.set(false);
                errors.present(failure(folder, result.error()));
                return;
            }
            restartFresh();
        });
    }

    private @Nullable Path dataFolder() {
        final Path file = settings.locationProperty().get();
        return file == null ? null : file.getParent();
    }

    private AppError failure(final @Nullable Path folder, final @Nullable AppError cause) {
        final String where = folder == null ? "" : folder.toString();
        return AppError.of(
                ErrorCode.internal,
                messages.get(MessageKey.CONFIRM_DELETE_DATA_FAILED_TITLE),
                messages.get(MessageKey.CONFIRM_DELETE_DATA_FAILED_TEXT, where),
                "storage delete",
                cause == null ? null : cause.cause());
    }

    // The data is gone and the storage refuses every later read and write, so only a new launch can go on.
    private void restartFresh() {
        log.info("all data deleted; restarting on a fresh database");
        final Result<Boolean> asked = restart.restart();
        if (asked.isOk() && Boolean.TRUE.equals(asked.data())) {
            return;
        }
        log.warn("all data deleted, but BookLoom could not restart by itself");
        errors.present(AppError.of(
                ErrorCode.internal,
                messages.get(MessageKey.CONFIRM_DELETE_DATA_NO_RESTART_TITLE),
                messages.get(MessageKey.CONFIRM_DELETE_DATA_NO_RESTART_TEXT)));
    }
}
