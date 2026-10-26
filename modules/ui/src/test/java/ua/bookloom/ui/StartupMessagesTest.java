package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/** The startup message for a database that cannot be opened speaks the operating system's language and names the folder. */
class StartupMessagesTest {

    private static final Path DATA_DIR = Path.of("/data/BookLoom");

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|storage open sqlite 8|Check the permissions of the data folder and start BookLoom again.",
                "uk|storage open sqlite 8|Перевірте дозволи теки даних і запустіть BookLoom знову.",
                "en|storage open locked|Close that program and start BookLoom again.",
                "uk|storage open locked|Закрийте її і запустіть BookLoom знову.",
                "de|storage open sqlite 8|Check the permissions of the data folder and start BookLoom again."
            })
    void storageFailure_osLanguage_wordsTheAdviceWithTheFolder(
            final String language, final String details, final String advice) {
        final AppError failure = AppError.of(
                ErrorCode.internal, "Storage not writable", "The database file cannot be written.", details, null);

        final AppError worded = StartupMessages.storageFailure(failure, DATA_DIR, Locale.forLanguageTag(language));

        assertThat(worded.message()).contains(DATA_DIR.toString()).endsWith(advice);
        assertThat(worded.code()).isEqualTo(ErrorCode.internal);
        assertThat(worded.details()).isEqualTo(details);
    }
}
