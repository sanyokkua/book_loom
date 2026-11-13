package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.RefusedSetting;

/**
 * "Settings this model refused" names what each refusal covers — the whole model, or one kind of call whose answer
 * schema it refused — and Send again forgets that scope only.
 */
class ModelRefusedSectionTest extends SettingsModelsScreenTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Test
    void refused_scopedAndWholeModelRows_showTheirScopeInWords() throws TimeoutException {
        modelSettings.put(refusals());

        openModels();
        chooseModel(GPT_OSS);

        assertThat(shown("models-refused-scope-temperature")).isEqualTo("Whole model");
        assertThat(shown("models-refused-scope-response_format-term-reading")).isEqualTo("Only: Name reading");
        assertThat(shown("models-refused-scope-response_format-draft")).isEqualTo("Only: Translation");
        assertThat(shown("models-refused-why-response_format-term-reading"))
                .startsWith("Refused on Oct 21.")
                .contains("describes the answer’s shape");
    }

    @Test
    void sendAgain_scopedRow_forgetsThatScopeOnly() throws TimeoutException {
        modelSettings.put(refusals());
        openModels();
        chooseModel(GPT_OSS);

        press("models-refused-send-response_format-term-reading");
        awaitFx(() -> !modelForm().sendingAgain().get());

        assertThat(scene.getRoot().lookup("#models-refused-row-response_format-term-reading"))
                .isNull();
        assertThat(visible("models-refused-row-response_format-draft")).isTrue();
        assertThat(modelSettings.stored("ollama", GPT_OSS).orElseThrow().refused())
                .extracting(RefusedSetting::name, RefusedSetting::scope)
                .containsExactly(tuple("temperature", null), tuple("response_format", "draft"));
    }

    @Test
    void refused_foldedSystemRole_saysWhereTheSystemTextGoes() throws TimeoutException {
        modelSettings.put(ModelSettings.defaults("ollama", GPT_OSS)
                .withRefused(List.of(new RefusedSetting("system_role", DAY, "folded"))));

        openModels();
        chooseModel(GPT_OSS);

        assertThat(shown("models-refused-why-system_role")).contains("system text at the start of the first message");
    }

    private static ModelSettings refusals() {
        return ModelSettings.defaults("ollama", GPT_OSS)
                .withRefused(List.of(
                        new RefusedSetting("temperature", DAY, "omitted"),
                        new RefusedSetting("response_format", DAY, "omitted", "term-reading"),
                        new RefusedSetting("response_format", DAY, "omitted", "draft")));
    }
}
