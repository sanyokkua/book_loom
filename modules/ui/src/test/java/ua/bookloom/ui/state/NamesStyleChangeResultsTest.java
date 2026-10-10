package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/**
 * What every Names &amp; style operation reports: the exact rows it added, removed and changed, the marks it leaves
 * on them, and how each row, or all of them, is put back through the services.
 */
class NamesStyleChangeResultsTest extends FxTestBase {

    private static final String PROJECT = "p1";
    private static final GlossaryEntry WELL = entry("e1", "Well", TermType.OTHER, Gender.UNKNOWN);
    private static final GlossaryEntry HALE = entry("e2", "Hale", TermType.OTHER, Gender.UNKNOWN);
    private static final GlossaryEntry MID = entry("e3", "Mid", TermType.CHARACTER, Gender.MALE);

    private ScriptedGlossaryService glossary;
    private ScriptedLexiconService lexicon;
    private SettingsViewModel settings;
    private NamesStyleViewModel vm;
    private ActivityTracker tracker;
    private Messages messages;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        glossary = new ScriptedGlossaryService();
        lexicon = new ScriptedLexiconService();
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        settings = injector.getInstance(SettingsViewModel.class);
        tracker = injector.getInstance(ActivityTracker.class);
        messages = injector.getInstance(Messages.class);
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                lexicon,
                ScriptedChatModelFactory.ok(),
                settings,
                new DirectExecutor(),
                messages,
                tracker,
                () -> "new"));
        glossary.willAnswer(Result.ok(List.of(WELL, HALE)));
        run(() -> vm.show(PROJECT));
        interact(() -> settings.model().set("gemma3:12b"));
    }

    private static GlossaryEntry entry(final String id, final String term, final TermType type, final Gender gender) {
        return new GlossaryEntry(id, PROJECT, term, null, type, gender, false);
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ChangeResults results() {
        return onFx(() -> vm.results().get());
    }

    private GlossaryReviewReport reviewThatRemovesHaleAndTypesWell() {
        final GlossaryEntry typed = entry("e1", "Well", TermType.CHARACTER, Gender.FEMALE);
        final EntryChanges<GlossaryEntry> changes = new EntryChanges<>(
                List.of(),
                List.of(new EntryChanges.Removal<>(HALE, "not a name")),
                List.of(new EntryChanges.Change<>(WELL, typed, null)));
        return new GlossaryReviewReport(1, 1, 0, List.of(typed), changes);
    }

    // IF the review's results listed counts only, THEN the person could not see which rows went or why.
    @Test
    void review_removesAndRewrites_publishesOneRowPerChangeWithBeforeAfterAndReason() {
        glossary.willAnswer(Result.ok(reviewThatRemovesHaleAndTypesWell()));

        run(vm::review);

        final ChangeResults shown = results();
        assertThat(shown.operation()).isEqualTo(ChangeOperation.NAME_REVIEW);
        assertThat(shown.outcome()).isEqualTo(ChangeResults.Outcome.CHANGED);
        assertThat(shown.rows())
                .extracting(ChangeRow::kind, ChangeRow::term, ChangeRow::before, ChangeRow::after, ChangeRow::reason)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ChangeKind.REMOVED, "Hale", "No target · other", "—", "not a name"),
                        org.assertj.core.groups.Tuple.tuple(
                                ChangeKind.CHANGED,
                                "Well",
                                "No target · other",
                                "No target · character · female",
                                null));
    }

    // IF a changed row left no mark, THEN "Changed in last run" would have nothing to show.
    @Test
    void review_rewritesARow_marksItAndNotTheRemovedOne() {
        glossary.willAnswer(Result.ok(reviewThatRemovesHaleAndTypesWell()));

        run(vm::review);

        assertThat(onFx(() -> vm.marks().glossary().isMarked("e1"))).isTrue();
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e2"))).isFalse();
    }

    @Test
    void modelScan_findsNames_publishesAddedRowsAndMarksThem() {
        glossary.willAnswer(Result.ok(List.of(MID)));

        run(vm::modelScan);

        assertThat(results().rows())
                .extracting(ChangeRow::kind, ChangeRow::term)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChangeKind.ADDED, "Mid"));
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e3"))).isTrue();
        assertThat(onFx(() -> vm.marks().glossary().size())).isEqualTo(1);
    }

    @Test
    void modelScan_findsNothing_publishesNothingChanged() {
        glossary.willAnswer(Result.ok(List.of()));

        run(vm::modelScan);

        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.NOTHING);
        assertThat(results().rows()).isEmpty();
    }

    @Test
    void modelScan_fails_publishesNoResults() {
        glossary.willAnswer(Result.err(AppError.of(ErrorCode.timeout, "Timed out", "The model did not answer.")));

        run(vm::modelScan);

        assertThat(results()).isNull();
        assertThat(onFx(() -> vm.notice().get().text())).isEqualTo("The model did not answer.");
    }

    // IF a stopped operation said nothing, THEN the person could not tell a stop from a hang.
    @Test
    void stopModel_whileTheScanWaits_publishesAStoppedResultWithNoRows() {
        final HeldExecutor held = new HeldExecutor();
        final NamesStyleViewModel stoppable = viewModelOver(held);
        run(stoppable::modelScan);

        run(stoppable::stopModel);

        final ChangeResults shown = onFx(() -> stoppable.results().get());
        assertThat(shown.outcome()).isEqualTo(ChangeResults.Outcome.STOPPED);
        assertThat(shown.rows()).isEmpty();
    }

    private NamesStyleViewModel viewModelOver(final HeldExecutor executor) {
        final NamesStyleViewModel model = onFx(() -> new NamesStyleViewModel(
                glossary, lexicon, ScriptedChatModelFactory.ok(), settings, executor, messages, tracker, () -> "new"));
        glossary.willAnswer(Result.ok(List.of(WELL)));
        run(() -> model.show(PROJECT));
        return model;
    }

    // IF the next operation kept the last one's marks, THEN "Changed in last run" would mix two runs.
    @Test
    void nextOperation_starts_dropsTheLastResultsAndMarks() {
        glossary.willAnswer(Result.ok(List.of(MID)));
        run(vm::modelScan);
        glossary.willAnswer(Result.ok(List.of()));

        run(vm::modelScan);

        assertThat(results().outcome()).isEqualTo(ChangeResults.Outcome.NOTHING);
        assertThat(onFx(() -> vm.marks().glossary().size())).isZero();
    }

    // IF reverting an added row left it in the table, THEN Revert would do nothing the person can see.
    @Test
    void revert_addedRow_removesTheEntryThroughTheServiceAndUnmarksIt() {
        glossary.willAnswer(Result.ok(List.of(MID)));
        run(vm::modelScan);
        glossary.willAnswer(Result.ok(true));

        run(() -> results().revert(results().rows().getFirst().id()));

        assertThat(glossary.calls()).contains("remove(p1, e3)");
        assertThat(onFx(() -> vm.rows())).extracting(GlossaryEntry::id).containsExactly("e1", "e2");
        assertThat(onFx(() -> vm.marks().glossary().size())).isZero();
        assertThat(onFx(() -> List.copyOf(results().reverted()))).hasSize(1);
    }

    @Test
    void revert_removedRow_addsTheEntryBackWithItsOwnId() {
        glossary.willAnswer(Result.ok(reviewThatRemovesHaleAndTypesWell()));
        run(vm::review);
        glossary.willAnswer(Result.ok(HALE));

        run(() -> results().revert(0));

        assertThat(glossary.added()).containsExactly(HALE);
        assertThat(onFx(() -> vm.rows())).extracting(GlossaryEntry::id).contains("e2");
    }

    @Test
    void revert_changedRow_setsTheEntryBackToWhatItWas() {
        glossary.willAnswer(Result.ok(reviewThatRemovesHaleAndTypesWell()));
        run(vm::review);
        glossary.willAnswer(Result.ok(WELL));

        run(() -> results().revert(1));

        assertThat(glossary.updated()).containsExactly(WELL);
        assertThat(onFx(() -> vm.rows())).containsExactly(WELL);
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e1"))).isFalse();
    }

    // IF Undo all stopped at the first row or skipped one, THEN the glossary would be left half-reverted.
    @Test
    void undoAll_everyRow_revertsThemInOrderAndLeavesNothingToRevert() {
        glossary.willAnswer(Result.ok(reviewThatRemovesHaleAndTypesWell()));
        run(vm::review);
        glossary.willAnswer(Result.ok(HALE));
        glossary.willAnswer(Result.ok(WELL));

        run(() -> results().undoAll());

        assertThat(glossary.calls()).endsWith("add(Hale)", "update(Well)");
        assertThat(onFx(() -> results().hasRowsToRevert())).isFalse();
        assertThat(onFx(() -> vm.rows())).containsExactlyInAnyOrder(WELL, HALE);
    }

    @Test
    void revert_serviceRefuses_keepsTheRowAndReportsInPlace() {
        glossary.willAnswer(Result.ok(List.of(MID)));
        run(vm::modelScan);
        glossary.willAnswer(
                Result.err(AppError.of(ErrorCode.internal, "Failed", "The glossary could not be reached.")));

        run(() -> results().revert(0));

        assertThat(onFx(() -> List.copyOf(results().reverted()))).isEmpty();
        assertThat(onFx(() -> vm.rows())).extracting(GlossaryEntry::id).contains("e3");
        assertThat(onFx(() -> vm.notice().get().text())).isEqualTo("The glossary could not be reached.");
    }

    // The recurring terms: a model scan adds rows, a review drops them, a suggestion sets a rendering.
    @Test
    void scanTerms_modelFinds_listsAddedTermsAndRevertRemovesThem() {
        lexicon.modelWillFind(LexiconEntry.of(PROJECT, "pentacle"));

        run(() -> vm.recurring().scan());

        assertThat(results().operation()).isEqualTo(ChangeOperation.TERM_SCAN);
        assertThat(results().rows())
                .extracting(ChangeRow::kind, ChangeRow::term)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChangeKind.ADDED, "pentacle"));
        assertThat(onFx(() -> vm.marks().terms().isMarked("pentacle"))).isTrue();

        run(() -> results().revert(0));

        assertThat(lexicon.calls()).contains("remove(pentacle)");
        assertThat(onFx(() -> vm.recurring().rows())).isEmpty();
    }

    @Test
    void reviewTerms_modelDrops_listsRemovedTermAndRevertRestoresIt() {
        lexicon.holds(LexiconEntry.of(PROJECT, "pentacle"), LexiconEntry.of(PROJECT, "table"));
        run(() -> vm.show(PROJECT));
        lexicon.reviewWillDrop("table");

        run(() -> vm.recurring().review());

        assertThat(results().operation()).isEqualTo(ChangeOperation.TERM_REVIEW);
        assertThat(results().rows())
                .extracting(ChangeRow::kind, ChangeRow::term)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChangeKind.REMOVED, "table"));

        run(() -> results().revert(0));

        assertThat(lexicon.calls()).contains("restore(table)");
        assertThat(onFx(() -> vm.recurring().rows()))
                .extracting(LexiconEntry::term)
                .containsExactlyInAnyOrder("pentacle", "table");
    }

    @Test
    void suggestRenderings_modelSuggests_listsTheChangeFromNothingToTheRendering() {
        lexicon.holds(LexiconEntry.of(PROJECT, "master"));
        run(() -> vm.show(PROJECT));
        lexicon.willSuggest(LexiconEntry.of(PROJECT, "master").withSuggested("господар"));

        run(() -> vm.recurring().translate());

        assertThat(results().rows())
                .extracting(ChangeRow::kind, ChangeRow::before, ChangeRow::after)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChangeKind.CHANGED, "—", "господар"));
        assertThat(results().operation()).isEqualTo(ChangeOperation.TERM_TRANSLATE);
    }

    /** Runs the screen's opening load at once and keeps every later task waiting, as a slow model would. */
    private static final class HeldExecutor extends AbstractExecutorService {

        private final List<Runnable> held = new CopyOnWriteArrayList<>();
        private boolean loaded;

        @Override
        public Future<?> submit(final Runnable task) {
            execute(task);
            return new CompletableFuture<Void>();
        }

        @Override
        public void execute(final Runnable command) {
            if (loaded) {
                held.add(command);
            } else {
                loaded = true;
                command.run();
            }
        }

        @Override
        public void shutdown() {
            // Nothing to stop.
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit) {
            return true;
        }
    }
}
