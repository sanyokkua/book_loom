package ua.bookloom.ui.state;

import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.ui.EnglishMessages;
import ua.bookloom.ui.ScriptedModelCapabilities;

/** What the Models form tests share: the form over fakes, and the helpers that select, type, save and wait. */
@SuppressWarnings("NullAway.Init")
abstract class ModelFormTestBase extends SettingsViewModelTestBase {

    static final ModelEntry GEMMA = new ModelEntry("ollama", "Ollama", ProviderKind.OLLAMA, "gemma4:e4b");
    static final ModelEntry GPT_OSS = new ModelEntry("ollama", "Ollama", ProviderKind.OLLAMA, "gpt-oss");

    final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();
    final StructuredAnswers structured = new StructuredAnswers();
    CountingPool pool;
    ModelRoster roster;
    ModelForm form;
    SettingsViewModel viewModel;
    private final ExecutorService io = Executors.newCachedThreadPool(task -> {
        final Thread thread = new Thread(task, "model-form-io-test");
        thread.setDaemon(true);
        return thread;
    });

    @AfterEach
    final void stopIo() {
        io.shutdownNow();
    }

    /** Builds the form over the fakes as they are scripted now; call it after scripting. */
    final void buildForm() {
        pool = useCountingPool();
        viewModel = viewModel();
        roster = onFx(() -> new ModelRoster(configs, catalog, modelSettings, viewModel, pool, io));
        form = onFx(() -> new ModelForm(
                modelSettings, capabilities, structured, roster, viewModel, EnglishMessages.create(), pool, io));
    }

    final void await(final BooleanSupplier condition) throws Exception {
        WaitForAsyncUtils.waitFor(WAIT_SECONDS, TimeUnit.SECONDS, () -> onFx(condition::getAsBoolean));
        WaitForAsyncUtils.waitForFxEvents();
    }

    final void select(final ModelEntry entry) throws Exception {
        onFx(() -> {
            form.select(entry);
            return null;
        });
        await(() -> !form.facts().get().loading());
    }

    /** Reads the roster again and waits until it has published. */
    final void refreshRoster() throws Exception {
        onFx(() -> {
            roster.refresh();
            return null;
        });
        await(() -> !roster.listing().get());
    }

    final void edit(final UnaryOperator<ModelDraft> change) {
        onFx(() -> {
            form.edit(change);
            return null;
        });
    }

    /** Presses Save and waits until it is over; true when the follow-up ran. */
    final boolean save() throws Exception {
        final List<String> ran = new ArrayList<>();
        onFx(() -> {
            form.save(() -> ran.add("then"));
            return null;
        });
        await(() -> !form.saving().get());
        return !ran.isEmpty();
    }

    final void sendAgain(final String name) throws Exception {
        onFx(() -> {
            form.sendAgain(name);
            return null;
        });
        await(() -> !form.sendingAgain().get());
    }

    final ModelDraft draft() {
        return onFx(() -> form.draft().get());
    }

    final boolean dirty() {
        return onFx(() -> form.dirty().get());
    }

    final ModelSettings stored(final ModelEntry entry) {
        return modelSettings.stored(entry.providerId(), entry.modelId()).orElseThrow();
    }

    static RefusedSetting temperatureRefusal() {
        return new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "omitted");
    }

    /** Stores a record that holds the refusal, the way the chat layer leaves it after a refused request. */
    final void refuse(final ModelEntry entry, final RefusedSetting refusal) {
        modelSettings.update(
                entry.providerId(),
                entry.modelId(),
                s -> new ModelSettings(
                        s.providerId(),
                        s.modelId(),
                        s.temperatureChoice(),
                        s.temperature(),
                        s.contextWindow(),
                        s.maxOutput(),
                        s.outputField(),
                        List.of(refusal),
                        s.measuredSecondsPerSegment(),
                        s.measuredFrom()));
    }
}
