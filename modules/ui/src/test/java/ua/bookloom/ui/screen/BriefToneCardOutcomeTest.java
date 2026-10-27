package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StyleSuggestionOutcome;

/** The line under Fill in from the book after the model could not suggest a style. */
class BriefToneCardOutcomeTest {

    private static final StyleSuggestionOutcome FAILED = new StyleSuggestionOutcome(
            StyleSuggestionOutcome.Kind.FAILED,
            AppError.of(ErrorCode.validation, "Unreadable", "It could not be read."));

    @Test
    void outcomeText_failedInEnglish_keepsTheFailuresOwnWords() {
        assertThat(BriefToneCard.outcomeText(FAILED, new Messages(() -> Locale.ENGLISH)))
                .isEqualTo("It could not be read.");
    }

    // IF the failure's own English words were shown, THEN a Ukrainian Book Brief would explain it in English.
    @Test
    void outcomeText_failedInUkrainian_namesTheFailureInUkrainian() {
        assertThat(BriefToneCard.outcomeText(FAILED, new Messages(() -> Locale.forLanguageTag("uk"))))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }
}
