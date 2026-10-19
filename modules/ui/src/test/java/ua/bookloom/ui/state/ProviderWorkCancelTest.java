package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.ScriptedProviderVerifier;

/**
 * A model listing and a provider test register while they are in flight and are cancelled — their request never made
 * or interrupted, their registration ended — when the provider or the model changes or the title bar's Stop is pressed.
 */
class ProviderWorkCancelTest extends SettingsViewModelTestBase {

    private final QueuedExecutor queued = new QueuedExecutor();

    private List<ActivityKind> running() {
        return onFx(() -> activities.running().stream().map(Activity::kind).toList());
    }

    private void fx(final Runnable action) {
        onFx(() -> {
            action.run();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a discarded listing kept its request, THEN another provider's list could arrive after the switch.
    @Test
    void listing_discardedWhileInFlight_isCancelledAndUnregistered() {
        executor = queued;
        final ModelListing listing = newModelListing();
        fx(() -> listing.refresh("ollama"));
        final List<ActivityKind> during = running();

        fx(listing::discard);
        queued.runAll();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(during).containsExactly(ActivityKind.MODEL_LISTING);
        assertThat(running()).isEmpty();
        assertThat(catalog.providerIds()).isEmpty();
    }

    // IF the title bar's Stop did not reach the listing, THEN it would offer a stop that does nothing.
    @Test
    void listing_stoppedFromTheTitleBar_isCancelledAndNoLongerListing() {
        executor = queued;
        final ModelListing listing = newModelListing();
        fx(() -> listing.refresh("ollama"));
        final long id = onFx(() -> activities.running().getFirst().id());

        fx(() -> activities.stop(id));
        queued.runAll();

        assertThat(running()).isEmpty();
        assertThat(onFx(() -> listing.listing().get())).isFalse();
        assertThat(catalog.providerIds()).isEmpty();
    }

    // IF a model change left the inference test running, THEN its answer would be read as the new model's.
    @Test
    void inferenceTest_modelChangedWhileInFlight_isCancelledAndUnregistered() {
        executor = queued;
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.idle();
        final SettingsViewModel viewModel = onFx(() -> new SettingsViewModel(
                new FakeProviderConfigs(), verifier, newModelListing(), toasts, errors, executor, activities));
        fx(() -> {
            viewModel.model().set(MODEL);
            viewModel.tests().run(ProviderTest.INFERENCE);
        });
        final List<ActivityKind> during = running();

        fx(() -> viewModel.model().set("qwen3:8b"));
        queued.runAll();

        assertThat(during).contains(ActivityKind.PROVIDER_INFERENCE_TEST);
        assertThat(running()).doesNotContain(ActivityKind.PROVIDER_INFERENCE_TEST);
        assertThat(verifier.selections()).isEmpty();
        assertThat(onFx(() -> viewModel.tests().checking().get())).isFalse();
    }
}
