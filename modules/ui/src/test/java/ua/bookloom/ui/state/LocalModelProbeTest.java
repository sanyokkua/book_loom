package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.settings.AppSetting;
import ua.bookloom.ui.RecordingModelCatalog;
import ua.bookloom.ui.dialog.RecordingModelPickPrompt;

/** What the first-launch look asks, stores and says; the pick and Try again cases are in {@link LocalModelProbePickTest}. */
@SuppressWarnings("NullAway.Init")
class LocalModelProbeTest extends LocalModelProbeTestBase {

    // IF the look asked any other address, or one twice, THEN it would reach where ADR-0044 does not allow.
    @Test
    void runFirstLaunch_freshStore_asksExactlyTheTwoLocalAddressesOnceEachWithAOneSecondConnect() {
        firstLaunch(probe());

        assertThat(catalog.drafts())
                .extracting(
                        RecordingModelCatalog.Draft::address,
                        RecordingModelCatalog.Draft::kind,
                        RecordingModelCatalog.Draft::connectTimeout)
                .containsExactly(
                        tuple(OLLAMA, ProviderKind.OLLAMA, Duration.ofSeconds(1)),
                        tuple(LM_STUDIO, ProviderKind.OPENAI_COMPATIBLE, Duration.ofSeconds(1)));
        assertThat(catalog.ids()).isEmpty();
    }

    // IF the look repeated on every launch, THEN the app would reach out by itself more than once.
    @Test
    void runFirstLaunch_flagAlreadySet_makesNoCallAtAll() {
        settings.put(AppSetting.FIRST_LAUNCH_PROBED, true);

        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(catalog.drafts()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Idle());
    }

    @Test
    void runFirstLaunch_calledTwice_looksOnlyOnce() {
        final LocalModelProbe probe = probe();

        firstLaunch(probe);
        firstLaunch(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
    }

    // IF the look overwrote a program the person already set up, THEN their name, timeouts and models would be lost.
    @Test
    void runFirstLaunch_providerSavedAtSameAddress_keepsItAndStillSelectsTheModel() {
        final ProviderConfig mine = new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99));
        configs.save(mine);
        catalog.answering(OLLAMA, "gemma4:e4b");

        firstLaunch(probe());

        assertThat(configs.saved()).containsExactly(mine);
        assertThat(settings.get(AppSetting.CURRENT_MODEL).data()).contains(GEMMA);
    }

    // IF "ollama" saved at a remote host were taken for the local program, THEN the chosen model would not exist there.
    @Test
    void runFirstLaunch_providerSavedAtAnotherAddress_ignoresTheAnswerAndSavesNothing() {
        configs.save(new ProviderConfig(
                "ollama",
                ProviderKind.OLLAMA,
                URI.create("http://10.0.0.5:11434"),
                Duration.ofSeconds(9),
                Duration.ofSeconds(99)));
        catalog.answering(OLLAMA, "gemma4:e4b");

        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        assertThat(configs.saved()).hasSize(1);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound(false));
    }

    // IF the flag were not written after a look that found nothing, THEN every launch would look again.
    @Test
    void runFirstLaunch_nothingAnswering_storesNothingSetsTheFlagAndSaysNotFound() {
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).isEmpty();
        assertThat(storedModel()).isEmpty();
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound(false));
    }

    // IF a program that answers with no models counted as found, THEN the line would say ready for nothing.
    @Test
    void runFirstLaunch_programAnswersWithZeroModels_countsAsNotFoundAndStoresNothing() {
        catalog.answering(OLLAMA);
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).isEmpty();
        assertThat(storedModel()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound(false));
    }

    // IF the stored provider carried the look's one-second connect timeout, THEN every later call would give up early.
    @Test
    void runFirstLaunch_oneOllamaModel_savesTheProviderWithDefaultTimeoutsAndChoosesTheModel() {
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved())
                .containsExactly(new ProviderConfig(
                        "ollama",
                        ProviderKind.OLLAMA,
                        URI.create(OLLAMA),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(storedModel()).contains(GEMMA);
        assertThat(onFx(() -> viewModel.selection())).contains(GEMMA);
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
        assertThat(pick.asked()).isEmpty();
    }

    @Test
    void runFirstLaunch_onlyLmStudioAnswersWithOneModel_savesItAndChoosesItsModel() {
        catalog.answering(LM_STUDIO, "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved())
                .containsExactly(new ProviderConfig(
                        "lmstudio",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(LM_STUDIO),
                        ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                        ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        assertThat(storedModel()).contains(LM_QWEN);
        assertThat(onFx(() -> viewModel.selection())).contains(LM_QWEN);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(LM_QWEN, true));
    }

    // IF both programs answered and only one were kept, THEN Settings would not list the other one the person has.
    @Test
    void runFirstLaunch_bothAnswer_savesBothAndMakesOllamaTheCurrentProgram() {
        catalog.answering(OLLAMA, "gemma4:e4b").answering(LM_STUDIO, "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactlyInAnyOrder("ollama", "lmstudio");
        assertThat(storedModel()).contains(GEMMA);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
    }

    @Test
    void runFirstLaunch_ollamaSilentLmStudioAnswers_lmStudioIsTheCurrentProgram() {
        catalog.answering(OLLAMA).answering(LM_STUDIO, "qwen3:8b");

        firstLaunch(probe());

        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactly("lmstudio");
        assertThat(storedModel()).contains(LM_QWEN);
    }

    // IF the first of several models were chosen silently, THEN the person would translate with a model they never
    // picked.
    @Test
    void runFirstLaunch_severalModels_asksWhichAndLeavesTheCurrentModelUnset() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(pick.asked())
                .containsExactly(new RecordingModelPickPrompt.Asked("ollama", List.of("gemma4:e4b", "qwen3:8b")));
        assertThat(storedModel()).isEmpty();
        assertThat(onFx(() -> viewModel.selection())).isEmpty();
        assertThat(configs.saved()).extracting(ProviderConfig::id).containsExactly("ollama");
        assertThat(flagSet()).isTrue();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Asking());
    }
}
