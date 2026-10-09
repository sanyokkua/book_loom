package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.UnknownGender;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.dialog.CharacterGendersDialog;

/**
 * Start translation lists the characters the book names often whose gender is still unknown: Start anyway (what Enter
 * chooses) goes on with them unknown, Back returns without starting, and the question is asked once per book.
 */
class CharacterGendersStartTest extends TranslatingScreenTestBase {

    private static final GlossaryEntry NAME =
            new GlossaryEntry("e1", "p1", "Chrome", "Хром", TermType.CHARACTER, Gender.UNKNOWN, false);
    private static final String CARD = "genders-card";

    private void showNamesStyle(final List<UnknownGender> unknown) throws Exception {
        projects.on(
                BOOK,
                Result.ok(BookFixtures.withNarratorHint(
                        BookFixtures.frankensteinImport(),
                        new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of()))));
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
        glossary.willListUnknownGenders(unknown);
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.NAMES_STYLE));
    }

    private boolean isAsking() {
        final Node card = scene.getRoot().lookup("#" + CARD);
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private void awaitAsking() throws Exception {
        awaitFx(this::isAsking);
    }

    // IF the run started silently, THEN a character the book names forty times would be drafted with a guessed gender.
    @Test
    void start_characterWithUnknownGender_listsItWithItsMentionsBeforeStarting() throws Exception {
        showNamesStyle(List.of(new UnknownGender("Chrome", 12)));

        onFx(() -> button("names-style-start").fire());
        awaitAsking();

        assertThat(textsUnder(required(CARD)))
                .contains("Characters without a gender", "Chrome — 12 mentions", "Back", "Start anyway");
        assertThat(engine.requests()).isEmpty();
        assertThat(glossary.unknownGenderCalls()).containsExactly("p1:5");
    }

    @Test
    void startAnyway_pressed_startsTheRunAndAsksNoMoreForTheBook() throws Exception {
        showNamesStyle(List.of(new UnknownGender("Chrome", 12)));
        onFx(() -> button("names-style-start").fire());
        awaitAsking();

        onFx(() -> button("genders-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
        final CharacterGendersDialog dialog = injector.getInstance(CharacterGendersDialog.class);
        final int[] started = {0};
        onFx(() -> dialog.startAfterAsking(() -> started[0]++));
        assertThat(started[0]).isEqualTo(1);
    }

    // Enter is the default button: it must be "Start anyway", never a choice that changes anything.
    @Test
    void enter_onTheQuestion_choosesStartAnyway() throws Exception {
        showNamesStyle(List.of(new UnknownGender("Chrome", 12)));
        onFx(() -> button("names-style-start").fire());
        awaitAsking();

        final boolean[] defaults = ThemeTestSupport.onFx(() -> new boolean[] {
            ((javafx.scene.control.Button) scene.getRoot().lookup("#genders-start")).isDefaultButton(),
            ((javafx.scene.control.Button) scene.getRoot().lookup("#genders-back")).isDefaultButton()
        });

        assertThat(defaults).containsExactly(true, false);
    }

    @Test
    void back_pressed_startsNothingAndAsksAgainAtTheNextStart() throws Exception {
        showNamesStyle(List.of(new UnknownGender("Chrome", 12)));
        onFx(() -> button("names-style-start").fire());
        awaitAsking();

        onFx(() -> button("genders-back").fire());
        onFx(() -> button("names-style-start").fire());
        awaitAsking();

        assertThat(engine.requests()).isEmpty();
        assertThat(isAsking()).isTrue();
    }

    @Test
    void start_noCharacterQualifies_startsWithoutAsking() throws Exception {
        showNamesStyle(List.of());

        onFx(() -> button("names-style-start").fire());

        job.awaitRunStarted();
        assertThat(isAsking()).isFalse();
    }
}
