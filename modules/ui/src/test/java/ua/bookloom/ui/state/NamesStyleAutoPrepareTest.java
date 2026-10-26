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
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Stage;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.NamesEstimate;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.ui.AutoNamesFixture;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.KeepAwake;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/**
 * The autonomous names pass as the Names &amp; style view model runs it (15i.U6): one call of the {@code NamesPass}
 * port with the chosen model, one blocking activity that keeps the computer awake, and one results object whose rows
 * cover the glossary and the recurring terms, each reverted alone or all at once through the two services.
 */
class NamesStyleAutoPrepareTest extends FxTestBase {

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
        vm = viewModel(tracker);
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> vm.show(PROJECT));
    }

    private NamesStyleViewModel viewModel(final ActivityTracker activities) {
        return onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                namesPass,
                ScriptedChatModelFactory.ok(),
                settings,
                new DirectExecutor(),
                messages,
                activities,
                () -> "new"));
    }

    private void chooseModel() {
        interact(() -> settings.model().set("gemma3:12b"));
    }

    private ChangeResults results() {
        return onFx(() -> vm.results().get());
    }

    private long rowId(final String term) {
        return results().rows().stream()
                .filter(row -> row.term().equals(term))
                .findFirst()
                .orElseThrow()
                .id();
    }

    // Scripts the pass to succeed and to leave both stores as its single write would.
    private void passWillSucceed() {
        namesPass.willAnswer(Result.ok(AutoNamesFixture.report()));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));
        glossary.willAnswer(Result.ok(List.of(WELL_TYPED, MID)));
    }

    private void prepare() {
        chooseModel();
        passWillSucceed();
        run(vm::autoPrepare);
    }

    // IF the button ran the pass for another project or without the chosen model, THEN it would prepare the wrong book.
    @Test
    void autoPrepare_modelChosen_runsThePassOnceForTheOpenProject() {
        prepare();

        assertThat(namesPass.runs()).hasSize(1);
        assertThat(namesPass.runs().getFirst().projectId()).isEqualTo(PROJECT);
    }

    // IF the results listed one table only, THEN the person could not see what the pass did to the other.
    @Test
    void autoPrepare_succeeds_publishesOneAutoNamesResultWithTheNamesRowsThenTheTermRows() {
        prepare();

        assertThat(results().operation()).isEqualTo(ChangeOperation.AUTO_NAMES);
        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.CHANGED);
        assertThat(results().rows())
                .extracting(ChangeRow::kind, ChangeRow::term)
                .containsExactly(
                        Tuple.tuple(ChangeKind.ADDED, "Mid"),
                        Tuple.tuple(ChangeKind.REMOVED, "Hale"),
                        Tuple.tuple(ChangeKind.CHANGED, "Well"),
                        Tuple.tuple(ChangeKind.ADDED, "pentacle"),
                        Tuple.tuple(ChangeKind.REMOVED, "table"));
    }

    // IF two sections reused row numbers, THEN reverting one row would put back another table's row.
    @Test
    void autoPrepare_succeeds_giveEveryRowOfBothSectionsItsOwnNumber() {
        prepare();

        assertThat(results().rows().stream().map(ChangeRow::id).distinct().count())
                .isEqualTo(5);
    }

    @Test
    void autoPrepare_succeeds_showsBothTablesAsThePassLeftThem() {
        prepare();

        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL_TYPED, MID);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle");
    }

    // IF only the glossary side were marked, THEN "Changed in last run" would be empty on the Recurring terms card.
    @Test
    void autoPrepare_succeeds_marksTheChangedRowsOfBothTables() {
        prepare();

        assertThat(onFx(() -> vm.marks().glossary().isMarked("e3"))).isTrue();
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e1"))).isTrue();
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e2"))).isFalse();
        assertThat(onFx(() -> vm.marks().terms().isMarked("pentacle"))).isTrue();
    }

    @Test
    void autoPrepare_passChangesNothing_publishesANothingChangedResult() {
        chooseModel();
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));

        run(vm::autoPrepare);

        assertThat(results().operation()).isEqualTo(ChangeOperation.AUTO_NAMES);
        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.NOTHING);
    }

    // IF Revert on a name row did not reach the glossary, THEN the button would be decoration.
    @Test
    void revert_oneNamesRow_removesThatNameAndLeavesTheRecurringTermsAlone() {
        prepare();
        glossary.willAnswer(Result.ok(true));

        run(() -> results().revert(rowId("Mid")));

        assertThat(glossary.calls()).contains("remove(p1, e3)");
        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactly(WELL_TYPED);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("pentacle");
    }

    // IF Revert on a term row did not reach the lexicon, THEN the Recurring terms section could not be undone.
    @Test
    void revert_oneTermRow_restoresThatTermAndLeavesTheGlossaryAlone() {
        prepare();

        run(() -> results().revert(rowId("table")));

        assertThat(lexicon.calls()).contains("restore(table)");
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactlyInAnyOrder("pentacle", "table");
        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL_TYPED, MID);
    }

    // IF Undo all stopped after the names section, THEN the pass would stay half applied.
    @Test
    void undoAll_afterThePass_putsBothTablesBackAsTheyWereBeforeIt() {
        prepare();
        glossary.willAnswer(Result.ok(true));
        glossary.willAnswer(Result.ok(HALE));
        glossary.willAnswer(Result.ok(WELL));

        run(() -> results().undoAll());

        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL, HALE);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("table");
        assertThat(onFx(() -> results().hasRowsToRevert())).isFalse();
        assertThat(onFx(() -> vm.marks().glossary().size())).isZero();
        assertThat(onFx(() -> vm.marks().terms().size())).isZero();
    }

    // IF a failed pass left a half-written table or a results card, THEN "changes nothing" would be untrue.
    @Test
    void autoPrepare_passFails_showsTheErrorPublishesNoResultsAndChangesNothing() {
        chooseModel();
        namesPass.willAnswer(Result.err(AppError.of(ErrorCode.timeout, "Timed out", "The model did not answer.")));
        namesPass.writes(() -> lexicon.replaceHeld(PENTACLE));

        run(vm::autoPrepare);

        assertThat(results()).isNull();
        assertThat(onFx(() -> vm.notice().get().text())).isEqualTo("The model did not answer.");
        assertThat(onFx(() -> vm.notice().get().level())).isEqualTo(GlossaryNotice.Level.ERROR);
        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactlyInAnyOrder(WELL, HALE);
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactly("table");
        assertThat(glossary.added()).isEmpty();
        assertThat(glossary.updated()).isEmpty();
        assertThat(onFx(() -> vm.busy().get())).isFalse();
    }

    // IF a person without a model reached the pass, THEN it would fail on a model that does not exist.
    @Test
    void autoPrepare_noModelChosen_asksNothingOfThePassAndSaysSo() {
        run(vm::autoPrepare);

        assertThat(namesPass.runs()).isEmpty();
        assertThat(onFx(() -> vm.notice().get().level())).isEqualTo(GlossaryNotice.Level.ERROR);
        assertThat(results()).isNull();
    }

    // IF a second model action could start beside the pass, THEN two requests would queue in the one inference gate.
    @Test
    void autoPrepare_otherModelWorkRuns_isRefusedWithoutCallingThePass() {
        chooseModel();
        onFx(() -> tracker.begin(ActivityKind.EXPORT, null));

        run(vm::autoPrepare);

        assertThat(namesPass.runs()).isEmpty();
        assertThat(onFx(() -> vm.notice().get().level())).isEqualTo(GlossaryNotice.Level.ERROR);
    }

    // IF the pass were not a registered activity, THEN the busy card, the title bar chip and the leave question
    // would not know it runs.
    @Test
    void autoPrepare_running_isOneBlockingNamesPassActivity() {
        chooseModel();
        final List<ActivityKind> kinds = new CopyOnWriteArrayList<>();
        final List<Boolean> blocking = new CopyOnWriteArrayList<>();
        namesPass.duringRun(() -> {
            tracker.running().forEach(activity -> {
                kinds.add(activity.kind());
                blocking.add(activity.isBlocking());
            });
        });

        run(vm::autoPrepare);

        assertThat(kinds).containsExactly(ActivityKind.NAMES_PASS);
        assertThat(blocking).containsExactly(true);
        assertThat(onFx(() -> tracker.running())).isEmpty();
    }

    // IF the lock were not taken for the pass, THEN a twenty-minute pass would let the computer sleep mid-way.
    @Test
    void autoPrepare_running_holdsTheComputerAwakeUntilItEnds() {
        final List<String> locks = new CopyOnWriteArrayList<>();
        final List<String> locksWhileRunning = new CopyOnWriteArrayList<>();
        final KeepAwake keepAwake = new KeepAwake() {
            @Override
            public void start() {
                locks.add("start");
            }

            @Override
            public void stop() {
                locks.add("stop");
            }
        };
        final NamesStyleViewModel awake = viewModel(onFx(() -> new ActivityTracker(new StateMirror(), keepAwake)));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> awake.show(PROJECT));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        chooseModel();
        namesPass.duringRun(() -> locksWhileRunning.addAll(locks));

        run(awake::autoPrepare);

        assertThat(locksWhileRunning).containsExactly("start");
        assertThat(locks).containsExactly("start", "stop");
    }

    // IF the estimate were read once and never again, THEN a changed dial would show the old minutes.
    @Test
    void autoEstimate_screenShownThenRefreshed_followsTheNumbersThePortAnswers() {
        namesPass.willEstimate(new NamesEstimate(12, 15, 25));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> vm.show(PROJECT));
        assertThat(onFx(() -> vm.autoEstimate().get())).isEqualTo(new NamesEstimate(12, 15, 25));

        namesPass.willEstimate(new NamesEstimate(40, 30, 60));
        run(vm::refreshAutoEstimate);

        assertThat(onFx(() -> vm.autoEstimate().get())).isEqualTo(new NamesEstimate(40, 30, 60));
        assertThat(namesPass.estimateCalls()).contains(PROJECT);
        assertThat(namesPass.estimateCalls().size()).isGreaterThanOrEqualTo(2);
    }

    // IF a refused estimate left the last one up, THEN the button would promise minutes nobody computed.
    @Test
    void autoEstimate_portRefuses_isEmpty() {
        namesPass.willEstimate(new NamesEstimate(12, 15, 25));
        run(vm::refreshAutoEstimate);
        assertThat(onFx(() -> vm.autoEstimate().get())).isEqualTo(new NamesEstimate(12, 15, 25));
        namesPass.willFailToEstimate(AppError.of(ErrorCode.validation, "No book", "The book is not open."));

        run(vm::refreshAutoEstimate);

        assertThat(onFx(() -> vm.autoEstimate().get())).isNull();
    }
}
