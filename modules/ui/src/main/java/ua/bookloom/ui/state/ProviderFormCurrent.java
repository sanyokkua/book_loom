package ua.bookloom.ui.state;

import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;

/**
 * What the Providers form does that concerns the current provider: deleting one, and making one current. Both call
 * storage off the FX thread and come back on it.
 */
@Slf4j
final class ProviderFormCurrent {

    private final SettingsViewModel viewModel;
    private final ProviderStorage storage;
    private final ExecutorService executor;
    private final ProviderFormFlags flags;

    ProviderFormCurrent(
            final SettingsViewModel viewModel,
            final ProviderStorage storage,
            final ExecutorService executor,
            final ProviderFormFlags flags) {
        this.viewModel = viewModel;
        this.storage = storage;
        this.executor = executor;
        this.flags = flags;
    }

    void delete(final String id, final Runnable then) {
        final boolean current = id.equals(viewModel.selectedProviderId().get());
        log.info("deleting provider '{}', current {}", id, current);
        flags.saving.set(true);
        executor.execute(() -> {
            final boolean removed = storage.remove(id, current);
            onFx(() -> deleted(removed, current, then));
        });
    }

    private void deleted(final boolean removed, final boolean current, final Runnable then) {
        if (!removed) {
            flags.failed("a provider could not be deleted");
            viewModel.reloadProviders();
            return;
        }
        if (current) {
            viewModel.forgetCurrent();
        }
        viewModel.reloadProviders(() -> {
            flags.saving.set(false);
            then.run();
        });
    }

    /**
     * Makes a provider and model the current ones.
     *
     * @param chosen the provider and the model, stripped; a blank model is kept as blank
     * @param settled what the form does once the choice is stored, before the view model adopts it
     * @param then what to do afterwards; not run when storage failed
     */
    void use(final ModelSelection chosen, final Runnable settled, final Runnable then) {
        flags.saving.set(true);
        flags.saveFailed.set(false);
        executor.execute(() -> {
            final Result<Boolean> written = storage.makeCurrent(chosen);
            onFx(() -> used(written, chosen, settled, then));
        });
    }

    private void used(
            final Result<Boolean> written, final ModelSelection chosen, final Runnable settled, final Runnable then) {
        if (written.isErr()) {
            flags.failed("provider '" + chosen.providerId() + "' could not be made current");
            return;
        }
        log.info("provider '{}' is now the current one", chosen.providerId());
        settled.run();
        viewModel.adopt(chosen);
        flags.saving.set(false);
        then.run();
    }

    private static void onFx(final Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
