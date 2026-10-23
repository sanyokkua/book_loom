package ua.bookloom.ui.screen;

import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ScriptedModelSettings;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.ModelEntry;
import ua.bookloom.ui.state.ModelForm;
import ua.bookloom.ui.state.ModelRoster;

/**
 * What the Settings › Models tests share: two providers (Ollama with two models, LM Studio with one), a recording
 * store and a scripted provider report, and the steps that open the tab and wait until it has read everything.
 */
abstract class SettingsModelsScreenTestBase extends SettingsScreenTestBase {

    static final String GEMMA = "gemma4:e4b";
    static final String GPT_OSS = "gpt-oss:20b";

    final ScriptedModelSettings modelSettings = new ScriptedModelSettings();
    final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    void customize(final UiTestInjector.Builder builder) {
        builder.modelSettings(modelSettings).capabilities(capabilities);
    }

    @BeforeEach
    void listModels() {
        catalog.respondFor("ollama", GEMMA, GPT_OSS);
        catalog.respondFor("lmstudio", "lapa");
    }

    /** Opens Settings, selects the Models tab and waits until the models are listed and the first one is read. */
    void openModels() throws TimeoutException {
        openSettings();
        onFx(() -> tabs().getSelectionModel().select(modelsTab()));
        awaitFx(() -> !roster().listing().get()
                && modelForm().selected().get() != null
                && !modelForm().facts().get().loading());
    }

    Tab modelsTab() {
        return tabs().getTabs().stream()
                .filter(tab -> "settings-tab-models".equals(tab.getId()))
                .findFirst()
                .orElseThrow();
    }

    ModelForm modelForm() {
        return injector.getInstance(ModelForm.class);
    }

    ModelRoster roster() {
        return injector.getInstance(ModelRoster.class);
    }

    @SuppressWarnings("unchecked")
    ComboBox<ModelEntry> modelsCombo() {
        return (ComboBox<ModelEntry>) required("models-model");
    }

    TextField modelsField(final String id) {
        return (TextField) required(id);
    }

    String shown(final String id) {
        return ThemeTestSupport.onFx(() -> ((Labeled) required(id)).getText());
    }

    boolean visible(final String id) {
        return ThemeTestSupport.onFx(() -> scene.getRoot().lookup("#" + id) != null
                && scene.getRoot().lookup("#" + id).isVisible()
                && scene.getRoot().lookup("#" + id).isManaged());
    }

    /** Picks a model in the chooser the way a person does, and waits until its settings are read. */
    void chooseModel(final String modelId) throws TimeoutException {
        final ModelEntry entry = ThemeTestSupport.onFx(() -> modelsCombo().getItems().stream()
                .filter(item -> item.modelId().equals(modelId))
                .findFirst()
                .orElseThrow());
        onFx(() -> modelsCombo().getSelectionModel().select(entry));
        awaitFx(() -> modelId.equals(modelForm().selected().get().modelId())
                && !modelForm().facts().get().loading());
    }

    static ModelSettings with(
            final String provider,
            final String model,
            final TemperatureChoice choice,
            final @Nullable Double temperature,
            final @Nullable Integer context,
            final @Nullable Integer limit) {
        return new ModelSettings(
                provider,
                model,
                choice,
                temperature,
                context,
                limit,
                OutputLengthField.MAX_TOKENS,
                List.<RefusedSetting>of(),
                null,
                null);
    }

    Button press(final String id) {
        final Button button = (Button) required(id);
        onFx(button::fire);
        return button;
    }
}
