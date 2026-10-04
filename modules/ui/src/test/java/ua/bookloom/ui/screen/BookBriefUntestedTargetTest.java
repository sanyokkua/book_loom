package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.UiTestInjector;

/**
 * The note under the target box for a language whose translation rules are not tested yet: shown for such a language
 * only, in the interface language, with its own hover explanation; the translation itself is never blocked.
 */
class BookBriefUntestedTargetTest extends BookBriefScreenTestBase {

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .prompt(prompt)
                .languages("uk"::equals)
                .build();
    }

    // IF the note appeared for a language with tested rules, THEN a person would distrust the one that works best.
    @Test
    void note_testedTarget_isHidden() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-target").select("uk"));

        assertThat(isShown("brief-target-untested")).isFalse();
    }

    // IF a language without tested rules said nothing, THEN worse quotes or gender would look like a fault of the book.
    @Test
    void note_untestedTarget_isShownWithItsTextAndTip() throws TimeoutException {
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-target").select("de"));

        assertThat(isShown("brief-target-untested")).isTrue();
        assertThat(((Label) required("brief-target-untested")).getText())
                .isEqualTo("Untested language: its translation rules are not tested yet, so the general rules are"
                        + " used.");
        assertThat(TooltipProbe.tipText(required("brief-target-untested"))).contains("only general advice");
        assertThat(briefModel().canContinue().get()).isTrue();
    }

    // IF the note stayed after the person went back to a tested language, THEN it would describe a choice no longer
    // made.
    @Test
    void note_targetChangedBackToTested_goesAway() throws TimeoutException {
        openFrankensteinThenShowBrief();
        onFx(() -> box("brief-target").select("de"));
        assertThat(isShown("brief-target-untested")).isTrue();

        onFx(() -> box("brief-target").select("uk"));

        assertThat(isShown("brief-target-untested")).isFalse();
    }

    // IF the note were English-only, THEN a Ukrainian-speaking person could not read why quality differs.
    @Test
    void note_ukrainianInterface_isInUkrainian() throws TimeoutException {
        useLocale(Locale.forLanguageTag("uk"));
        openFrankensteinThenShowBrief();

        onFx(() -> box("brief-target").select("de"));

        assertThat(((Label) required("brief-target-untested")).getText()).startsWith("Неперевірена мова");
    }
}
