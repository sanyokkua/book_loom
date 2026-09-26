package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The settings screen's model control: the editable combo, the listing notes and the refusal line, read from the real
 * scene.
 */
class SettingsModelScreenTest extends SettingsScreenTestBase {

    // IF the model control were missing, read-only, or disabled, THEN a person could not type a model the server does
    // not list, and the check could never be enabled.
    @Test
    void modelControl_settingsScreen_isAnEditableComboThatIsNeverDisabled() {
        openSettings();

        assertThat(required("settings-model")).isInstanceOf(ComboBox.class);
        assertThat(modelCombo().isEditable()).isTrue();
        assertThat(modelCombo().isDisable()).isFalse();
    }

    // IF the screen did not ask for the list when shown, THEN the combo would stay empty until something else
    // refreshed it; and IF it asked about another provider, THEN it would list another server's models.
    @Test
    void modelControl_screenShown_asksTheCatalogueOnceForTheSelectedProvider() throws InterruptedException {
        openSettings();
        catalog.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(catalog.providerIds()).containsExactly("ollama");
    }

    // IF the offered models did not reach the control, THEN a listed model could not be picked.
    @Test
    void modelControl_catalogueListsModels_offersThemInOrder() throws TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");

        openSettings();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        assertThat(modelCombo().getItems()).containsExactly("gemma3:12b", "qwen3:8b");
    }

    // IF choosing a listed model in the control did not reach the view model, THEN the pick would change nothing.
    @Test
    void modelControl_listedModelPicked_becomesTheChosenModelAndEnablesTheCheck() throws TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");
        openSettings();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        onFx(() -> modelCombo().getSelectionModel().select("qwen3:8b"));

        assertThat(ThemeTestSupport.onFx(() -> viewModel().model().get())).isEqualTo("qwen3:8b");
        assertThat(((Button) required("settings-check")).isDisable()).isFalse();
    }

    // IF typing did not set the model as it happens, THEN the check would stay disabled: a disabled button never
    // takes the click that would move focus and commit the entry.
    @Test
    void modelControl_textTyped_setsTheModelAndEnablesTheCheck() throws TimeoutException {
        openSettingsSettled();
        assertThat(((Button) required("settings-check")).isDisable()).isTrue();

        typeIntoModelEntry("mistral-small:24b");

        assertThat(ThemeTestSupport.onFx(() -> viewModel().model().get())).isEqualTo("mistral-small:24b");
        assertThat(((Button) required("settings-check")).isDisable()).isFalse();
    }

    // IF the control did not follow the view model, THEN a model chosen elsewhere would not show in the entry.
    @Test
    void modelControl_modelChosenInViewModel_showsInTheEntry() throws TimeoutException {
        openSettingsSettled();

        onFx(() -> viewModel().setModelText("gemma3:12b"));

        assertThat(modelCombo().getEditor().getText()).isEqualTo("gemma3:12b");
    }

    // IF a provider change left the typed text in the entry, THEN the screen would show a model the view model has
    // discarded, and the check would be aimed at nothing.
    @Test
    void modelControl_providerChanged_emptiesTheEntry() throws TimeoutException {
        openSettingsSettled();
        typeIntoModelEntry("gemma3:12b");

        onFx(() -> viewModel().selectProvider("lmstudio"));

        assertThat(modelCombo().getEditor().getText()).isEmpty();
        assertThat(((Button) required("settings-check")).isDisable()).isTrue();
    }

    // IF the listing note showed or took room when nothing was being listed, THEN it would claim work that is not
    // happening; and IF it stayed after the answer, it would never go away.
    @Test
    void listingLabel_whileListing_visibleAndManaged_thenHidden() throws InterruptedException, TimeoutException {
        openSettings();
        awaitFx(() -> !listingLabel().isVisible());
        assertThat(listingLabel().isManaged()).isFalse();
        catalog.hold();

        onFx(() -> viewModel().refreshModels());
        catalog.awaitCalls(2);
        awaitFx(() -> listingLabel().isVisible());

        assertThat(listingLabel().isManaged()).isTrue();
        assertThat(listingLabel().getText()).isNotBlank();

        catalog.release();
        awaitFx(() -> !listingLabel().isVisible());

        assertThat(listingLabel().isManaged()).isFalse();
    }

    // IF an unreadable listing were not said in place, THEN an empty combo would read as the whole truth; and IF the
    // note took room while hidden, it would leave a gap.
    @Test
    void noListLabel_listingFailed_visibleAndManagedAndEntryStaysUsable() throws TimeoutException {
        catalog.respondWith(AppError.of(ErrorCode.discoveryFailed, "Discovery failed", "The list is unreadable."));

        openSettings();
        awaitFx(() -> noListLabel().isVisible());

        assertThat(noListLabel().isManaged()).isTrue();
        assertThat(noListLabel().getText()).isNotBlank();
        assertThat(modelCombo().isDisable()).isFalse();
    }

    // IF a server that lists nothing gave no note, THEN an empty choice would be presented as the whole truth.
    @Test
    void noListLabel_emptyListing_visible() throws TimeoutException {
        openSettings();

        awaitFx(() -> noListLabel().isVisible());

        assertThat(noListLabel().isManaged()).isTrue();
    }

    // IF the note stayed when models arrived, THEN the screen would say no list was obtained beside a populated one.
    @Test
    void noListLabel_modelsListed_hiddenAndNotManaged() throws TimeoutException {
        catalog.respondWith("gemma3:12b");

        openSettings();
        awaitFx(() -> modelCombo().getItems().size() == 1);

        assertThat(noListLabel().isVisible()).isFalse();
        assertThat(noListLabel().isManaged()).isFalse();
    }

    // IF a list arriving replaced or blanked what a person had typed but not yet committed, THEN their entry would be
    // lost the moment the server answered.
    @Test
    void modelControl_typedNotCommitted_thenListArrives_keepsTextAndModel()
            throws InterruptedException, TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");
        catalog.hold();
        openSettings();
        catalog.awaitEntered();
        typeIntoModelEntry("mistral-small:24b");

        catalog.release();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        assertThat(modelCombo().getEditor().getText()).isEqualTo("mistral-small:24b");
        assertThat(ThemeTestSupport.onFx(() -> viewModel().model().get())).isEqualTo("mistral-small:24b");
        assertThat(((Button) required("settings-check")).isDisable()).isFalse();
    }

    // IF a list that does not contain a committed identifier replaced it in the entry, THEN a person who named a model
    // the server does not list would lose it as soon as the listing answered.
    @Test
    void modelControl_committedThenListWithoutThatIdArrives_keepsTheText()
            throws InterruptedException, TimeoutException {
        catalog.respondWith("gemma3:12b", "qwen3:8b");
        catalog.hold();
        openSettings();
        catalog.awaitEntered();
        typeIntoModelEntry("mistral-small:24b");
        commitModelEntry();

        catalog.release();
        awaitFx(() -> modelCombo().getItems().size() == 2);

        assertThat(modelCombo().getEditor().getText()).isEqualTo("mistral-small:24b");
        assertThat(ThemeTestSupport.onFx(() -> viewModel().model().get())).isEqualTo("mistral-small:24b");
    }

    // IF a refusal a person can fix were not said beside the control, THEN they would get no reason for an empty list;
    // and IF it showed when there was none, THEN it would be an empty line.
    @Test
    void refusalLabel_validationRefusal_showsTheMessageInPlace() throws TimeoutException {
        openSettingsSettled();
        assertThat(refusalLabel().isVisible()).isFalse();
        assertThat(refusalLabel().isManaged()).isFalse();
        catalog.respondWith(AppError.of(ErrorCode.validation, "Unknown provider", "No such provider is registered."));

        onFx(() -> viewModel().refreshModels());
        awaitFx(() -> refusalLabel().isVisible());

        assertThat(refusalLabel().isManaged()).isTrue();
        assertThat(refusalLabel().getText()).isEqualTo("No such provider is registered.");
    }
}
