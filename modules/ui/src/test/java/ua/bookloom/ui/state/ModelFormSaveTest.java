package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.TemperatureChoice;

/** What Save on the Models tab writes, and what it leaves alone. */
class ModelFormSaveTest extends ModelFormTestBase {

    // IF Save did not write the choice, THEN the model would still be sent the temperature it refuses.
    @Test
    void save_omitForGptOss_storesOmit() throws Exception {
        buildForm();
        select(GPT_OSS);
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));

        final boolean ran = save();

        assertThat(ran).isTrue();
        assertThat(stored(GPT_OSS).temperatureChoice()).isEqualTo(TemperatureChoice.OMIT);
        assertThat(stored(GPT_OSS).temperature()).isNull();
        assertThat(dirty()).isFalse();
        assertThat(onFx(() -> form.justSaved().get())).isTrue();
    }

    @Test
    void save_myValue05_storesValue05() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withChoice(TemperatureChoice.VALUE).withTemperature(0.5));

        save();

        assertThat(stored(GEMMA).temperatureChoice()).isEqualTo(TemperatureChoice.VALUE);
        assertThat(stored(GEMMA).temperature()).isEqualTo(0.5);
    }

    // IF a save touched another model's record, THEN setting up one model would change a model nobody opened.
    @Test
    void save_contextForGemmaOnly_otherModelUnchanged() throws Exception {
        modelSettings.put(ModelSettings.defaults("ollama", "gpt-oss")
                .withChoices(TemperatureChoice.OMIT, null, null, null, OutputLengthField.MAX_TOKENS));
        final ModelSettings before = stored(GPT_OSS);
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withContext(true, "16384", null));

        save();

        assertThat(stored(GEMMA).contextWindow()).isEqualTo(16384);
        assertThat(stored(GPT_OSS)).isEqualTo(before);
    }

    // IF Save wrote what it read at selection, THEN a refusal the chat layer learned meanwhile would be forgotten.
    @Test
    void save_refusalRecordedMeanwhile_refusalKept() throws Exception {
        buildForm();
        select(GEMMA);
        refuse(GEMMA, temperatureRefusal());
        edit(draft -> draft.withContext(true, "8192", null));

        save();

        assertThat(stored(GEMMA).refused()).containsExactly(temperatureRefusal());
        assertThat(stored(GEMMA).contextWindow()).isEqualTo(8192);
    }

    @Test
    void save_measuredSpeed_kept() throws Exception {
        modelSettings.measured("ollama", "gemma4:e4b", 2.5, MeasuredFrom.RUN);
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withLimit(true, "300"));

        save();

        assertThat(stored(GEMMA).measuredSecondsPerSegment()).isEqualTo(2.5);
        assertThat(stored(GEMMA).measuredFrom()).isEqualTo(MeasuredFrom.RUN);
        assertThat(stored(GEMMA).maxOutput()).isEqualTo(300);
    }

    // IF a figure that is not a number reached the store, THEN the model would be sent a window nobody typed.
    @Test
    void save_blockingFinding_writesNothingAndThenNotRun() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withContext(true, "0", null));
        final int updatesBefore = modelSettings.updateCount();

        final boolean ran = save();

        assertThat(ran).isFalse();
        assertThat(modelSettings.updateCount()).isEqualTo(updatesBefore);
        assertThat(onFx(() -> form.errorsShown().get())).isTrue();
        assertThat(onFx(() -> form.findings().get())).isNotEmpty();
    }

    // IF a failed save dropped the typing, THEN a full disk would also cost the person their work.
    @Test
    void save_storeFails_keepsDraftAndSaveFailed() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));
        final ModelDraft typed = draft();
        modelSettings.failUpdate();

        final boolean ran = save();

        assertThat(ran).isFalse();
        assertThat(onFx(() -> form.saveFailed().get())).isTrue();
        assertThat(draft()).isEqualTo(typed);
        assertThat(dirty()).isTrue();
    }

    // IF the store or the provider were called where the window draws, THEN a slow disk or server would freeze it.
    @Test
    void save_andSelect_neverCallTheStoreOrTheProviderOnTheFxThread() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT));

        save();

        assertThat(modelSettings.fxThreadCalls()).isZero();
        assertThat(capabilities.fxThreadCalls()).isZero();
    }
}
