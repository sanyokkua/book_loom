package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.UnitPolicy;
import ua.bookloom.ui.BookFixtures;

/**
 * What the brief holds and what it reports: it starts from the brief the project was created with, every choice
 * replaces it at once, and the languages decide whether the step can go on.
 */
class BookBriefViewModelTest extends BookBriefViewModelTestBase {

    // IF a new book started with other choices, THEN every translation a person never configured would change.
    @Test
    void brief_newBook_holdsTheDefaults() {
        openBook(BookFixtures.frankensteinImport());

        final BookBrief opened = current();

        assertThat(opened.register()).isEqualTo(Register.NEUTRAL);
        assertThat(opened.names()).isEqualTo(NamePolicy.TRANSLITERATE);
        assertThat(opened.foreignPassages()).isEqualTo(ForeignPassagePolicy.KEEP);
        assertThat(opened.footnotes()).isEqualTo(FootnotePolicy.TRANSLATE);
        assertThat(opened.units()).isEqualTo(UnitPolicy.KEEP);
        assertThat(opened.balance()).isEqualTo(55);
        assertThat(opened.alsoTranslate()).isEqualTo(new AlsoTranslate(true, true, true, false));
        assertThat(opened.dial()).isEqualTo(QualityDial.BALANCED);
        assertThat(opened.genre()).isNull();
        assertThat(opened.voiceEra()).isNull();
        assertThat(opened.audience()).isNull();
        assertThat(opened.targetLanguage()).isNull();
    }

    // IF the preselected source could not be corrected, THEN a book declaring the wrong language would be
    // translated as if the declaration were true.
    @Test
    void setSourceLanguage_bookPreselectedEnglish_theChangeToUkrainianIsKept() {
        openBook(BookFixtures.withSource(BookFixtures.frankensteinImport(), "en"));
        assertThat(current().sourceLanguage()).isEqualTo("en");

        change(() -> brief.setSourceLanguage("uk"));

        assertThat(current().sourceLanguage()).isEqualTo("uk");
    }

