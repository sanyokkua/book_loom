package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;

/** The words a failure is shown in: its own in English, the catalogue's by reason or by code otherwise. */
class ErrorWordsTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));
    private static final String UK_FALLBACK = "Щось пішло не так. Подробиці пояснюють більше.";

    private static AppError withReason(final FailureReason reason) {
        return AppError.of(
                ErrorCode.validation,
                "The disk is full",
                "There is not enough free space on the disk.",
                SafeDetails.empty()
                        .withReason(reason)
                        .withFolder(Path.of("/x/Books"))
                        .render(),
                null);
    }

    @Test
    void message_inEnglish_keepsTheFailuresOwnWords() {
        assertThat(ErrorWords.message(EN, withReason(FailureReason.DISK_FULL)))
                .isEqualTo("There is not enough free space on the disk.");
    }

    // IF a full disk read as the generic "something in the input is wrong", THEN the person would not know to free
    // space.
    @Test
    void message_inUkrainianWithAReason_givesThatReasonsAdvice() {
        assertThat(ErrorWords.message(UK, withReason(FailureReason.DISK_FULL)))
                .isEqualTo("На диску бракує вільного місця. Звільніть місце або оберіть інший диск.");
    }

    // IF a reason had no branch of its own, THEN it would fall to the catalogue's catch-all words.
    @ParameterizedTest
    @EnumSource(FailureReason.class)
    void message_inUkrainian_everyReasonHasItsOwnAdvice(final FailureReason reason) {
        assertThat(ErrorWords.message(UK, withReason(reason))).isNotEqualTo(UK_FALLBACK);
    }

    @Test
    void message_inUkrainianWithoutAReason_isWordedByItsCode() {
        final AppError plain = AppError.of(ErrorCode.validation, "Refused", "No.");

        assertThat(ErrorWords.message(UK, plain))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }
}
