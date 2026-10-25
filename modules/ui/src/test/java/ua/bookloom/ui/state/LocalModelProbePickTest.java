package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.settings.AppSetting;

/** The model pick and the person's "Try again" of the first-launch look. */
@SuppressWarnings("NullAway.Init")
class LocalModelProbePickTest extends LocalModelProbeTestBase {

    @Test
    void pick_useThisModel_storesAndAdoptsTheChosenModel() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        onFx(() -> {
            pick.use("qwen3:8b");
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(storedModel()).contains(QWEN);
        assertThat(onFx(() -> viewModel.selection())).contains(QWEN);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(QWEN, true));
    }

    @Test
    void pick_notNow_leavesTheCurrentModelUnsetAndSaysDeclined() {
        catalog.answering(OLLAMA, "gemma4:e4b", "qwen3:8b");
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        onFx(() -> {
            pick.notNow();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(storedModel()).isEmpty();
        assertThat(onFx(() -> viewModel.selection())).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Declined());
    }

    // IF the look ignored an unreadable flag and looked anyway, THEN a damaged store would make it repeat each launch.
    @Test
    void runFirstLaunch_flagUnreadable_skipsTheLookAndMakesNoCall() {
        final LocalModelProbe probe = probe(new UnreadableFlagSettings(settings));

        firstLaunch(probe);

        assertThat(catalog.drafts()).isEmpty();
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Idle());
    }

    // IF Try again honoured the flag, THEN pressing it after the first launch would do nothing.
    @Test
    void tryAgain_flagAlreadySet_looksAgainAnyway() {
        settings.put(AppSetting.FIRST_LAUNCH_PROBED, true);
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        tryAgain(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
        assertThat(storedModel()).contains(GEMMA);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, false));
    }

    @Test
    void tryAgain_afterANotFoundLook_asksBothAddressesAgainOnceEach() {
        final LocalModelProbe probe = probe();
        firstLaunch(probe);

        tryAgain(probe);

        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO, OLLAMA, LM_STUDIO);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound(true));
    }

    // IF the look blocked on the calling thread, THEN the window would freeze while a program does not answer.
    @Test
    void runFirstLaunch_programSlowToAnswer_saysLookingUntilTheAnswerComes() throws Exception {
        executor = Executors.newSingleThreadExecutor(task -> {
            final Thread thread = new Thread(task, "probe-test");
            thread.setDaemon(true);
            return thread;
        });
        catalog.holdDrafts();
        catalog.answering(OLLAMA, "gemma4:e4b");
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Looking());
        catalog.releaseDrafts();
        WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS, () -> outcome(probe) instanceof ProbeOutcome.Found);
        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.Found(GEMMA, true));
    }

    @Test
    void runFirstLaunch_errorReply_isNotFoundWithoutAnyThrownError() {
        catalog.failing(OLLAMA, AppError.of(ErrorCode.upstream, "Bad", "A 503 reply."));
        final LocalModelProbe probe = probe();

        firstLaunch(probe);

        assertThat(outcome(probe)).isEqualTo(new ProbeOutcome.NotFound(false));
        assertThat(catalog.draftAddresses()).containsExactly(OLLAMA, LM_STUDIO);
    }
}
