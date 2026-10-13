package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.ViewNames;

/** Each workflow step's hover text names the number its badge shows, and the screens' Back and Continue name their targets. */
class StepTipNumbersTest {

    // IF a tip kept the number of the six-step workflow, THEN the hover would contradict the badge beside it.
    @ParameterizedTest
    @CsvSource({
        "en, IMPORT, Step 1.",
        "en, BOOK_BRIEF, Step 2.",
        "en, NAMES_STYLE, Step 3.",
        "en, TRANSLATING, Step 4.",
        "en, EXPORT, Step 5.",
        "uk, IMPORT, Крок 1.",
        "uk, BOOK_BRIEF, Крок 2.",
        "uk, NAMES_STYLE, Крок 3.",
        "uk, TRANSLATING, Крок 4.",
        "uk, EXPORT, Крок 5.",
    })
    void navTip_workflowStep_startsWithItsOwnStepNumber(
            final String language, final ViewNames view, final String prefix) {
        assertThat(Catalogues.load(language).get(view.tipKey().key())).startsWith(prefix);
    }

    // IF Continue or Back still named the removed Structure step, THEN a button would promise a screen that is gone.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en | brief.continue | Continue to names & style",
                "en | brief.continue.tip | Goes to the Names & style step.",
                "en | namesStyle.back | Back to Book Brief",
                "en | namesStyle.back.tip | Goes back to the Book Brief step.",
                "uk | brief.continue | Перейти до імен і стилю",
                "uk | brief.continue.tip | Переходить до кроку «Імена та стиль».",
                "uk | namesStyle.back | Назад до опису книги",
                "uk | namesStyle.back.tip | Повертає до кроку «Опис книги».",
            })
    void stepButtons_afterStructureWasFolded_nameTheirNewTargets(
            final String language, final String key, final String expected) {
        final Map<String, String> bundle = Catalogues.load(language);

        assertThat(bundle.get(key)).isEqualTo(expected);
    }

    // IF a Structure entry or screen text stayed, THEN the catalogue would hold words for a screen nobody can open.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void catalogue_afterStructureWasFolded_holdsNoStructureStepOrScreenKeys(final String language) {
        assertThat(Catalogues.load(language).keySet())
                .doesNotContain(
                        "nav.structure",
                        "nav.structure.tip",
                        "structure.title",
                        "structure.continue",
                        "structure.continue.tip");
    }
}
