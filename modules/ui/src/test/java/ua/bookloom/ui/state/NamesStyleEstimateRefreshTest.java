package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;

/**
 * The Prepare card's estimate is read again once a names pass or a run has stored the seconds it measured, so it says
 * "measured on this model" right after the pass, without leaving the screen and coming back.
 */
class NamesStyleEstimateRefreshTest extends FxTestBase {

    private static final String PROJECT = "p1";
    private static final long WAIT_SECONDS = 5;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all this test needs; the view model has no scene.
    }

    // IF the estimate were read only when the screen opens, THEN the card would say "estimate" after a pass that stored
    // its seconds, until the person left the screen and came back.
    @Test
    void storedSpeedsChange_screenShown_readsTheEstimateAgain() throws TimeoutException {
        final ScriptedNamesPass namesPass = new ScriptedNamesPass();
        final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
        glossary.willAnswer(Result.ok(List.of()));
        final Injector injector = UiTestInjector.builder(Locale.ENGLISH)
                .glossary(glossary)
                .namesPass(namesPass)
                .build();
        final NamesStyleViewModel vm = onFx(() -> injector.getInstance(NamesStyleViewModel.class));
        namesPass.willEstimate(ScriptedNamesPass.range(8, 1, 2));
        run(() -> vm.show(PROJECT));
        awaitEstimate(vm, ScriptedNamesPass.range(8, 1, 2));
        namesPass.willEstimate(ScriptedNamesPass.range(8, 1, 1));

        run(() -> injector.getInstance(ModelSpeeds.class).bump());

        awaitEstimate(vm, ScriptedNamesPass.range(8, 1, 1));
        assertThat(onFx(() -> vm.autoEstimate().get())).isEqualTo(ScriptedNamesPass.range(8, 1, 1));
    }

    private static void awaitEstimate(final NamesStyleViewModel vm, final Object expected) throws TimeoutException {
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> Objects.equals(onFx(() -> vm.autoEstimate().get()), expected));
    }
}
