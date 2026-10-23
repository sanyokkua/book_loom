package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.event.ActionEvent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.ActivityTracker;
import ua.bookloom.ui.state.ProviderDraft;

/** The Providers form as the window builds it: its fields, its footer, saving, adding and deleting. */
class SettingsProviderFormScreenTest extends SettingsScreenTestBase {

    @SuppressWarnings("unchecked")
    private ComboBox<ProviderKind> kindCombo() {
        return (ComboBox<ProviderKind>) required("settings-form-kind");
    }

    private ToggleSwitch toggle(final String id) {
        return (ToggleSwitch) required(id);
    }

    private void startAddingOfficeLm() {
        onFx(() -> button("settings-provider-add").fire());
        onFx(() -> kindCombo().setValue(ProviderKind.OPENAI_COMPATIBLE));
        setField("settings-form-name", "Office LM");
        setField("settings-form-address", "http://10.0.0.5:1234/v1");
    }

    // IF a field showed another provider's value, THEN Save would write the wrong thing over the stored provider.
    @Test
    void form_currentProviderOpened_showsNameKindAddressAndTimeouts() {
        openSettings();

        assertThat(field("settings-form-name").getText()).isEqualTo("Ollama");
        assertThat(ThemeTestSupport.onFx(() -> kindCombo().getValue())).isEqualTo(ProviderKind.OLLAMA);
        assertThat(field("settings-form-address").getText()).isEqualTo("http://localhost:11434");
        assertThat(field("settings-form-connect-timeout").getText()).isEqualTo("10");
        assertThat(field("settings-form-request-timeout").getText()).isEqualTo("180");
    }

    // IF the footer cried "unsaved" on a clean form, or Save worked with nothing to save, THEN the footer would say
    // nothing true.
    @Test
    void footer_cleanForm_hasNoDirtyMarkAndSaveAndCancelAreOff() {
        openSettings();

        assertThat(required("settings-form-dirty").isVisible()).isFalse();
        assertThat(button("settings-form-save").isDisable()).isTrue();
        assertThat(button("settings-form-cancel").isDisable()).isTrue();
    }

    // IF typing did not mark the form, THEN leaving would lose it without a word.
    @Test
    void footer_afterTyping_showsTheDirtyMarkAndOffersSaveAndCancel() {
        openSettings();

        setField("settings-form-request-timeout", "240");

        assertThat(text("settings-form-dirty")).isEqualTo("● Unsaved changes");
        assertThat(button("settings-form-save").isDisable()).isFalse();
        assertThat(button("settings-form-cancel").isDisable()).isFalse();
        assertThat(configs.find("ollama").orElseThrow().requestTimeout()).isEqualTo(Duration.ofMinutes(3));
    }

    // IF Cancel left the typing in the field, THEN the form would show something that is not stored.
    @Test
    void cancel_afterTyping_restoresTheStoredText() {
        openSettings();
        setField("settings-form-request-timeout", "240");

        onFx(() -> button("settings-form-cancel").fire());

        assertThat(field("settings-form-request-timeout").getText()).isEqualTo("180");
        assertThat(required("settings-form-dirty").isVisible()).isFalse();
    }

    // IF Save did not reach the registry, or left the form dirty, THEN the change would be lost on the next visit.
    @Test
    void save_timeoutChanged_isStoredOnceAndTheFormGoesClean() throws TimeoutException {
        openSettings();
        setField("settings-form-request-timeout", "240");

        save();

        final ProviderConfig stored = configs.find("ollama").orElseThrow();
        assertThat(stored.requestTimeout()).isEqualTo(Duration.ofMinutes(4));
        assertThat(configs.saves()).isEqualTo(1);
        assertThat(required("settings-form-dirty").isVisible()).isFalse();
        assertThat(button("settings-form-save").isDisable()).isTrue();
        assertThat(configs.fxThreadCalls()).isZero();
    }