    // IF the two languages were not both saved, THEN a corrected source would be lost with the target.
    @Test
    void setLanguages_sourceUkrainianTargetPolish_savesABriefHoldingBoth() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setSourceLanguage("uk"));
        change(() -> brief.setTargetLanguage("pl"));
        runSaves();
        runSaves();

        assertThat(projects.briefs().getLast().sourceLanguage()).isEqualTo("uk");
        assertThat(projects.briefs().getLast().targetLanguage()).isEqualTo("pl");
    }

    // IF a book declaring nothing had a source anyway, THEN a run could start with a language nobody chose.
    @Test
    void canContinue_bookWithNoSource_isFalseUntilASourceAndATargetAreChosen() {
        openBook(BookFixtures.declaringNoLanguage("notes", BookFormat.TXT, 1));
        change(() -> brief.setTargetLanguage("uk"));

        assertThat(sourceUndeclared()).isTrue();
        assertThat(canContinue()).isFalse();

        change(() -> brief.setSourceLanguage("en"));

        assertThat(sourceUndeclared()).isFalse();
        assertThat(canContinue()).isTrue();
    }

    // IF a book with only a source could continue, THEN a run would start toward no language.
    @Test
    void canContinue_sourceButNoTarget_isFalse() {
        openBook(BookFixtures.frankensteinImport());

        assertThat(canContinue()).isFalse();
    }

    // IF the same language on both sides were allowed, THEN a run would translate a book into its own language.
    @Test
    void sameLanguage_sourceAndTargetBothEnglish_blocksContinueAndClearsWhenTheyDiffer() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setTargetLanguage("en"));
        assertThat(sameLanguage()).isTrue();
        assertThat(canContinue()).isFalse();

        change(() -> brief.setTargetLanguage("uk"));
        assertThat(sameLanguage()).isFalse();
        assertThat(canContinue()).isTrue();
    }

    // IF an untested target looked like a tested one, THEN the note about general rules would never reach the person.
    @Test
    void targetUntested_languageWithoutTestedRules_isTrueOnlyWhileSuchATargetIsChosen() {
        openBook(BookFixtures.frankensteinImport());
        assertThat(brief.targetUntested().get()).isFalse();

        change(() -> brief.setTargetLanguage("xx"));
        assertThat(brief.targetUntested().get()).isTrue();
        assertThat(canContinue()).isTrue();

        change(() -> brief.setTargetLanguage("uk"));
        assertThat(brief.targetUntested().get()).isFalse();
    }

    // IF free text were replaced by a suggestion, THEN a genre no list holds could not be given.
    @Test
    void setGenre_freeText_isKeptAsWritten() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setGenre("Cosy mystery set in 1920s Kyiv"));

        assertThat(current().genre()).isEqualTo("Cosy mystery set in 1920s Kyiv");
    }

    @ParameterizedTest
    @CsvSource({"-5,0", "0,0", "20,20", "100,100", "150,100"})
    void setBalance_anyNumber_isKeptWithinZeroToHundred(final int given, final int kept) {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setBalance(given));

        assertThat(current().balance()).isEqualTo(kept);
    }

    // IF a policy change were not kept, THEN the run would translate names the way the person did not choose.
    @Test
    void setNames_keepOriginal_isKeptWithTheRestUntouched() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setNames(NamePolicy.KEEP_ORIGINAL));

        assertThat(current().names()).isEqualTo(NamePolicy.KEEP_ORIGINAL);
        assertThat(current().balance()).isEqualTo(55);
    }

    // IF leaving the screen dropped the choice, THEN the register would fall back to Neutral on every visit.
    @Test
    void register_casualThenANewViewModel_stillCasual() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setRegister(Register.CASUAL));

        recreateBrief();

        assertThat(current().register()).isEqualTo(Register.CASUAL);
    }

    // IF a save ran on the FX thread, THEN a slow project service would freeze the window.
    @Test
    void change_eachChange_savesTheBriefAsItThenStandsOffTheFxThread() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setDial(QualityDial.MAX));
        runSaves();
        change(() -> brief.setUnits(UnitPolicy.METRIC));
        runSaves();

        assertThat(projects.briefs()).hasSize(2);
        assertThat(projects.briefs().get(0).dial()).isEqualTo(QualityDial.MAX);
        assertThat(projects.briefs().get(1).units()).isEqualTo(UnitPolicy.METRIC);
        assertThat(projects.briefs().get(1).dial()).isEqualTo(QualityDial.MAX);
        assertThat(projects.briefCallsOnFxThread()).containsExactly(false, false);
    }

    // IF an older brief overtook a newer one, THEN the project would keep the choice the person already replaced.
    @Test
    void change_madeWhileASaveIsInFlight_isSentAfterItAndOnlyTheNewestWaits() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setRegister(Register.CASUAL));
        change(() -> brief.setRegister(Register.FORMAL_LITERARY));
        change(() -> brief.setBalance(20));

        runSaves();
        runSaves();

        assertThat(projects.briefs()).hasSize(2);
        assertThat(projects.briefs().get(0).register()).isEqualTo(Register.CASUAL);
        assertThat(projects.briefs().get(1).register()).isEqualTo(Register.FORMAL_LITERARY);
        assertThat(projects.briefs().get(1).balance()).isEqualTo(20);
    }

    // IF setting a value it already held saved anyway, THEN every re-shown screen would write to the project.
    @Test
    void change_valueAlreadyHeld_savesNothing() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setRegister(Register.NEUTRAL));
        runSaves();

        assertThat(projects.briefs()).isEmpty();
    }

    // IF a change with no book open did something, THEN a save would name a project that does not exist.
    @Test
    void change_noBookOpen_savesNothingAndHoldsNoBrief() {
        change(() -> brief.setRegister(Register.CASUAL));
        runSaves();

        assertThat(projects.briefs()).isEmpty();
        assertThat(onFx(() -> current.brief().get())).isNull();
    }

    // IF a later change rebuilt the brief without the narrator, THEN choosing a genre would silently forget who
    // narrates.
    @Test
    void narrator_firstPersonMale_survivesEveryOtherChangeAndANewViewModel() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setNarratorPerson(NarratorPerson.FIRST));
        change(() -> brief.setNarratorGender(Gender.MALE));

        change(() -> brief.setRegister(Register.CASUAL));
        change(() -> brief.setGenre("Gothic novel"));
        change(() -> brief.setDial(QualityDial.MAX));
        recreateBrief();

        assertThat(current().narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
    }
}
