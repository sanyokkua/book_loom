package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.Messages;

/** What each result of a check is read as: a measured sentence, not a bare word. */
class CheckSentencesTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));

    private static StageChip chip(
            final VerificationStage stage, final StageStatus status, final long millis, final @Nullable Integer count) {
        return new StageChip(stage, status, null, null, Duration.ofMillis(millis), count);
    }

    // IF the connection result were a bare word, THEN a 41 ms server and a 4 s server would read the same.
    @ParameterizedTest
    @CsvSource({"41, Ollama answers (41 ms)", "1500, Ollama answers (1.5 s)"})
    void of_connectionPassed_saysWhoAnswersAndHowFast(final long millis, final String expected) {
        final StageChip passed = chip(VerificationStage.CONNECTION, StageStatus.PASSED, millis, null);

        assertThat(CheckSentences.of(EN, passed, "Ollama")).contains(expected);
    }

    @Test
    void of_connectionFailed_saysWhoDoesNotAnswer() {
        final StageChip failed = new StageChip(
                VerificationStage.CONNECTION,
                StageStatus.FAILED,
                AppError.of(ErrorCode.unreachable, "Unreachable", "No answer."),
                null);

        assertThat(CheckSentences.of(EN, failed, "Office LM")).contains("Office LM does not answer");
        assertThat(CheckSentences.of(UK, failed, "Office LM")).contains("Office LM не відповідає");
    }

    // IF an unset key variable read "QA Test does not answer", THEN the person would look at the server, which was
    // never asked, instead of at the variable.
    @Test
    void of_connectionFailedOnAnUnsetKeyVariable_namesTheMissingKey() {
        final StageChip failed = new StageChip(
                VerificationStage.CONNECTION,
                StageStatus.FAILED,
                AppError.of(ErrorCode.missingCredential, "Missing credential", "QA_KEY is not set."),
                null);

        assertThat(CheckSentences.of(EN, failed, "QA Test"))
                .contains("QA Test: the key is missing — its environment variable is not set");
        assertThat(CheckSentences.of(UK, failed, "QA Test"))
                .contains("QA Test: ключа немає — змінну середовища з ключем не задано");
    }

    // IF the count were not worded, THEN "7" would not say that the chosen model is among them.
    @ParameterizedTest
    @CsvSource({"7, 7 models found — the chosen one is there", "1, 1 model found — the chosen one is there"})
    void of_modelsPassed_countsAndSaysTheChosenOneIsThere(final int count, final String expected) {
        final StageChip passed = chip(VerificationStage.MODELS, StageStatus.PASSED, 5, count);

        assertThat(CheckSentences.of(EN, passed, "Ollama")).contains(expected);
    }

    @ParameterizedTest
    @CsvSource({"1, Знайдено 1 модель — обрана є", "3, Знайдено 3 моделі — обрана є", "7, Знайдено 7 моделей — обрана є"
    })
    void of_modelsPassedInUkrainian_usesTheRightPlural(final int count, final String expected) {
        final StageChip passed = chip(VerificationStage.MODELS, StageStatus.PASSED, 5, count);

        assertThat(CheckSentences.of(UK, passed, "Ollama")).contains(expected);
    }

    // IF a server without a list read as a failure, THEN it could never be used.
    @Test
    void of_modelsSoftPass_saysConnectedButNoList() {
        final StageChip soft = chip(VerificationStage.MODELS, StageStatus.SOFT_PASS, 5, 0);

        assertThat(CheckSentences.of(EN, soft, "Ollama")).contains("Connected — the list of models could not be read");
    }

    @Test
    void of_inferencePassed_saysHowLongTheModelTook() {
        final StageChip passed = chip(VerificationStage.INFERENCE, StageStatus.PASSED, 1200, null);

        assertThat(CheckSentences.of(EN, passed, "Ollama")).contains("The model replied in 1.2 s");
        assertThat(CheckSentences.of(UK, passed, "Ollama")).contains("Модель відповіла за 1,2 с");
    }

    // IF a test the server never reached read as passed or failed, THEN the person would blame the wrong stage.
    @Test
    void of_skipped_saysWhichStageWasNotAttempted() {
        final StageChip skipped = new StageChip(VerificationStage.INFERENCE, StageStatus.SKIPPED, null, null);

        assertThat(CheckSentences.of(EN, skipped, "Ollama")).contains("Inference: not attempted");
        assertThat(CheckSentences.of(UK, skipped, "Ollama")).contains("Відповідь моделі: не перевірялося");
    }

    // IF a finding without a measurement made up a sentence, THEN it would claim a number nobody measured.
    @Test
    void of_passedWithNoMeasurement_hasNoSentenceOfItsOwn() {
        final StageChip bare = new StageChip(VerificationStage.CONNECTION, StageStatus.PASSED, null, null);

        assertThat(CheckSentences.of(EN, bare, "Ollama")).isEqualTo(Optional.empty());
    }

    @Test
    void durationText_underASecond_isMilliseconds() {
        assertThat(CheckSentences.durationText(EN, Duration.ofMillis(41))).isEqualTo("41 ms");
    }
}