    // IF a header whose value looks like a key were stored, THEN a secret would sit in the database; the message names
    // the header and never repeats the value.
    @Test
    void save_headerLooksLikeAKey_isRefusedWithTheHeaderNamedAndNothingStored() throws TimeoutException {
        openSettings();
        onFx(() -> toggle("settings-form-headers-on").setSelected(true));
        onFx(() -> button("settings-form-header-add").fire());
        setField("settings-form-header-name-0", "X-Api-Key");
        setField("settings-form-header-value-0", "sk-1");

        save();

        final String shown = text("settings-form-errors-text");
        assertThat(shown).contains("X-Api-Key").contains("looks like a key").doesNotContain("sk-1");
        assertThat(fold().isExpanded()).isTrue();
        assertThat(configs.saves()).isZero();
    }

    // IF a header written as an environment reference were refused, THEN the safe way to send a key would not work.
    @Test
    void save_headerValueIsAnEnvironmentReference_isStored() throws TimeoutException {
        openSettings();
        onFx(() -> toggle("settings-form-headers-on").setSelected(true));
        onFx(() -> button("settings-form-header-add").fire());
        setField("settings-form-header-name-0", "X-Api-Key");
        setField("settings-form-header-value-0", "${env:OFFICE_KEY}");

        save();

        assertThat(configs.find("ollama").orElseThrow().headers()).containsEntry("X-Api-Key", "${env:OFFICE_KEY}");
    }

    // IF "New provider" did not open a blank, open form, THEN a person could not tell where to start.
    @Test
    void add_pressed_opensABlankFormWithTheDetailsOpenAndTheLocalAddress() {
        openSettings();

        onFx(() -> button("settings-provider-add").fire());

        assertThat(text("settings-form-title")).isEqualTo("New provider");
        assertThat(field("settings-form-name").getText()).isEmpty();
        assertThat(field("settings-form-address").getText()).isEqualTo("http://localhost:11434");
        assertThat(fold().isExpanded()).isTrue();
        assertThat(required("settings-summary").isVisible()).isFalse();
        assertThat(button("settings-provider-delete").isDisable()).isTrue();
    }

    // IF a typed provider did not reach the list, THEN adding would do nothing a person could see.
    @Test
    void add_officeLmTypedAndSaved_isStoredAndListedWithItsHost() throws TimeoutException {
        openSettings();
        startAddingOfficeLm();

        save();

        final ProviderConfig stored = configs.find("office-lm").orElseThrow();
        assertThat(stored.displayName()).isEqualTo("Office LM");
        assertThat(stored.baseUrl().toString()).isEqualTo("http://10.0.0.5:1234/v1");
        assertThat(configs.saves()).isEqualTo(1);
        assertThat(required("provider-row-office-lm").lookup(".provider-endpoint"))
                .isInstanceOfSatisfying(
                        javafx.scene.control.Label.class,
                        label -> assertThat(label.getText()).isEqualTo("10.0.0.5:1234"));
        assertThat(text("settings-form-title")).isEqualTo("Office LM");
    }

    // IF Cancel on a new provider left the blank form, THEN there would be no way back to the provider in use.
    @Test
    void add_cancelled_returnsToTheCurrentProvider() {
        openSettings();
        startAddingOfficeLm();

        onFx(() -> button("settings-form-cancel").fire());

        assertThat(text("settings-form-title")).isEqualTo("Ollama");
        assertThat(configs.saves()).isZero();
    }

