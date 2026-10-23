package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.TemperatureChoice;

/** Choosing a model: what the form shows for it, what the program says about it, and which model is chosen first. */
class ModelFormSelectTest extends ModelFormTestBase {

    // IF the loaded figure were stored as the person's choice, THEN opening a model would make the form dirty.
    @Test
    void select_loaded16384NothingStored_suggests16384AndClean() throws Exception {
        capabilities.report("ollama", "gpt-oss", null, 16384);
        buildForm();

        select(GPT_OSS);

        assertThat(onFx(() -> form.facts().get().loaded())).isEqualTo(16384);
        assertThat(draft().contextOn()).isFalse();
        assertThat(dirty()).isFalse();
        onFx(() -> {
            form.switchContext(true);
            return null;
        });
        assertThat(draft().contextText()).isEqualTo("16384");
        assertThat(dirty()).isTrue();
    }

    @Test
    void select_storedContext_showsItWithTheSwitchOnAndTheReportedFacts() throws Exception {
        modelSettings.put(ModelSettings.defaults("ollama", "gemma4:e4b")
                .withChoices(TemperatureChoice.CHOOSE, null, 16384, null, OutputLengthField.MAX_TOKENS));
        capabilities.report("ollama", "gemma4:e4b", 131072, 16384);
        buildForm();

        select(GEMMA);

        assertThat(draft().contextOn()).isTrue();
        assertThat(draft().contextText()).isEqualTo("16384");
        assertThat(onFx(() -> form.facts().get().contextLength())).isEqualTo(131072);
        assertThat(dirty()).isFalse();
    }

    @Test
    void select_structuredFromLastTest_yes() throws Exception {
        structured.record("ollama", "gemma4:e4b", true);
        buildForm();

        select(GEMMA);

        assertThat(onFx(() -> form.facts().get().structured())).isTrue();
    }

    // IF a model never tested this session showed "no", THEN a person would think it cannot answer structurally.
    @Test
    void select_noTest_structuredNull() throws Exception {
        buildForm();

        select(GEMMA);

        assertThat(onFx(() -> form.facts().get().structured())).isNull();
    }

    @Test
    void facts_testFinishesWhileShown_followsTheNewAnswer() throws Exception {
        buildForm();
        select(GEMMA);

        onFx(() -> {
            structured.record("ollama", "gemma4:e4b", false);
            return null;
        });

        assertThat(onFx(() -> form.facts().get().structured())).isFalse();
    }

    // IF a slow answer for the model left behind landed last, THEN the form would show another model's figures.
    @Test
    void select_staleLoad_ignored() throws Exception {
        capabilities.report("ollama", "gemma4:e4b", 131072, 16384);
        final CountDownLatch release = new CountDownLatch(1);
        capabilities.holdUntil("ollama", "gemma4:e4b", release);
        buildForm();
        onFx(() -> {
            form.select(GEMMA);
            return null;
        });
        select(GPT_OSS);

        release.countDown();
        pool.awaitFinished(2);
        await(() -> true);

        assertThat(onFx(() -> form.selected().get())).isEqualTo(GPT_OSS);
        assertThat(onFx(() -> form.facts().get().contextLength())).isNull();
        assertThat(onFx(() -> form.facts().get().loading())).isFalse();
    }

    // IF choosing the same model again dropped the typing, THEN coming back to the tab would lose it.
    @Test
    void select_sameModelWhileDirty_keepsTheTyping() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withContext(true, "4096", null));

        onFx(() -> {
            form.select(GEMMA);
            return null;
        });

        assertThat(draft().contextText()).isEqualTo("4096");
        assertThat(dirty()).isTrue();
    }

    @Test
    void discard_restoresBaseline() throws Exception {
        buildForm();
        select(GEMMA);
        edit(draft -> draft.withChoice(TemperatureChoice.OMIT).withLimit(true, "200"));

        onFx(() -> {
            form.discard();
            return null;
        });

        assertThat(draft()).isEqualTo(ModelDraft.of(ModelSettings.defaults("ollama", "gemma4:e4b")));
        assertThat(dirty()).isFalse();
    }

    @Test
    void subject_namesModelAndProvider() throws Exception {
        buildForm();
        assertThat(onFx(form::subject)).isEmpty();

        select(GEMMA);

        assertThat(onFx(form::subject)).isEqualTo("gemma4:e4b · Ollama");
    }

    // IF the first model were not the one in use, THEN the tab would open on a model nobody is translating with.
    @Test
    void roster_listed_selectsTheCurrentModel() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b", "gpt-oss");
        catalog.respondFor("lmstudio");
        buildForm();
        onFx(() -> {
            viewModel.applySavedCurrentModel("gpt-oss");
            roster.refresh();
            return null;
        });

        await(() -> form.selected().get() != null && !form.facts().get().loading());

        assertThat(onFx(() -> form.selected().get())).isEqualTo(GPT_OSS);
    }

    @Test
    void roster_listedWithoutTheCurrentModel_selectsTheFirst() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b", "gpt-oss");
        catalog.respondFor("lmstudio");
        buildForm();
        onFx(() -> {
            viewModel.applySavedCurrentModel("something-else");
            roster.refresh();
            return null;
        });

        await(() -> form.selected().get() != null && !form.facts().get().loading());

        assertThat(onFx(() -> form.selected().get())).isEqualTo(GEMMA);
    }

    // IF the stored settings waited for the program's answer, THEN a slow server would lock the form for seconds.
    @Test
    void select_reportHeldBack_storedSettingsShownAndFormNotReading() throws Exception {
        modelSettings.put(ModelSettings.defaults("ollama", "gemma4:e4b")
                .withChoices(TemperatureChoice.CHOOSE, null, 16384, null, OutputLengthField.MAX_TOKENS));
        final CountDownLatch release = new CountDownLatch(1);
        capabilities.holdUntil("ollama", "gemma4:e4b", release);
        buildForm();

        onFx(() -> {
            form.select(GEMMA);
            return null;
        });
        await(() -> !form.reading().get());

        assertThat(draft().contextText()).isEqualTo("16384");
        assertThat(onFx(() -> form.facts().get().loading())).isTrue();
        release.countDown();
        await(() -> !form.facts().get().loading());
    }

    // IF the chosen model's provider was deleted and the form kept it, THEN Save would write a row nothing lists.
    @Test
    void offer_rosterWithoutTheSelectedEntry_selectsAnother() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b");
        catalog.respondFor("lmstudio", "lapa");
        buildForm();
        refreshRoster();
        assertThat(onFx(() -> form.selected().get().modelId())).isEqualTo("gemma4:e4b");

        configs.delete("ollama");
        refreshRoster();
        await(() -> !form.reading().get() && !form.facts().get().loading());

        assertThat(onFx(() -> form.selected().get().modelId())).isEqualTo("lapa");
        assertThat(dirty()).isFalse();
    }

    @Test
    void offer_emptyRoster_clearsTheSelection() throws Exception {
        catalog.respondFor("ollama", "gemma4:e4b");
        buildForm();
        refreshRoster();
        configs.delete("ollama");
        configs.delete("lmstudio");

        refreshRoster();

        assertThat(onFx(() -> form.selected().get())).isNull();
        assertThat(onFx(() -> form.reading().get())).isFalse();
    }
}
