package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;

/** The list of models the Models tab offers: every provider's, each model once, and never read on the FX thread. */
class ModelRosterTest extends SettingsViewModelTestBase {

    private final ExecutorService io = Executors.newCachedThreadPool(task -> {
        final Thread thread = new Thread(task, "roster-io-test");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    void stopIo() {
        io.shutdownNow();
    }

    private ModelRoster roster() {
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel();
        return onFx(() -> new ModelRoster(configs, catalog, modelSettings, viewModel, pool, io));
    }

    private void refreshAndWait(final ModelRoster roster) throws Exception {
        onFx(() -> {
            roster.refresh();
            return null;
        });
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> onFx(() -> !roster.listing().get()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static List<String> shown(final ModelRoster roster) {
        return onFx(() -> roster.entries().stream()
                .map(entry -> entry.modelId() + " · " + entry.providerName())
                .toList());
    }

    private void addCustom(final String providerId, final String... names) {
        final ProviderConfig base = configs.find(providerId).orElseThrow();
        configs.save(new ProviderConfig(
                base.id(),
                base.kind(),
                base.baseUrl(),
                base.connectTimeout(),
                base.requestTimeout(),
                base.displayName(),
                base.auth(),
                base.apiKeyHeader(),
                base.credentialRef(),
                base.modelsPath(),
                base.chatPath(),
                base.headers(),
                List.of(names)));
    }

    // IF only the selected provider were read, THEN a model of the other one could never be given its own settings.
    @Test
    void refresh_twoProviders_listsEachOnesModelsWithTheProviderName() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b");
        catalog.respondFor("lmstudio", "lapa");
        final ModelRoster roster = roster();

        refreshAndWait(roster);

        assertThat(shown(roster)).containsExactly("gemma4:e4b · Ollama", "lapa · LM Studio");
    }

    // IF a model were listed twice, THEN the picker would show it twice and one copy would never be the one edited.
    @Test
    void refresh_customAndStoredModels_followTheListedOnesEachOnce() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b");
        catalog.respondFor("lmstudio");
        addCustom("ollama", "mine", "gemma4:e4b");
        modelSettings.put(ModelSettings.defaults("ollama", "old-model"));
        modelSettings.put(ModelSettings.defaults("ollama", "mine"));
        final ModelRoster roster = roster();

        refreshAndWait(roster);

        assertThat(shown(roster)).containsExactly("gemma4:e4b · Ollama", "mine · Ollama", "old-model · Ollama");
    }

    // IF one unreachable server emptied the list, THEN its custom and already-set models could not be reached.
    @Test
    void refresh_oneProviderFailsToList_stillOffersItsCustomAndStoredModelsAndTheOthers() throws Exception {
        catalog.failFor("ollama", AppError.of(ErrorCode.unreachable, "Offline", "Nothing answered."));
        catalog.respondFor("lmstudio", "lapa");
        addCustom("ollama", "mine");
        modelSettings.put(ModelSettings.defaults("ollama", "old-model"));
        final ModelRoster roster = roster();

        refreshAndWait(roster);

        assertThat(shown(roster)).containsExactly("mine · Ollama", "old-model · Ollama", "lapa · LM Studio");
    }

    // IF the stores were read where the window draws, THEN a slow disk would freeze it.
    @Test
    void refresh_always_readsTheStoreOffTheFxThread() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b");
        final ModelRoster roster = roster();

        refreshAndWait(roster);

        assertThat(modelSettings.fxThreadCalls()).isZero();
        assertThat(shown(roster)).isNotEmpty();
    }

    // IF an older reading landed last, THEN the list would show models the newer reading no longer had.
    @Test
    void refresh_olderReadingArrivesLast_isIgnored() throws Exception {
        configs.delete("lmstudio");
        catalog.respondFor("ollama", "first");
        catalog.hold();
        final ModelRoster roster = roster();
        onFx(() -> {
            roster.refresh();
            return null;
        });
        catalog.awaitEntered();
        catalog.respondFor("ollama", "second");

        refreshAndWait(roster);
        catalog.release();
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> catalog.callCount() == 2);
        WaitForAsyncUtils.waitForFxEvents();
        Thread.sleep(200);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(shown(roster)).containsExactly("second · Ollama");
        assertThat(onFx(() -> roster.listing().get())).isFalse();
    }

    // IF a reading that threw left the tab saying it is reading, THEN the person would wait for nothing.
    @Test
    void refresh_storeThrows_publishesNothingAndStopsListing() throws Exception {
        modelSettings.throwOnListing();
        final ModelRoster roster = roster();

        refreshAndWait(roster);

        assertThat(shown(roster)).isEmpty();
        assertThat(onFx(() -> roster.listing().get())).isFalse();
    }
}
