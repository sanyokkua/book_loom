package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.NarratorDialog;

/**
 * Start translation on a book found to be told in the first person asks once who narrates: male, female or not stated
 * starts the run, and Back or Escape returns without starting. The answer is written to the brief.
 */
class NamesStyleNarratorTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry NAME =
            new GlossaryEntry("e1", "p1", "Nathaniel", "Натаніель", TermType.CHARACTER, Gender.MALE, false);
    private static final String CARD = "narrator-card";

    private void showNamesStyle(final NarratorHint hint) throws Exception {
        projects.on(BOOK, Result.ok(BookFixtures.withNarratorHint(BookFixtures.frankensteinImport(), hint)));
        openImport();
        openBook(BOOK);
        chooseTarget();
        ThemeTestSupport.onFx(() -> {
            injector.getInstance(ua.bookloom.ui.state.SettingsViewModel.class)
                    .model()
                    .set(MODEL);
            return null;
        });
        glossary.willAnswer(Result.ok(List.of(NAME)));
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private boolean isAsking() {
        final Node card = scene.getRoot().lookup("#" + CARD);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private Narrator narrator() {
        return ThemeTestSupport.onFx(() -> injector.getInstance(ua.bookloom.ui.state.CurrentProject.class)
                .brief()
                .get()
                .narrator());
    }

    // IF the start went ahead silently, THEN a male narrator would be drafted with feminine past forms.
    @Test
    void start_firstPersonBookNarratorNotStated_asksWhoNarratesFirst() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());

        onFx(() -> button("names-style-start").fire());

        assertThat(isAsking()).isTrue();
        assertThat(textsUnder(required(CARD)))
                .contains(
                        "Who narrates?",
                        "Back",
                        "Not stated — start anyway",
                        "Female",
                        "Male",
                        "The book is told in the first person. Choose the narrator's gender so the translation's verbs"
                                + " and adjectives agree with it.");
        assertThat(engine.requests()).isEmpty();
    }

    @Test
    void male_pressed_setsAFirstPersonMaleNarratorAndStartsTheRun() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("narrator-male").fire());

        job.awaitRunStarted();
        assertThat(narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
        assertThat(isAsking()).isFalse();
        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.TRANSLATING);
    }

    // IF the run started before the save landed, THEN the engine would read the old, unstated narrator from the
    // project.
    @Test
    void male_pressedWhileTheBriefSaveIsSlow_startsTheRunOnlyAfterTheStoredBriefHasTheNarrator() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        final int savedBefore = projects.briefs().size();
        projects.holdBriefSaves();
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("narrator-male").fire());

        assertThat(engine.requests()).isEmpty();
        assertThat(projects.briefs()).hasSize(savedBefore);
        projects.releaseBriefSaves();
        job.awaitRunStarted();
        assertThat(projects.briefs().getLast().narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
    }

    @Test
    void enter_onTheQuestion_defaultsToNotStatedNeverToMale() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        final boolean[] defaults = ThemeTestSupport.onFx(() -> new boolean[] {
            ((javafx.scene.control.Button) scene.getRoot().lookup("#narrator-unknown")).isDefaultButton(),
            ((javafx.scene.control.Button) scene.getRoot().lookup("#narrator-male")).isDefaultButton(),
            ((javafx.scene.control.Button) scene.getRoot().lookup("#narrator-female")).isDefaultButton()
        });

        assertThat(defaults).containsExactly(true, false, false);
    }

    @Test
    void female_pressed_setsAFirstPersonFemaleNarratorAndStartsTheRun() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("narrator-female").fire());

        job.awaitRunStarted();
        assertThat(narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.FEMALE));
    }

    @Test
    void notStated_pressed_startsTheRunAndLeavesTheGenderUnknown() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("narrator-unknown").fire());

        job.awaitRunStarted();
        assertThat(narrator().gender()).isEqualTo(Gender.UNKNOWN);
        assertThat(engine.requests()).hasSize(1);
    }

    @Test
    void back_pressed_startsNothingAndStaysOnTheNames() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        onFx(() -> button("narrator-back").fire());

        assertThat(isAsking()).isFalse();
        assertThat(engine.requests()).isEmpty();
        assertThat(ThemeTestSupport.onFx(() -> navigator.currentView().get())).isEqualTo(ViewNames.NAMES_STYLE);
    }

    @Test
    void escape_pressedOnTheQuestion_startsNothingAndAsksAgainAtTheNextStart() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> button("names-style-start").fire());

        onFx(() -> scene.getRoot()
                .lookup("#" + CARD)
                .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));
        onFx(() -> button("names-style-start").fire());

        assertThat(engine.requests()).isEmpty();
        assertThat(isAsking()).isTrue();
    }

    @Test
    void start_genderAlreadyChosenOnTheBrief_startsWithoutAsking() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> injector.getInstance(ua.bookloom.ui.state.BookBriefViewModel.class)
                .setFirstPersonNarrator(Gender.FEMALE));

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
    }

    @Test
    void start_thirdPersonBook_startsWithoutAsking() throws Exception {
        showNamesStyle(new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of()));

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
    }

    @Test
    void startAfterAsking_answeredOnce_runsAtOnceTheNextTime() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        final NarratorDialog dialog = injector.getInstance(NarratorDialog.class);
        final int[] started = {0};
        onFx(() -> dialog.startAfterAsking(() -> started[0]++));
        onFx(() -> button("narrator-unknown").fire());

        onFx(() -> dialog.startAfterAsking(() -> started[0]++));

        assertThat(started[0]).isEqualTo(2);
    }

    @Test
    void translatingStart_firstPersonBookNarratorNotStated_asksToo() throws Exception {
        showNamesStyle(BookFixtures.firstPersonHint());
        onFx(() -> shell.activate(ViewNames.TRANSLATING));

        onFx(() -> button("translating-start").fire());

        assertThat(isAsking()).isTrue();
        assertThat(engine.requests()).isEmpty();
    }

    @Test
    void start_ukrainianLocale_asksInUkrainian() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        showNamesStyle(BookFixtures.firstPersonHint());

        onFx(() -> button("names-style-start").fire());

        assertThat(textsUnder(required(CARD)))
                .contains("Хто розповідає?", "Назад", "Не вказано — почати все одно", "Жіноча", "Чоловіча");
    }
}
