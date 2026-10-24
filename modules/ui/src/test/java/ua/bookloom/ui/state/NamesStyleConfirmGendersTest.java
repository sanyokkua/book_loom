package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ScriptedChatModelFactory;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ScriptedLexiconService;
import ua.bookloom.ui.ScriptedNamesPass;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;

/** "Confirm suggested genders": every suggested character gender becomes the person's, and the change can be undone. */
class NamesStyleConfirmGendersTest extends FxTestBase {

    private static final String PROJECT = "p1";
    private static final GlossaryEntry ANNE = new GlossaryEntry(
                    "e1", PROJECT, "Anne", "Анна", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.FEMALE);
    private static final GlossaryEntry BORIS = new GlossaryEntry(
                    "e2", PROJECT, "Boris", "Борис", TermType.CHARACTER, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.MALE);
    private static final GlossaryEntry CARL =
            new GlossaryEntry("e3", PROJECT, "Carl", "Карл", TermType.CHARACTER, Gender.MALE, false);
    private static final GlossaryEntry DORA =
            new GlossaryEntry("e4", PROJECT, "Dora", "Дора", TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final GlossaryEntry RIVER = new GlossaryEntry(
                    "e5", PROJECT, "River", "Ріка", TermType.PLACE, Gender.UNKNOWN, false)
            .withSuggestedGender(Gender.FEMALE);

    private ScriptedGlossaryService glossary;
    private NamesStyleViewModel vm;

    @Override
    public void start(final Stage stage) {
        // The toolkit is all these tests need; the view model has no scene.
    }

    @BeforeEach
    void setUp() {
        glossary = new ScriptedGlossaryService();
        useViewModelIn(Locale.ENGLISH);
        glossary.willAnswer(Result.ok(List.of(ANNE, BORIS, CARL, DORA, RIVER)));
        run(() -> vm.show(PROJECT));
    }

    private void useViewModelIn(final Locale locale) {
        final Injector injector = UiTestInjector.create(locale);
        vm = onFx(() -> new NamesStyleViewModel(
                glossary,
                new ScriptedLexiconService(),
                new ScriptedNamesPass(),
                ScriptedChatModelFactory.ok(),
                injector.getInstance(SettingsViewModel.class),
                new DirectExecutor(),
                injector.getInstance(Messages.class),
                injector.getInstance(ActivityTracker.class),
                () -> "new"));
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF a refused confirmation kept the service's English words, THEN a Ukrainian screen would report it in English.
    @Test
    void confirmGenders_refusedInUkrainian_reportsTheFailureInUkrainian() {
        useViewModelIn(Locale.forLanguageTag("uk"));
        glossary.willAnswer(Result.ok(List.of(ANNE, BORIS, CARL, DORA, RIVER)));
        run(() -> vm.show(PROJECT));
        glossary.willAnswer(Result.err(AppError.of(ErrorCode.validation, "Refused", "Not stored.")));

        run(vm::confirmGenders);

        assertThat(onFx(() -> vm.notice().get().text()))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }

    @Test
    void suggestedGenderCount_mixedRows_countsOnlySuggestedCharacters() {
        assertThat(onFx(() -> vm.suggestedGenderCount().get())).isEqualTo(2);
    }

    // IF the action only relabelled the rows, THEN the next run would still treat the genders as guesses.
    @Test
    void confirmGenders_suggestedCharacters_storeEachGenderAsThePersonsAndLeaveTheOthers() {
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));

        run(vm::confirmGenders);

        assertThat(glossary.updated())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Anne", Gender.FEMALE, GenderOrigin.PERSON),
                        org.assertj.core.groups.Tuple.tuple("Boris", Gender.MALE, GenderOrigin.PERSON));
        assertThat(onFx(() -> vm.suggestedGenderCount().get())).isZero();
    }

    @Test
    void confirmGenders_done_publishesOneChangedRowPerCharacter() {
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));

        run(vm::confirmGenders);

        final ChangeResults results = onFx(() -> vm.results().get());
        assertThat(results.operation()).isEqualTo(ChangeOperation.CONFIRM_GENDERS);
        assertThat(results.count(ChangeKind.CHANGED)).isEqualTo(2);
    }

    // IF Undo did not put the guess back, THEN a confirmation could never be taken back.
    @Test
    void undoAll_afterConfirming_putsTheSuggestedGendersBack() {
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(BORIS.withGender(Gender.MALE)));
        run(vm::confirmGenders);
        glossary.willAnswer(Result.ok(ANNE));
        glossary.willAnswer(Result.ok(BORIS));

        run(() -> vm.results().get().undoAll());

        assertThat(glossary.updated().subList(2, 4)).containsExactly(ANNE, BORIS);
        assertThat(onFx(() -> vm.suggestedGenderCount().get())).isEqualTo(2);
    }

    @Test
    void confirmGenders_nothingSuggested_callsNothingAndPublishesNothing() {
        glossary.willAnswer(Result.ok(List.of(CARL, DORA, RIVER)));
        run(() -> vm.show(PROJECT));

        run(vm::confirmGenders);

        assertThat(glossary.updated()).isEmpty();
        assertThat(onFx(() -> vm.results().get())).isNull();
    }

    // IF Undo restored only the rows the action asked for, THEN a character's other spellings stayed the person's.
    @Test
    void undoAll_characterWithAnotherSpelling_putsBothRowsBack() {
        final GlossaryEntry ember = new GlossaryEntry(
                        "e6", PROJECT, "Ember", "Ембер", TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.FEMALE);
        final GlossaryEntry embers = new GlossaryEntry(
                        "e7", PROJECT, "Embers", "Ембер", TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.FEMALE);
        glossary.willAnswer(Result.ok(List.of(ember, embers)));
        run(() -> vm.show(PROJECT));
        glossary.willAnswer(Result.ok(ember.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(embers.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(List.of(ember.withGender(Gender.FEMALE), embers.withGender(Gender.FEMALE))));
        run(vm::confirmGenders);
        assertThat(onFx(() -> vm.results().get()).count(ChangeKind.CHANGED)).isEqualTo(2);
        glossary.willAnswer(Result.ok(ember));
        glossary.willAnswer(Result.ok(embers));

        run(() -> vm.results().get().undoAll());

        assertThat(glossary.updated()).endsWith(ember, embers);
        assertThat(onFx(() -> vm.suggestedGenderCount().get())).isEqualTo(2);
    }

    // IF the glossary shared the gender with a spelling nobody asked about, THEN the results must list that row too.
    @Test
    void confirmGenders_serviceChangesAnotherSpelling_reportsThatRowToo() {
        final GlossaryEntry ember = new GlossaryEntry(
                        "e6", PROJECT, "Ember", "Ембер", TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.FEMALE);
        final GlossaryEntry embers =
                new GlossaryEntry("e7", PROJECT, "Embers", "Ембер", TermType.CHARACTER, Gender.FEMALE, false);
        glossary.willAnswer(Result.ok(List.of(ember, embers)));
        run(() -> vm.show(PROJECT));
        glossary.willAnswer(Result.ok(ember.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.ok(List.of(ember.withGender(Gender.FEMALE), embers.withGender(Gender.FEMALE))));

        run(vm::confirmGenders);

        assertThat(onFx(() -> vm.results().get()).count(ChangeKind.CHANGED)).isEqualTo(2);
    }

    // IF a refusal after some rows were stored were dropped, THEN the person would think every gender was confirmed.
    @Test
    void confirmGenders_refusedAfterOneRow_reportsTheStoredRowAndSaysItStopped() {
        glossary.willAnswer(Result.ok(ANNE.withGender(Gender.FEMALE)));
        glossary.willAnswer(Result.err(AppError.of(ErrorCode.internal, "Refused", "Boris could not be stored.")));

        run(vm::confirmGenders);

        assertThat(onFx(() -> vm.results().get()).count(ChangeKind.CHANGED)).isEqualTo(1);
        final GlossaryNotice notice = onFx(() -> vm.notice().get());
        assertThat(notice.level()).isEqualTo(GlossaryNotice.Level.ERROR);
        assertThat(notice.text()).isEqualTo("Boris could not be stored.");
    }
}
