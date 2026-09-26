package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.ui.ScriptedModelCatalog;
import ua.bookloom.ui.ScriptedProviderVerifier;

/**
 * The settings view model's model choice: typing, the listed models, and the list following the provider.
 */
class SettingsViewModelModelTest extends SettingsViewModelTestBase {

    // IF a typed identifier were not accepted when the server lists nothing, THEN the application would be unusable
    // against exactly the servers manual entry exists for.
    @Test
    void setModelText_typedIdWhenCatalogueReturnsNothing_becomesTheChosenModel() {
        final SettingsViewModel viewModel = viewModel();
        refreshAndAwait(viewModel);

        onFx(() -> {
            viewModel.setModelText("mistral-small:24b");
            return null;
        });

        assertThat(offered(viewModel)).isEmpty();
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("mistral-small:24b");
    }

    // IF a listed model could not be chosen, THEN the list would be decoration and the check could never be enabled
    // from it.
    @Test
    void setModelText_listedModel_becomesTheChosenModel() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b", "qwen3:8b");
        final SettingsViewModel viewModel = viewModel();
        refreshAndAwait(viewModel);
        assertThat(offered(viewModel)).containsExactly("gemma3:12b", "qwen3:8b");

        onFx(() -> {
            viewModel.setModelText("gemma3:12b");
            return null;
        });

        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("gemma3:12b");
    }

    // IF the typed text were stripped as it is typed, THEN a person could not type a space inside an identifier one
    // key at a time; but IF the check kept the padding, THEN the provider would be asked about a model no server
    // knows by that name.
    @Test
    void check_modelTypedWithSurroundingSpaces_keepsTheTextButAsksAboutTheStrippedId() {
        final ScriptedProviderVerifier verifier = ScriptedProviderVerifier.returning(allPassed());
        final SettingsViewModel viewModel = viewModel(verifier);

        onFx(() -> {
            viewModel.setModelText("  mistral-small:24b \t");
            viewModel.check();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("  mistral-small:24b \t");
        assertThat(verifier.selections()).containsExactly(new ModelSelection("ollama", "mistral-small:24b"));
    }

    // IF committing the entry changed the model, THEN pressing Enter on a typed id would alter what was typed.
    @Test
    void commitModel_afterTyping_leavesTheModelAsTyped() {
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.setModelText("mistral-small:24b");
            return null;
        });

        onFx(() -> {
            viewModel.commitModel();
            return null;
        });

        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("mistral-small:24b");
    }

    // IF the check were available with no model, or stayed unavailable after one is chosen, THEN it would either
    // verify against nothing or could never be run; clearing the choice must take it away again.
    @Test
    void checkAvailable_followsWhetherAModelIsChosen() {
        final SettingsViewModel viewModel = viewModel();
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();

        onFx(() -> {
            viewModel.setModelText(MODEL);
            return null;
        });
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();

        onFx(() -> {
            viewModel.setModelText("   ");
            return null;
        });
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isFalse();
    }

    // IF a model and list from one server survived a switch to another, THEN the screen would offer identifiers that
    // mean nothing there; the old list must be gone at once, before the new server has answered, and the new
    // provider must be the one asked.
    @Test
    void selectProvider_differentProvider_clearsModelAndListAtOnceThenAsksTheNewProvider() throws InterruptedException {
        catalog = ScriptedModelCatalog.returning("gemma3:12b", "qwen3:8b");
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.refreshModels();
            return null;
        });
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {
            viewModel.setModelText("gemma3:12b");
            return null;
        });
        catalog.hold();

        onFx(() -> {
            viewModel.selectProvider("lmstudio");
            return null;
        });
        catalog.awaitCalls(2);

        assertThat(onFx(() -> viewModel.model().get())).isEmpty();
        assertThat(offered(viewModel)).isEmpty();
        assertThat(onFx(() -> viewModel.modelListing().noListObtained().get())).isFalse();
        assertThat(onFx(() -> viewModel.modelListing().listing().get())).isTrue();
        assertThat(catalog.providerIds()).containsExactly("ollama", "lmstudio");
        catalog.release();
    }

    // IF re-selecting the current provider threw its list away or asked the server again, THEN clicking the
    // already-selected row would flicker the list and repeat a request for nothing.
    @Test
    void selectProvider_sameProvider_keepsOfferedListAndDoesNotAskAgain() {
        catalog = ScriptedModelCatalog.returning("gemma3:12b");
        final SettingsViewModel viewModel = viewModel();
        refreshAndAwait(viewModel);

        onFx(() -> {
            viewModel.selectProvider("ollama");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(offered(viewModel)).containsExactly("gemma3:12b");
        assertThat(catalog.callCount()).isEqualTo(1);
    }

    // IF the listing did not report itself while unanswered, THEN a person could not tell it was underway; and IF
    // typing waited on it, THEN the typed entry would not be the escape hatch the spec makes it.
    @Test
    void refreshModels_inFlight_reportsListingAndTypingStillChoosesTheModel()
            throws InterruptedException, TimeoutException {
        catalog = ScriptedModelCatalog.gated("gemma3:12b");
        final CountingPool pool = useCountingPool();
        final SettingsViewModel viewModel = viewModel();
        onFx(() -> {
            viewModel.refreshModels();
            return null;
        });
        catalog.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        onFx(() -> {
            viewModel.setModelText("mistral-small:24b");
            return null;
        });

        assertThat(onFx(() -> viewModel.modelListing().listing().get())).isTrue();
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("mistral-small:24b");
        assertThat(onFx(() -> viewModel.checkAvailable().get())).isTrue();

        catalog.release();
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !onFx(() -> viewModel.modelListing().listing().get()));
        pool.awaitFinished(1);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(offered(viewModel)).containsExactly("gemma3:12b");
        assertThat(onFx(() -> viewModel.model().get())).isEqualTo("mistral-small:24b");
    }
}
