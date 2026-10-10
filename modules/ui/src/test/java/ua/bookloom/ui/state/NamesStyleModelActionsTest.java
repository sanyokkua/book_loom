package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.EntryChanges;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The glossary's model actions on the names and style state: the review, the waiting line and the stop. */
class NamesStyleModelActionsTest extends FxTestBase {

    private static final String PROJECT = "p1";

    private ScriptedGlossaryService glossary;
    private SettingsViewModel settings;
    private Messages messages;
    private ActivityTracker activities;
    private final List<String> lines = new CopyOnWriteArrayList<>();

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        glossary = new ScriptedGlossaryService();
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
        settings = injector.getInstance(SettingsViewModel.class);
        messages = injector.getInstance(Messages.class);
        activities = injector.getInstance(ActivityTracker.class);
    }

    private NamesStyleViewModel viewModel(final ExecutorService executor) {
        final NamesStyleViewModel vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                new ScriptedLexiconService(),
                ScriptedChatModelFactory.ok(),
                settings,
                executor,
                messages,
                activities,
                () -> "new"));
        interact(() -> vm.notice().addListener((observed, was, now) -> {
            if (now != null) {
                lines.add(now.text());
            }
        }));
        glossary.willAnswer(Result.ok(List.of(well(), hale(TermType.OTHER, Gender.UNKNOWN))));
        run(() -> vm.show(PROJECT));
        return vm;
    }

    private static GlossaryEntry well() {
        return new GlossaryEntry("e1", PROJECT, "Well", null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    private static GlossaryEntry hale(final TermType type, final Gender gender) {
        return new GlossaryEntry("e2", PROJECT, "Hale", null, type, gender, false);
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a review's outcome were not shown, THEN rows would vanish with no word of why.
    @Test
    void review_answersReport_showsTheStoredRowsAndTheCounts() {
        final NamesStyleViewModel vm = viewModel(new DirectExecutor());
        interact(() -> settings.model().set("gemma3:12b"));
        final GlossaryEntry typed = hale(TermType.CHARACTER, Gender.MALE);
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(1, 1, 2, List.of(typed))));

        run(vm::review);

        assertThat(glossary.calls()).contains("review(p1)");
        assertThat(onFx(() -> List.copyOf(vm.rows()))).containsExactly(typed);
        assertThat(onFx(() -> vm.notice().get().text()))
                .isEqualTo("Model review done: 1 row removed, 1 row updated, 2 targets suggested.");
        assertThat(onFx(() -> vm.busy().get())).isFalse();
    }

    // IF Translate reused Review's report, THEN a target suggestion would be sold as a verdict on the names.
    @Test
    void translate_modelSuggestsATarget_publishesTheChangedRowAndShowsItInTheTable() {
        final NamesStyleViewModel vm = viewModel(new DirectExecutor());
        interact(() -> settings.model().set("gemma3:12b"));
        final GlossaryEntry translated =
                new GlossaryEntry("e1", PROJECT, "Well", "Колодязь", TermType.OTHER, Gender.UNKNOWN, false);
        glossary.willAnswer(Result.ok(new EntryChanges<>(
                List.of(), List.of(), List.of(new EntryChanges.Change<>(well(), translated, null)))));

        run(vm::translate);

        final ChangeResults shown = onFx(() -> vm.results().get());
        assertThat(glossary.calls()).contains("translate(p1)");
        assertThat(shown.operation()).isEqualTo(ChangeOperation.NAME_TRANSLATE);
        assertThat(shown.rows())
                .extracting(ChangeRow::kind, ChangeRow::term, ChangeRow::after)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(ChangeKind.CHANGED, "Well", "Колодязь · other"));
        assertThat(onFx(() -> List.copyOf(vm.rows()))).contains(translated);
        assertThat(onFx(() -> vm.marks().glossary().isMarked("e1"))).isTrue();
    }

    // IF a translate were not an activity of its own, THEN the title bar would call it a "scan".
    @Test
    void translate_whileItWaits_registersTranslatingNames() {
        final NamesStyleViewModel vm = viewModel(new HeldExecutor());
        interact(() -> settings.model().set("gemma3:12b"));

        run(vm::translate);

        assertThat(onFx(() -> activities.running().stream().map(Activity::kind).toList()))
                .containsExactly(ActivityKind.GLOSSARY_TRANSLATE);
    }

    // IF Translate ran with no model chosen, THEN it would fail on a request nobody can name.
    @Test
    void translate_noModelChosen_saysSoAndAsksNothing() {
        final NamesStyleViewModel vm = viewModel(new DirectExecutor());

        run(vm::translate);

        assertThat(onFx(() -> vm.notice().get().text()))
                .isEqualTo("Choose a model in the provider settings to translate the names.");
        assertThat(glossary.calls()).containsExactly("entries(p1)");
    }

    // IF the review ran with no model chosen, THEN it would fail on a request nobody can name.
    @Test
    void review_noModelChosen_saysSoAndAsksNothing() {
        final NamesStyleViewModel vm = viewModel(new DirectExecutor());

        run(vm::review);

        assertThat(onFx(() -> vm.notice().get().text()))
                .isEqualTo("Choose a model in the provider settings to review the names.");
        assertThat(glossary.calls()).containsExactly("entries(p1)");
    }

    // IF a long model scan showed nothing while it waited, THEN the person could not tell it from a hang.
    @Test
    void modelScan_requestGoesOut_showsTheWaitingLineWithItsAttempt() {
        final NamesStyleViewModel vm = viewModel(new DirectExecutor());
        interact(() -> settings.model().set("gemma3:12b"));
        glossary.willReport(new ModelCallStarted(null, CallKind.PRESCAN, List.of(), 1, 2, Duration.ofMinutes(2), null));
        glossary.willAnswer(Result.ok(List.of()));

        run(vm::modelScan);

        assertThat(lines).contains("Waiting for the model · request 1 · attempt 1 of 2");
    }

    // IF Stop left the buttons disabled or said nothing, THEN a stuck request would hold the screen.
    @Test
    void stopModel_whileTheReviewWaits_endsTheWaitAndSaysTheGlossaryIsUnchanged() {
        final HeldExecutor held = new HeldExecutor();
        final NamesStyleViewModel vm = viewModel(held);
        interact(() -> settings.model().set("gemma3:12b"));
        run(vm::review);
        assertThat(onFx(() -> vm.busy().get())).isTrue();

        run(vm::stopModel);

        assertThat(onFx(() -> vm.busy().get())).isFalse();
        assertThat(onFx(() -> vm.notice().get().text()))
                .isEqualTo("The model request was stopped. The glossary was not changed.");
        assertThat(glossary.calls()).doesNotContain("review(p1)");
    }

    // IF a stopped review's late answer ended the next one, THEN its Stop would vanish while the model still works.
    @Test
    void stopModel_stoppedReviewAnswersAfterTheNextStarted_keepsTheNextOneRunning() {
        final LateExecutor late = new LateExecutor();
        final NamesStyleViewModel vm = viewModel(late);
        interact(() -> settings.model().set("gemma3:12b"));
        run(vm::review);
        run(vm::stopModel);
        run(vm::review);
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(0, 0, List.of())));

        run(late.held.getFirst());

        assertThat(onFx(() -> vm.busy().get())).isTrue();
        assertThat(onFx(() -> List.copyOf(vm.rows()))).hasSize(2);
    }

    /**
     * Runs the screen's opening load at once and holds every later task; its futures ignore a cancel, as a request
     * already on the wire does.
     */
    private static final class LateExecutor extends AbstractExecutorService {

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

    /** Runs the screen's opening load at once and keeps every later task waiting, as a slow model would. */
    private static final class HeldExecutor extends AbstractExecutorService {

        private int executed;

        @Override
        public void execute(final Runnable command) {
            executed++;
            if (executed == 1) {
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
