package ua.bookloom.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.SafeDetails;

/** The words a failure is shown in: its own in English, the catalogue's by reason or by code otherwise. */
class ErrorWordsTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));
    private static final String UK_VALIDATION =
            "Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.";

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

    // IF a reason with no words of its own took the catalogue's catch-all, THEN the person would lose the code's more
    // useful words.
    @ParameterizedTest
    @EnumSource(
            value = FailureReason.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {
                "FOLDER_MISSING",
                "ACCESS_DENIED",
                "READ_ONLY",
                "DISK_FULL",
                "NOT_UTF8",
                "CSV_HEADER",
                "WRONG_PATH",
                "MARKDOWN_STRUCTURE",
                "CONTEXT_OVERFLOW",
                "REPEAT_LIMIT",
                "OUT_OF_MEMORY",
                "MODEL_NOT_PULLED",
                "HOST_FORBIDDEN",
                "CLOUD_DISABLED",
                "CLOUD_SIGNIN",
                "TERMINATED",
                "AUTH",
                "WRONG_MODEL_SERVED",
                "NOT_SUITABLE_STRUCTURED",
                "NOT_SUITABLE_DOCUMENTS",
                "UNCLASSIFIED"
            })
    void message_inUkrainian_tokensWithoutWords_keepTheCodeWords(final FailureReason reason) {
        assertThat(ErrorWords.message(UK, withReason(reason))).isEqualTo(UK_VALIDATION);
    }

    // The reasons a person meets in a run, each with advice they can act on. Every failure carries the counts of a
    // prompt that did not fit, which only the context-overflow words show.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "CONTEXT_OVERFLOW | Цей уривок не вміщується у вікно моделі (696 з 512 токенів). Його позначено."
                        + " Завантажте модель із більшим контекстом або надішліть менше тексту.",
                "REPEAT_LIMIT | Модель зациклилася на повторах, і сервер її зупинив. Спробуйте ще раз або оберіть"
                        + " іншу модель.",
                "OUT_OF_MEMORY | Бракує вільної пам’яті, щоб завантажити цю модель. Закрийте інші програми або"
                        + " оберіть меншу модель.",
                "MODEL_NOT_PULLED | В Ollama немає цієї моделі. Встановіть її в Ollama командою ollama pull або"
                        + " оберіть іншу модель у налаштуваннях.",
                "HOST_FORBIDDEN | Ollama відхилила цю адресу. Вкажіть 127.0.0.1 або дозвольте Ollama приймати"
                        + " запити з мережі.",
                "CLOUD_DISABLED | Хмарні моделі Ollama вимкнено на цьому комп’ютері. Увімкніть їх в Ollama або"
                        + " оберіть локальну модель.",
                "CLOUD_SIGNIN | Увійдіть в Ollama, щоб користуватися цією хмарною моделлю, і спробуйте ще раз.",
                "TERMINATED | Сервер розірвав з’єднання, поки відповідав. Спробуйте ще раз за мить.",
                "AUTH | Сервер вимагає ключ API. Укажіть у налаштуваннях провайдера назву змінної середовища, що його містить.",
                "WRONG_MODEL_SERVED | Сервер відповів іншою моделлю, ніж обрано. Перевірте назву моделі.",
                "NOT_SUITABLE_STRUCTURED | Ця програма не підтримує структурованих відповідей, тому BookLoom не може нею"
                        + " користуватися.",
                "NOT_SUITABLE_DOCUMENTS | Ця адреса веде розмову з документами, а не з моделлю."
            })
    void message_ukrainianDisplay_wordsEachNewReasonToken(final FailureReason reason, final String expected) {
        final AppError error = AppError.of(
                ErrorCode.upstream,
                "Provider error",
                "The provider failed.",
                SafeDetails.empty().withReason(reason).withTokenCounts(696, 512).render(),
                null);

        assertThat(ErrorWords.message(UK, error)).isEqualTo(expected);
    }

    // Ukrainian counts take one, few or many forms: 1 and 21 токена, 2 токенів, 5 токенів.
    @ParameterizedTest
    @CsvSource({"1, 696 з 1 токена", "2, 696 з 2 токенів", "5, 696 з 5 токенів", "21, 696 з 21 токена"})
    void message_ukrainianContextOverflow_pluralisesTheWindowCount(final int window, final String counts) {
        final AppError error = AppError.of(
                ErrorCode.contextWindow,
                "Too long",
                "The passage did not fit.",
                SafeDetails.empty()
                        .withReason(FailureReason.CONTEXT_OVERFLOW)
                        .withTokenCounts(696, window)
                        .render(),
                null);

        assertThat(ErrorWords.message(UK, error)).contains("(" + counts + ")");
    }

    // IF the provider stated no counts, THEN the words say the passage did not fit without inventing figures.
    @Test
    void message_ukrainianContextOverflowWithoutCounts_omitsTheFigures() {
        final AppError error = AppError.of(
                ErrorCode.contextWindow,
                "Too long",
                "The passage did not fit.",
                SafeDetails.empty().withReason(FailureReason.CONTEXT_OVERFLOW).render(),
                null);

        assertThat(ErrorWords.message(UK, error))
                .isEqualTo("Цей уривок не вміщується у вікно моделі. Його позначено. Завантажте модель із більшим"
                        + " контекстом або надішліть менше тексту.");
    }

    // The English catalogue holds the same advice for the screens that word a reason by its token.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "context-overflow | This passage did not fit the model’s window (696 of 512 tokens). It was left to"
                        + " review. Load the model with a larger context, or send less text.",
                "repeat-limit | The model kept repeating itself and the server stopped it. Try again, or choose"
                        + " another model.",
                "out-of-memory | There is not enough free memory to load this model. Close other programs, or"
                        + " choose a smaller model.",
                "model-not-pulled | Ollama does not have this model installed. Install it in Ollama with ollama"
                        + " pull, or choose another model in Settings.",
                "host-forbidden | Ollama refused this address. Use 127.0.0.1 as the address, or let Ollama listen"
                        + " on the network.",
                "cloud-disabled | Ollama’s cloud models are turned off on this computer. Turn them on in Ollama, or"
                        + " choose a local model.",
                "cloud-signin | Sign in to Ollama to use this cloud model, then try again.",
                "terminated | The server closed the connection while it was answering. Try again in a moment."
            })
    void code_englishReasonMessage_wordsEachNewReasonToken(final String token, final String expected) {
        assertThat(EN.code(MessageKey.ERROR_REASON_MESSAGE, token, "counted", 696, 512))
                .isEqualTo(expected);
    }

    // IF "unclassified" took the catalogue's catch-all, THEN the person would lose the code's more useful words.
    @Test
    void message_inUkrainianUnclassified_isWordedByItsCode() {
        assertThat(ErrorWords.message(UK, withReason(FailureReason.UNCLASSIFIED)))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }

    @Test
    void message_inUkrainianWithoutAReason_isWordedByItsCode() {
        final AppError plain = AppError.of(ErrorCode.validation, "Refused", "No.");

        assertThat(ErrorWords.message(UK, plain))
                .isEqualTo("Щось у введених даних або в книжці не так. Подробиці пояснюють, що саме.");
    }
}
