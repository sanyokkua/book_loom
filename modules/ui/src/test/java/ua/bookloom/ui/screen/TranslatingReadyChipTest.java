package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.QuantTier;
import ua.bookloom.ui.ScriptedModelCapabilities;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.SettingsViewModel;

/** The ready card's model row carries the quant chip of the chosen model, the same chip the pickers show. */
class TranslatingReadyChipTest extends TranslatingScreenTestBase {

    private static final String GLM = "hf.co/unsloth/GLM-4.7-Flash-GGUF:UD-Q4_K_XL";

    private final ScriptedModelCapabilities capabilities = ScriptedModelCapabilities.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .models(models)
                .engine(engine)
                .glossary(glossary)
                .lexicon(lexicon)
                .namesPass(namesPass)
                .reviewDesk(desk)
                .capabilities(capabilities)
                .build();
    }

    private void choose(final String model) {
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(SettingsViewModel.class).model().set(model);
            return null;
        });
    }

    private Node chip() {
        return ThemeTestSupport.onFx(
                () -> required("translating-ready-row-model").lookup("#quant-chip"));
    }

    // IF the AI-model choice before Start carried no chip, THEN a compressed download would be picked blind.
    @Test
    void readyCard_chosenModelWithQuant_showsTheChipAfterTheValue() throws Exception {
        capabilities.nameQuant(GLM, "UD-Q4_K_XL", QuantTier.GOOD);
        readyToStart();
        choose(GLM);

        showTranslating();
        awaitFx(() -> chip() != null);

        assertThat(((Label) chip()).getText()).isEqualTo("UD-Q4_K_XL · good");
        assertThat(labelText("translating-ready-model")).isEqualTo(GLM);
    }

    // IF a model without a quant got a hidden chip, THEN a node named quant-chip would exist with nothing to say.
    @Test
    void readyCard_chosenModelWithoutQuant_hasNoChipNode() throws Exception {
        readyToStart();
        choose("llama3.1:8b");

        showTranslating();
        awaitFx(() -> "llama3.1:8b".equals(labelText("translating-ready-model")));

        assertThat(chip()).isNull();
    }
}