    // IF Delete removed at once, THEN one click would destroy a provider; IF Cancel still removed it, the question
    // would be decoration.
    @Test
    void delete_questionCancelled_keepsTheProvider() {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));

        onFx(() -> button("settings-provider-delete").fire());
        assertThat(text("confirm-yes")).isEqualTo("Delete");
        onFx(() -> button("confirm-cancel").fire());

        assertThat(configs.find("lmstudio")).isPresent();
        assertThat(scene.getRoot().lookup("#provider-row-lmstudio")).isNotNull();
    }

    @Test
    void delete_questionConfirmed_removesTheProviderAndItsRow() throws TimeoutException {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));
        onFx(() -> button("settings-provider-delete").fire());

        onFx(() -> button("confirm-yes").fire());
        awaitFx(() -> viewModel().providers().size() == 1);

        assertThat(configs.find("lmstudio")).isEmpty();
        assertThat(scene.getRoot().lookup("#provider-row-lmstudio")).isNull();
        assertThat(text("settings-form-title")).isEqualTo("Ollama");
    }

    // IF the key's status were missing or showed the value, THEN a person could not tell why a request is refused.
    @ParameterizedTest
    @CsvSource({"OFFICE_KEY, set", "OTHER_KEY, not set"})
    void keyStatus_variableNamed_saysSetOrNotSetAndNeverShowsAValue(final String variable, final String words) {
        openSettings();
        openFold();
        onFx(() -> ((ToggleButton) required("settings-form-auth-bearer")).setSelected(true));

        setField("settings-form-key-variable", variable);

        assertThat(text("settings-form-key-status")).isEqualTo(words);
    }

    // IF Ollama's chat endpoint could be edited, THEN a value would be saved that Ollama's own protocol ignores.
    @Test
    void chatPath_ollamaOpened_isOffWithTheReason() {
        openSettings();

        final TextField chat = field("settings-form-chat-path");

        assertThat(chat.isDisable()).isTrue();
        assertThat(text("settings-form-chat-path-note")).startsWith("Not used for Ollama");
    }

    // IF the model list ignored the names a person added, THEN a model the server does not list could not be picked.
    @Test
    void customModels_oneAdded_isOfferedInTheModelList() {
        openSettings();
        openFold();
        onFx(() -> toggle("settings-form-models-on").setSelected(true));

        setField("settings-form-model-input", "office-model");
        onFx(() -> field("settings-form-model-input").getOnAction().handle(new ActionEvent()));

        assertThat(ThemeTestSupport.onFx(() -> modelCombo().getItems())).contains("office-model");
    }

    // IF removing the middle row dropped or kept the wrong header, THEN the rows would no longer match what is stored.
    @Test
    void headerRemove_middleRow_keepsTheOthersInOrder() {
        openSettings();
        onFx(() -> toggle("settings-form-headers-on").setSelected(true));
        for (final String name : List.of("X-A", "X-B", "X-C")) {
            onFx(() -> button("settings-form-header-add").fire());
            setField(
                    "settings-form-header-name-"
                            + (form().draft().get().headers().size() - 1),
                    name);
        }

        onFx(() -> button("settings-form-header-remove-1").fire());

        assertThat(ThemeTestSupport.onFx(() -> form().draft().get().headers()))
                .extracting(ProviderDraft.HeaderRow::name)
                .containsExactly("X-A", "X-C");
        assertThat(field("settings-form-header-name-0").getText()).isEqualTo("X-A");
        assertThat(field("settings-form-header-name-1").getText()).isEqualTo("X-C");
        assertThat(scene.getRoot().lookup("#settings-form-header-name-2")).isNull();
    }

    // IF Delete stayed on while the model works for the provider in use, THEN the work would lose its provider.
    @Test
    void delete_modelWorkRunsOnTheCurrentProvider_isOffAndSaysWhy() {
        openSettings();
        assertThat(button("settings-provider-delete").isDisable()).isFalse();

        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.TRANSLATION, () -> {}));

        assertThat(button("settings-provider-delete").isDisable()).isTrue();
        assertThat(text("settings-provider-delete-reason")).startsWith("Wait until the model work ends");
    }

    // IF Delete were off for a provider that is not in use, THEN a running translation would block tidying the list.
    @Test
    void delete_modelWorkRunsOtherProviderOpened_staysOn() {
        openSettings();
        onFx(() -> viewModel().editProvider("lmstudio"));

        onFx(() -> injector.getInstance(ActivityTracker.class).begin(ActivityKind.TRANSLATION, () -> {}));

        assertThat(button("settings-provider-delete").isDisable()).isFalse();
    }
}
