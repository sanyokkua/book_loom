package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;

/**
 * Auto as the source language of the Book Brief (15i.U4): first in the source list and chosen for a book that declares
 * no language, said by a note, and enough for Continue when only a target is chosen.
 */
class BookBriefAutoSourceTest extends BookBriefScreenTestBase {

    private static final String AUTO_EN = "Find out from the text";
    private static final String AUTO_UK = "Визначити за текстом";

    private void openBookWithNoLanguage() throws TimeoutException {
        openBookThenShowBrief(Path.of("Diary.txt"), BookFixtures.declaringNoLanguage("diary", BookFormat.TXT, 1));
    }

    private String shownName(final String boxId, final int index) {
        return ThemeTestSupport.onFx(
                () -> box(boxId).getConverter().toString(box(boxId).getItems().get(index)));
    }

    private String editorText(final String boxId) {
        return ThemeTestSupport.onFx(() -> box(boxId).getEditor().getText());
    }

    // IF Auto were not the first entry, THEN a person scanning the list for "do not guess" would not find it.
    @Test
    void sourceList_bookOpened_startsWithAutoThenTheSameLanguagesAsTheTarget() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(box("brief-source").getItems()).hasSize(35);
        assertThat(shownName("brief-source", 0)).isEqualTo(AUTO_EN);
        assertThat(box("brief-source").getItems().subList(1, 35))
                .containsExactlyElementsOf(box("brief-target").getItems());
    }

    // IF the target list offered Auto, THEN a person could choose to translate into "the language the model decides".
    @Test
    void targetList_bookOpened_holdsNoAutoEntry() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(box("brief-target").getItems()).hasSize(34);
        assertThat(shownName("brief-target", 0)).isNotEqualTo(AUTO_EN);
    }

    // IF a book with no language were shown with an empty box, THEN a person could not tell "not decided" from "Auto".
    @Test
    void source_bookDeclaringNoLanguage_isPreselectedAsAutoAndTheBriefHoldsNoSource() throws TimeoutException {
        openBookWithNoLanguage();

        assertThat(editorText("brief-source")).isEqualTo(AUTO_EN);
        assertThat(brief().sourceLanguage()).isNull();
        assertThat(isShown("brief-source-undeclared")).isTrue();
    }

    // IF the note appeared for a declared language, THEN it would say "identify it" about a language already known.
    @Test
    void autoNote_bookDeclaringEnglish_isHiddenAndTheBoxShowsEnglish() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(editorText("brief-source")).isEqualTo("English");
        assertThat(isShown("brief-source-undeclared")).isFalse();
    }

    // IF choosing Auto over a declared English left English in the brief, THEN the run would ignore the choice.
    @Test
    void source_autoChosenOverDeclaredEnglish_clearsTheSourceAndShowsTheAutoNote() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-source").select(box("brief-source").getItems().getFirst()));

        assertThat(brief().sourceLanguage()).isNull();
        assertThat(editorText("brief-source")).isEqualTo(AUTO_EN);
        assertThat(isShown("brief-source-undeclared")).isTrue();
    }

    // IF the note still said the book "does not declare a language" after the person chose Auto, THEN it would be
    // false.
    @Test
    void autoNote_shown_saysTheModelIdentifiesTheLanguageWithoutClaimingTheBookDeclaresNone() throws TimeoutException {
        openBookWithNoLanguage();

        final String note = ((Label) required("brief-source-undeclared")).getText();

        assertThat(note).contains("The model will name the language");
        assertThat(note).doesNotContain("does not declare");
    }

    // IF a language chosen after Auto were not kept, THEN the person could not go back to naming the source.
    @Test
    void source_languageChosenAfterAuto_reachesTheBriefAndHidesTheNote() throws TimeoutException {
        openBookWithNoLanguage();

        onFx(() -> box("brief-source").select("fr"));

        assertThat(brief().sourceLanguage()).isEqualTo("fr");
        assertThat(isShown("brief-source-undeclared")).isFalse();
    }

    // IF Continue also needed a source, THEN a book that declares none could not go on without a guessed language.
    @Test
    void continueControl_autoSourceAndOnlyATarget_isAvailableAndMovesOn() throws TimeoutException {
        openBookWithNoLanguage();
        assertThat(button("brief-continue").isDisabled()).isTrue();

        onFx(() -> box("brief-target").select("uk"));

        assertThat(brief().sourceLanguage()).isNull();
        assertThat(button("brief-continue").isDisabled()).isFalse();
        assertThat(isShown("brief-languages-same")).isFalse();
        assertThat(scene.getRoot().lookup("#brief-continue-hint")).isNull();

        onFx(() -> button("brief-continue").fire());

        assertThat(currentView()).isEqualTo(ViewNames.NAMES_STYLE);
    }

    // IF Auto and no target still let Continue through, THEN a run would start toward no language.
    @Test
    void continueControl_autoSourceAndNoTarget_isUnavailableAndSaysWhy() throws TimeoutException {
        openBookWithNoLanguage();

        assertThat(button("brief-continue").isDisabled()).isTrue();
        assertThat(isShown("brief-continue-hint")).isTrue();
        assertThat(isShown("brief-target-required")).isTrue();
    }

    // IF the Ukrainian bundle lacked the Auto entry or its note, THEN a Ukrainian session would see an English one.
    @Test
    void sourceList_underUkrainian_startsWithTheUkrainianAutoEntryAndNote() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openBookWithNoLanguage();

        assertThat(shownName("brief-source", 0)).isEqualTo(AUTO_UK);
        assertThat(editorText("brief-source")).isEqualTo(AUTO_UK);
        assertThat(((Label) required("brief-source-undeclared")).getText()).matches(".*\\p{IsCyrillic}.*");
    }
}
