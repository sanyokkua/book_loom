package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The book-brief screen the application builds, read from the real scene: the state with no book open and its way to
 * the import screen, the Languages card (the boxes, the source the book declares and the same-language refusal), and
 * the buttons that move around the workflow. The other cards are in {@code BookBriefScreenCardsTest}.
 */
class BookBriefScreenTest extends BookBriefScreenTestBase {

    // IF the brief showed an empty form with no book, THEN a person would fill in a language for nothing.
    @Test
    void screen_noBookOpen_reportsItAndPresentsNoLanguageBoxes() {
        showBrief();

        assertThat(isShown("nobook-card")).isTrue();
        assertThat(((Label) required("nobook-report")).getText()).isNotBlank();
        assertThat(optional("brief-target")).isNull();
        assertThat(optional("brief-languages-card")).isNull();
    }

    // IF the route to the import screen were not wired, THEN a person on an empty brief would have no way forward.
    @Test
    void nobookOpen_noBookOpen_firingItMovesToTheImportScreen() {
        showBrief();

        onFx(() -> button("nobook-open").fire());

        assertThat(currentView()).isEqualTo(ViewNames.IMPORT);
    }

    // IF the empty report stayed after a book was opened, THEN the brief would say no book is open beside a book.
    @Test
    void screen_bookOpenedThenBriefShownAgain_showsTheBriefAndNotTheReport() throws TimeoutException {
        showBrief();
        assertThat(isShown("nobook-card")).isTrue();

        openFrankensteinThenShowBrief();

        assertThat(isShown("brief-languages-card")).isTrue();
        assertThat(isShown("nobook-card")).isFalse();
    }

    // IF the opening of a book under a brief that is already showing were missed, THEN the report of no book would
    // stay up beside a book, until the person happened to navigate away and back.
    @Test
    void screen_bookOpenedWhileBriefDisplayed_swapsToBriefWithoutNavigating() throws TimeoutException {
        showBrief();
        projects.on(FRANKENSTEIN, Result.ok(BookFixtures.frankensteinImport()));

        openBook(FRANKENSTEIN);

        assertThat(currentView()).isEqualTo(ViewNames.BOOK_BRIEF);
        assertThat(isShown("brief-languages-card")).isTrue();
        assertThat(isShown("nobook-card")).isFalse();
    }

