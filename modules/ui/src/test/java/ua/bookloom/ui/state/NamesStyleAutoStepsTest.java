package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.AutoNamesFixture.HALE;
import static ua.bookloom.ui.AutoNamesFixture.MID;
import static ua.bookloom.ui.AutoNamesFixture.PENTACLE;
import static ua.bookloom.ui.AutoNamesFixture.PROJECT;
import static ua.bookloom.ui.AutoNamesFixture.TABLE;
import static ua.bookloom.ui.AutoNamesFixture.WELL;
import static ua.bookloom.ui.AutoNamesFixture.WELL_TYPED;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.ui.AutoNamesFixture;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.PassEvents;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;

/**
 * What the results of "Prepare names automatically" keep of the run for the Results view: the steps the pass
 * announced, the time it took by the tracker's clock, and whether its dialog was in the background when it ended.
 */
class NamesStyleAutoStepsTest extends FxTestBase {

    private static final Duration ELAPSED = Duration.ofSeconds(108);

    private final MutableClock clock = new MutableClock();
    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();
    private final ScriptedLexiconService lexicon = new ScriptedLexiconService();
    private final ScriptedNamesPass namesPass = new ScriptedNamesPass();
    private ActivityTracker tracker;
    private NamesStyleViewModel vm;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        lexicon.holds(TABLE);
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        final SettingsViewModel settings = injector.getInstance(SettingsViewModel.class);
        tracker = onFx(() -> new ActivityTracker(new StateMirror(), new KeepAwake.Off(), new KeepAwakeGate(), clock));
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                namesPass,
                ScriptedChatModelFactory.ok(),
                settings,
                new DirectExecutor(),
                injector.getInstance(ua.bookloom.ui.i18n.Messages.class),
                tracker,
                () -> "new"));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> vm.show(PROJECT));
        interact(() -> settings.model().set("gemma3:12b"));
    }

    private void prepare() {
        namesPass.willAnswer(Result.ok(AutoNamesFixture.report()));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));
        run(vm::autoPrepare);
    }

    private ChangeResults results() {
        return onFx(() -> vm.results().get());
    }

    // IF the results forgot the steps, THEN the Steps tab of the Results view would have nothing to list.
    @Test
    void autoPrepare_ends_resultsCarryTheStepsAndTheElapsedTime() {
        PassEvents.upTo(new int[] {0, 6, 0, 3, 25, 4, 2, 0}, 8, 49).forEach(namesPass::willReport);
        namesPass.duringRun(() -> clock.advance(ELAPSED));

        prepare();

        assertThat(results().elapsed()).isEqualTo(ELAPSED);
        assertThat(results().steps().entries().stream()
                        .filter(entry -> entry.kind() == StepKind.STAGE)
                        .count())
                .isEqualTo(8);
        assertThat(results().steps().entries().stream()
                        .filter(entry -> entry.kind() == StepKind.REQUEST)
                        .count())
                .isEqualTo(40);
        assertThat(results().endedHidden()).isFalse();
    }

    // IF a pass that ended in the background looked like a visible one, THEN its Results view would open by itself.
    @Test
    void autoPrepare_endsWhileHidden_marksTheResultsHidden() {
        namesPass.duringRun(() -> tracker.hide(tracker.running().getFirst().id()));

        prepare();

        assertThat(results().endedHidden()).isTrue();
    }
}
