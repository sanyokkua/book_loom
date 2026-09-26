package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelCatalog;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * The models the selected provider offers, and what the screen should say about how obtaining them went.
 *
 * <p>A singleton, like {@link SettingsViewModel} that owns it, because the settings screen is rebuilt on every visit
 * while a list already fetched must survive it. The listing is a plain submission to the background executor, not a
 * JavaFX {@code Task}: the catalogue reports failure by <em>returning</em> it, so {@code Task.setOnFailed} would never
 * fire (D12 of the workspace design). Every property changes on the FX Application Thread only.
 *
 * <p>Typing a model name never depends on this list, so a failure is said in place beside the entry rather than
 * blocking it; only an unexpected refusal ({@code internal}, {@code busy}) opens the error dialog.
 */
@Slf4j
@Singleton
public final class ModelListing {

    private final ModelCatalog catalog;
    private final Toasts toasts;
    private final ErrorPresenter errors;
    private final ExecutorService executor;
    private final ObservableList<String> offered = FXCollections.observableArrayList();
    private final ObservableList<String> offeredView = FXCollections.unmodifiableObservableList(offered);
    private final ReadOnlyBooleanWrapper listing = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper noListObtained = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper refusal = new ReadOnlyStringWrapper("");

    /**
     * Counts every event that makes an answer in flight meaningless: a listing starting, or the listing being
     * discarded. An answer is published only if the count is what it was when its listing began; FX thread only.
     */
    private long epoch;

    private @Nullable String inFlightProvider;

    /**
     * Creates a listing that has asked for nothing yet.
     *
     * @param catalog the port the provider's models are read through
     * @param toasts where an unreadable list is announced
     * @param errors where an unexpected refusal is shown
     * @param executor the daemon executor the catalogue is asked on, never the FX thread
     */
    @Inject
    public ModelListing(
            final ModelCatalog catalog,
            final Toasts toasts,
            final ErrorPresenter errors,
            @BackgroundExecutor final ExecutorService executor) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.toasts = Objects.requireNonNull(toasts, "toasts");
        this.errors = Objects.requireNonNull(errors, "errors");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * The model ids the provider last reported, in its own order.
     *
     * @return a read-only list; a listing that fails leaves it as it was, only {@link #discard()} empties it
     */
    public ObservableList<String> offered() {
        return offeredView;
    }

    /**
     * Whether a listing has been asked for and its answer has not arrived.
     *
     * @return a read-only property
     */
    public ReadOnlyBooleanProperty listing() {
        return listing.getReadOnlyProperty();
    }

    /**
     * Whether the last answer gave the person no list to choose from, so an empty choice is not read as the whole
     * truth.
     *
     * @return a read-only property, {@code true} after an empty answer or a failure that is said in place
     */
    public ReadOnlyBooleanProperty noListObtained() {
        return noListObtained.getReadOnlyProperty();
    }

    /**
     * Why the last listing was refused as a whole for a reason the person can fix, e.g. an unknown provider.
     *
     * @return a read-only property holding the error's own message, empty when there is none
     */
    public ReadOnlyStringProperty refusal() {
        return refusal.getReadOnlyProperty();
    }

    /**
     * Asks the provider for its models. Returns at once; the answer arrives on the FX thread. Does nothing while a
     * listing of the same provider is already in flight, so revisiting the screen cannot stack requests on a slow
     * server. Never clears {@link #offered()}: the previous list stays usable until a new one replaces it. FX thread
     * only.
     *
     * @param providerId the id of the provider to ask; the port refuses one nobody registered
     */
    public void refresh(final String providerId) {
        Objects.requireNonNull(providerId, "providerId");
        log.debug("model listing requested for provider '{}'", providerId);
        if (listing.get() && providerId.equals(inFlightProvider)) {
            log.debug("listing of '{}' already in flight, not asking again", providerId);
            return;
        }
        epoch++;
        inFlightProvider = providerId;
        listing.set(true);
        submit(providerId, epoch);
    }

    /**
     * Forgets everything about the provider that was listed: the ids, the notes and any answer still in flight,
     * which will be dropped when it arrives. FX thread only.
     */
    public void discard() {
        log.debug("model listing discarded, {} id(s) forgotten", offered.size());
        epoch++;
        inFlightProvider = null;
        offered.clear();
        noListObtained.set(false);
        refusal.set("");
        listing.set(false);
    }

    private void submit(final String providerId, final long requested) {
        try {
            executor.execute(() -> listOffThread(providerId, requested));
        } catch (RuntimeException rejected) {
            log.error("the model listing could not be submitted", rejected);
            publish(requested, Result.err(internalError(rejected)));
        }
    }

    private void listOffThread(final String providerId, final long requested) {
        log.debug(
                "listing models of '{}' on {}",
                providerId,
                Thread.currentThread().getName());
        final Result<List<ModelInfo>> result = listGuarded(providerId);
        Platform.runLater(() -> publish(requested, result));
    }

    private Result<List<ModelInfo>> listGuarded(final String providerId) {
        try {
            return catalog.listModels(providerId);
        } catch (Throwable thrown) {
            log.error("the model catalogue threw instead of returning a result", thrown);
            return Result.err(internalError(thrown));
        }
    }

    private static AppError internalError(final Throwable cause) {
        return AppError.of(
                ErrorCode.internal,
                "Unexpected error",
                "The model list stopped because of an unexpected error.",
                null,
                cause);
    }

    private void publish(final long requested, final Result<List<ModelInfo>> result) {
        if (requested != epoch) {
            log.debug("answer of listing {} discarded: the current one is {}", requested, epoch);
            return;
        }
        listing.set(false);
        inFlightProvider = null;
        final AppError failure = result.error();
        if (failure == null) {
            accept(Objects.requireNonNull(result.data()));
        } else {
            route(failure);
        }
    }

    private void accept(final List<ModelInfo> models) {
        log.debug("{} model(s) listed", models.size());
        refusal.set("");
        if (models.isEmpty()) {
            log.debug("the provider lists no models: saying so in place");
            noListObtained.set(true);
        } else {
            offered.setAll(models.stream().map(ModelInfo::id).toList());
            noListObtained.set(false);
        }
    }

    private void route(final AppError failure) {
        log.debug("the listing failed with {}", failure.code());
        switch (failure.code()) {
            case cancelled -> log.debug("the listing was cancelled: nothing is said");
            case internal, busy -> errors.present(failure);
            case validation -> {
                noListObtained.set(false);
                refusal.set(failure.message());
            }
            default -> {
                noListObtained.set(true);
                refusal.set("");
                toasts.warning(MessageKey.TOAST_MODEL_LIST_UNREADABLE);
            }
        }
    }
}
