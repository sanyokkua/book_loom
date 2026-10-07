package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorHint;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.TooltipProbe;

/**
 * The notice on the tone card that the source is told in the first person: shown while the narrator's gender is not
 * chosen, hidden otherwise, and never a change the person did not make beyond preselecting "first person".
 */
class BookBriefNarratorNoticeTest extends BookBriefScreenTestBase {

    private static final String NOTICE = "brief-tone-narrator-notice";

    private void openWith(final NarratorHint hint) throws TimeoutException {
        openBookThenShowBrief(FRANKENSTEIN, BookFixtures.withNarratorHint(BookFixtures.frankensteinImport(), hint));
    }

    @Test
    void notice_firstPersonHintAndNoGender_isShownWithItsTextAndTip() throws TimeoutException {
        openWith(BookFixtures.firstPersonHint());

        assertThat(isShown(NOTICE)).isTrue();
        assertThat(((Label) required(NOTICE)).getText())
                .isEqualTo("The book is narrated in the first person — choose the narrator's gender.");
        assertThat(TooltipProbe.tipText(required(NOTICE))).contains("Pick Male or Female");
    }

    @Test
    void notice_firstPersonHint_preselectsTheFirstPersonButNoGender() throws TimeoutException {
        openWith(BookFixtures.firstPersonHint());

        assertThat(brief().narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.UNKNOWN));
        assertThat(selectedStates("brief-tone-narrator")).containsExactly(false, true, false);
    }

    @Test
    void notice_mixedHint_isShown() throws TimeoutException {
        openWith(new NarratorHint(NarratorHint.Person.MIXED, 0.12, List.of(2, 5)));

        assertThat(isShown(NOTICE)).isTrue();
    }

    @Test
    void notice_thirdPersonHint_isHiddenAndTheBriefIsUntouched() throws TimeoutException {
        openWith(new NarratorHint(NarratorHint.Person.THIRD, 0.0, List.of()));

        assertThat(isShown(NOTICE)).isFalse();
        assertThat(brief().narrator()).isEqualTo(Narrator.unspecified());
    }

    @Test
    void notice_noHint_isHidden() throws TimeoutException {
        openFrankensteinThenShowBrief();

        assertThat(isShown(NOTICE)).isFalse();
    }

    @Test
    void notice_genderChosen_goesAway() throws TimeoutException {
        openWith(BookFixtures.firstPersonHint());

        onFx(() -> segmented("brief-tone-narrator-gender").getButtons().get(1).setSelected(true));

        assertThat(brief().narrator()).isEqualTo(new Narrator(NarratorPerson.FIRST, Gender.MALE));
        assertThat(isShown(NOTICE)).isFalse();
    }

    @Test
    void notice_personChoosesThirdPerson_goesAway() throws TimeoutException {
        openWith(BookFixtures.firstPersonHint());

        onFx(() -> segmented("brief-tone-narrator").getButtons().get(2).setSelected(true));

        assertThat(isShown(NOTICE)).isFalse();
    }

    @Test
    void notice_ukrainianInterface_isInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openWith(BookFixtures.firstPersonHint());

        assertThat(((Label) required(NOTICE)).getText()).startsWith("Книга розповідається від першої особи");
    }
}
