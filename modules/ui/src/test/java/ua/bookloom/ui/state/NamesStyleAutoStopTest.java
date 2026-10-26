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
import java.util.List;
import java.util.Locale;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.AutoNamesFixture;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/**
 * Stop during the automatic names pass (15i.U6): a pass that had not stored anything changes nothing, a pass that had
 * already stored its answer when Stop came is shown with its results, and a stopped pass that failed is read back.
 */
class NamesStyleAutoStopTest extends FxTestBase {

    private ScriptedGlossaryService glossary;
    private ScriptedLexiconService lexicon;
    private ScriptedNamesPass namesPass;
    private SettingsViewModel settings;
    private ActivityTracker tracker;
    private Messages messages;
    private NamesStyleViewModel vm;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        glossary = new ScriptedGlossaryService();
        lexicon = new ScriptedLexiconService();
        lexicon.holds(TABLE);
        namesPass = new ScriptedNamesPass();
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        settings = injector.getInstance(SettingsViewModel.class);
        tracker = injector.getInstance(ActivityTracker.class);
        messages = injector.getInstance(Messages.class);
        vm = viewModel(new DirectExecutor());
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> vm.show(PROJECT));
    }

    private NamesStyleViewModel viewModel(final java.util.concurrent.ExecutorService executor) {
        return onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                namesPass,
                ScriptedChatModelFactory.ok(),
                settings,
                executor,
                messages,
                tracker,
                () -> "new"));
    }

    private void chooseModel() {
        interact(() -> settings.model().set("gemma3:12b"));
    }

    private ChangeResults results() {
        return onFx(() -> vm.results().get());
    }

    private void passWillSucceed() {
        namesPass.willAnswer(Result.ok(AutoNamesFixture.report()));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));
    }

    // IF Stop left the pass writing, THEN "a stopped pass changes nothing" would be untrue.
    @Test
    void stopModel_passWaiting_publishesAStoppedAutoNamesResultAndWritesNothing() {
        final NamesStyleViewModel stoppable = viewModelWithHeldBackground();
        run(stoppable::autoPrepare);

        run(stoppable::stopModel);

        final ChangeResults shown = onFx(() -> stoppable.results().get());
        assertThat(shown.operation()).isEqualTo(ChangeOperation.AUTO_NAMES);
        assertThat(shown.outcome()).isEqualTo(ChangeResults.Outcome.STOPPED);
        assertThat(shown.rows()).isEmpty();
        assertThat(lexicon.calls()).noneMatch(call -> call.startsWith("remove(") || call.startsWith("restore("));
        assertThat(onFx(() -> tracker.running())).isEmpty();
    }

    // IF Stop dropped an answer that had already been stored, THEN the screen would show the old rows and offer no Undo
    // for changes the glossary really holds. The pass finishes on the executor and its answer waits in the FX queue
    // while Stop is pressed.
    @Test
    void stopModel_passAlreadyStoredItsAnswer_showsWhatWasStoredWithItsResults() {
        chooseModel();
        passWillSucceed();

        run(() -> {
            vm.autoPrepare();
            vm.stopModel();
        });

        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL_TYPED, MID);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle");
        assertThat(results().operation()).isEqualTo(ChangeOperation.AUTO_NAMES);
        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.CHANGED);
        assertThat(onFx(() -> vm.busy().get())).isFalse();
    }

    // IF a stopped pass that wrote before it was interrupted were not read back, THEN the tables on screen would
    // differ from the stored ones.
    @Test
    void stopModel_passWroteThenFailedAsCancelled_reloadsBothTables() {
        chooseModel();
        namesPass.duringRun(() -> lexicon.replaceHeld(PENTACLE));
        namesPass.willAnswer(Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "The pass was cancelled.")));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));

        run(() -> {
            vm.autoPrepare();
            vm.stopModel();
        });

        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL_TYPED, MID);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle");
        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.STOPPED);
    }

    private NamesStyleViewModel viewModelWithHeldBackground() {
        final NamesStyleViewModel model = onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                namesPass,
                ScriptedChatModelFactory.ok(),
                settings,
                new HeldExecutor(),
                messages,
                tracker,
                () -> "new"));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> model.show(PROJECT));
        chooseModel();
        return model;
    }
}
