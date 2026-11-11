package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageDeclaration;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ListKind;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.Messages;

/**
 * The Models tab's declared-languages line says what is known about the target: nothing can be checked while no
 * target is chosen, and once one is, whether the maker's list declares it.
 */
class ModelAdviceLanguagesLineTest {

    private static final Set<String> THIRTEEN =
            Set.of("en", "ru", "zh", "es", "fr", "de", "it", "ja", "ko", "pt", "ar", "vi", "th");

    private final ModelAdviceLines lines =
            new ModelAdviceLines(new Messages(() -> Locale.ENGLISH), new LanguageNames());

    private static AdviceReading reading(
            final LanguageState target, final @Nullable String sourceTag, final @Nullable String targetTag) {
        final ModelReport report = new ModelReport(
                null,
                null,
                null,
                ModelReport.Match.FAMILY,
                ModelReport.UNKNOWN_MODEL,
                List.of(),
                ReasoningControl.UNKNOWN,
                new LanguageDeclaration(ListKind.COMPLETE, THIRTEEN, "test"),
                null,
                null,
                null);
        final LanguageAdvice advice =
                new LanguageAdvice(LanguageState.DECLARED, target, false, false, "Invented", null, List.of());
        return new AdviceReading("invented-model:1b", report, advice, "", sourceTag, targetTag);
    }

    // IF the line named only the declared languages with no book open, THEN it would read as if the target were
    // covered, when no target is known to check.
    @Test
    void languages_noTargetChosen_saysTheTargetIsNotChosenYet() {
        assertThat(lines.languages(reading(LanguageState.DECLARED, null, null)))
                .isEqualTo("Declared languages: 13 (includes ru) · no target language chosen yet");
    }

    // IF a complete list that leaves the target out were shown with no word on it, THEN the reader would have to
    // know the list by heart to see the target is missing.
    @Test
    void languages_completeListWithoutTarget_saysTheTargetIsNotDeclared() {
        assertThat(lines.languages(reading(LanguageState.NOT_DECLARED, "en", "uk")))
                .isEqualTo("Declared languages: 13 (includes ru) · Ukrainian is not declared");
    }

    @Test
    void languages_listDeclaresTarget_saysSo() {
        assertThat(lines.languages(reading(LanguageState.DECLARED, "uk", "ru")))
                .isEqualTo("Declared languages: 13 (includes ru) · Russian is declared");
    }

    // A positive-only list can never say a language is missing; nothing is added where the state is unknown.
    @Test
    void languages_targetStateUnknown_addsNothing() {
        assertThat(lines.languages(reading(LanguageState.UNKNOWN, "en", "uk")))
                .isEqualTo("Declared languages: 13 (includes ru)");
    }
}