    // IF the source were a fixed label, THEN a mis-declared book could not be corrected.
    @Test
    void source_bookDeclaringEnglish_isAnEnabledSearchableBoxShowingEnglish() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(box("brief-source").isDisabled()).isFalse();
        assertThat(box("brief-source").isEditable()).isTrue();
        assertThat(box("brief-source").getCommitted()).isEqualTo("en");
        assertThat(box("brief-source").getEditor().getText()).isEqualTo("English");
    }

    // IF a book declaring nothing were shown with a guessed language, THEN the screen would state a fact nobody knows.
    @Test
    void source_bookDeclaringNothing_isEmptySaysSoAndBlocksContinue() throws TimeoutException {
        openBookThenShowBrief(Path.of("Diary.txt"), BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1));
        onFx(() -> briefModel().setTargetLanguage("uk"));

        assertThat(box("brief-source").getCommitted()).isNull();
        assertThat(isShown("brief-source-undeclared")).isTrue();
        assertThat(button("brief-continue").isDisabled()).isTrue();

        onFx(() -> box("brief-source").select("en"));

        assertThat(brief().sourceLanguage()).isEqualTo("en");
        assertThat(isShown("brief-source-undeclared")).isFalse();
        assertThat(button("brief-continue").isDisabled()).isFalse();
    }

    // IF the two lists differed or were cut short, THEN a language could be a source and not a target.
    @Test
    void boxes_bookOpened_bothHoldTheSameThirtyFourLanguages() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(box("brief-source").getItems()).hasSize(34);
        assertThat(box("brief-target").getItems())
                .containsExactlyElementsOf(box("brief-source").getItems());
        assertThat(box("brief-target").getCommitted()).isNull();
    }

    // IF a choice in the box did not reach the brief, THEN the run would use a language the person did not pick.
    @ParameterizedTest
    @ValueSource(strings = {"pl", "de", "la"})
    void target_chosenInTheBox_reachesTheBrief(final String tag) throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-target").select(tag));

        assertThat(brief().targetLanguage()).isEqualTo(tag);
    }

    // IF the box did not follow the view model, THEN it would show one language while the run used another.
    @ParameterizedTest
    @ValueSource(strings = {"pl", "de"})
    void target_setInTheViewModel_isShownInTheBox(final String tag) throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> briefModel().setTargetLanguage(tag));

        assertThat(box("brief-target").getCommitted()).isEqualTo(tag);
    }

    // IF a language outside the list were refused, THEN a person translating Latin could not say so.
    @Test
    void target_latinTypedAndFocusLeft_isAcceptedAsLa() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> {
            box("brief-target").getEditor().requestFocus();
            box("brief-target").getEditor().setText("Latin");
        });
        onFx(() -> button("brief-back").requestFocus());

        assertThat(brief().targetLanguage()).isEqualTo("la");
        assertThat(box("brief-target").getEditor().getText()).isEqualTo("Latin");
    }

    // IF unrecognised text were taken as a language, THEN the model would be told to translate into Elvish.
    @Test
    void target_elvishTypedAndFocusLeft_keepsThePreviousChoice() throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> briefModel().setTargetLanguage("pl"));

        onFx(() -> {
            box("brief-target").getEditor().requestFocus();
            box("brief-target").getEditor().setText("Elvish");
        });
        onFx(() -> button("brief-back").requestFocus());

        assertThat(brief().targetLanguage()).isEqualTo("pl");
    }

    // IF the same language on both sides raised no message, THEN a run would translate a book into its own language.
    @Test
    void sameLanguage_sourceAndTargetEnglish_showsTheMessageAndBlocksContinueUntilTheyDiffer() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-target").select("en"));

        assertThat(isShown("brief-languages-same")).isTrue();
        assertThat(textOf("brief-languages-same")).contains("The source and target languages are the same.");
        assertThat(button("brief-continue").isDisabled()).isTrue();

        onFx(() -> box("brief-target").select("uk"));

        assertThat(isShown("brief-languages-same")).isFalse();
        assertThat(button("brief-continue").isDisabled()).isFalse();
    }

    // IF Back were not wired, THEN a person could not return to the book they opened.
    @Test
    void backControl_bookOpened_firingItMovesToTheImportScreen() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> button("brief-back").fire());

        assertThat(currentView()).isEqualTo(ViewNames.IMPORT);
    }

    // IF Continue were not wired to the workflow, THEN a person who has briefed the run could not go on; and
    // IF it did not mark the step, THEN the navigation would never show the brief as done.
    @Test
    void continueControl_bothLanguagesChosen_movesToStructureAndMarksTheBriefDone() throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> briefModel().setTargetLanguage("uk"));

        onFx(() -> button("brief-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.STRUCTURE);
        assertThat(ThemeTestSupport.onFx(() ->
                        injector.getInstance(WorkflowProgress.class).done().contains(ViewNames.BOOK_BRIEF)))
                .isTrue();
    }

    // IF Continue were available with no target, THEN a run could start toward no language.
    @Test
    void continueControl_noTargetChosen_isUnavailable() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(button("brief-continue").isDisabled()).isTrue();
    }

    // IF the brief were built only for English, THEN a Ukrainian session would show an empty or untranslated report.
    @Test
    void screen_noBookOpenUnderUkrainian_reportsInThatLanguage() {
        useLocale(Locale.forLanguageTag("uk"));
        showBrief();

        assertThat(((Label) required("nobook-report")).getText()).isNotBlank().matches(".*\\p{IsCyrillic}.*");
    }
}
