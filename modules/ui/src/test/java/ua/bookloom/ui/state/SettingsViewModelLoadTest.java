package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.MemorySettings;
import ua.bookloom.ui.ScriptedProviderVerifier;

/** The registry is read on the background executor, never in the constructor on the FX thread. */
class SettingsViewModelLoadTest extends SettingsViewModelTestBase {

    // IF the constructor listed providers, THEN the SQLite adapter would be read on the FX thread while the injector
    // builds.
    @Test
    void constructor_realExecutor_readsRegistryOffTheFxThreadAndThenFillsTheList() throws InterruptedException {
        final ThreadRecordingConfigs configs = new ThreadRecordingConfigs();
        final CountingPool pool = useCountingPool();

        final SettingsViewModel viewModel = onFx(() -> new SettingsViewModel(
                configs,
                ScriptedProviderVerifier.idle(),
                newModelListing(),
                errors,
                executor,
                activities,
                new MemorySettings()));
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(configs.readOnFx).isEmpty();
        assertThat(configs.reads).hasSize(1);
        assertThat(onFx(() -> viewModel.providers().size())).isEqualTo(2);
        assertThat(onFx(() -> viewModel.selectedProviderId().get())).isEqualTo("ollama");
    }

    /** Records whether each {@code all()} call came from the FX thread. */
    private static final class ThreadRecordingConfigs implements ProviderConfigs {

        private final FakeProviderConfigs delegate = new FakeProviderConfigs();

        final List<String> reads = new CopyOnWriteArrayList<>();
        final List<String> readOnFx = new CopyOnWriteArrayList<>();

        @Override
        public List<ProviderConfig> all() {
            reads.add(Thread.currentThread().getName());
            if (Platform.isFxApplicationThread()) {
                readOnFx.add(Thread.currentThread().getName());
            }
            return delegate.all();
        }

        @Override
        public Result<ProviderConfig> save(final ProviderConfig config) {
            return delegate.save(config);
        }

        @Override
        public Optional<ProviderConfig> find(final String id) {
            return delegate.find(id);
        }

        @Override
        public Result<Boolean> delete(final String id) {
            return delegate.delete(id);
        }
    }
}
