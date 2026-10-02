package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BatchStarted;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** The model's suggested targets on the names and style state: counted, accepted one by one or all at once. */
class NamesStyleSuggestionsTest extends FxTestBase {

    private static final String PROJECT = "p1";
    private static final GlossaryEntry HALE = new GlossaryEntry(
                    "e1", PROJECT, "Hale", null, TermType.CHARACTER, Gender.MALE, false)
            .withSuggestedTarget("Гейл");
    private static final GlossaryEntry WALES = new GlossaryEntry(
                    "e2", PROJECT, "Wales", null, TermType.PLACE, Gender.UNKNOWN, false)
            .withSuggestedTarget("Уельс");
    private static final GlossaryEntry MILTON =
            new GlossaryEntry("e3", PROJECT, "Milton", "Мілтон", TermType.PLACE, Gender.UNKNOWN, false);

    private ScriptedGlossaryService glossary;
    private SettingsViewModel settings;
    private NamesStyleViewModel vm;
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
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                ScriptedChatModelFactory.ok(),
                settings,
                new DirectExecutor(),
                injector.getInstance(Messages.class),
                injector.getInstance(ActivityTracker.class),
                () -> "new"));
        interact(() -> vm.notice().addListener((observed, was, now) -> lines.add(now == null ? "" : now.text())));
        glossary.willAnswer(Result.ok(List.of(HALE, WALES, MILTON)));
        run(() -> vm.show(PROJECT));
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    void suggestedCount_rowsWithSuggestions_countsOnlyThose() {
        assertThat(onFx(() -> vm.suggestedCount().get())).isEqualTo(2);
    }

    @Test
    void accept_suggestedRow_storesTheSameTargetAsThePersonsAndRecounts() {
        glossary.willAnswer(Result.ok(HALE.accepted()));

        run(() -> vm.accept("e1"));

        assertThat(glossary.updated())
                .extracting(GlossaryEntry::target, GlossaryEntry::origin)
                .containsExactly(tuple("Гейл", TargetOrigin.PERSON));
        assertThat(onFx(() -> vm.suggestedCount().get())).isEqualTo(1);
    }

    @Test
    void acceptAll_twoSuggestions_storesEachAsThePersonsAndLeavesTheOtherRowAlone() {
        glossary.willAnswer(Result.ok(HALE.accepted()));
        glossary.willAnswer(Result.ok(WALES.accepted()));

        run(vm::acceptAll);

        assertThat(glossary.updated()).containsExactly(HALE.accepted(), WALES.accepted());
        assertThat(onFx(() -> vm.suggestedCount().get())).isZero();
    }

    // IF the suggestion stage showed only "request 3", THEN a long glossary would give no sense of how far it is.
    @Test
    void review_suggestionBatchOut_showsHowManyBatchesAreDone() {
        interact(() -> settings.model().set("gemma3:12b"));
        glossary.willReport(new BatchStarted(CallKind.SUGGEST_TARGETS, 2, 5));
        glossary.willReport(
                new ModelCallStarted(null, CallKind.SUGGEST_TARGETS, List.of(), 1, 2, Duration.ofMinutes(2), null));
        glossary.willAnswer(Result.ok(new GlossaryReviewReport(0, 0, 3, List.of(HALE, WALES, MILTON))));

        run(vm::review);

        assertThat(lines).contains("Suggesting renderings 2/5");
        assertThat(lines).doesNotContain("Waiting for the model · request 1 · attempt 1 of 2");
        assertThat(lines.getLast())
                .isEqualTo("Model review done: 0 rows removed, 0 rows updated, 3 targets suggested.");
    }
}
