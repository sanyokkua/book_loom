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
        final Injector injector = UiTestInjector.create(Locale.ENGLISH);
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
        glossary.willAnswer(Result.ok(List.of(ANNE, BORIS, CARL, DORA, RIVER)));
        run(() -> vm.show(PROJECT));
    }

    private void run(final Runnable action) {
        interact(action);
        WaitForAsyncUtils.waitForFxEvents();
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
}
