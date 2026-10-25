package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TestDetail.Metric;
import ua.bookloom.ui.state.TestDetail.Severity;

/** What the test dialog says about each test: a verdict, the request and the figures behind it. */
class TestDetailsTest {

    private static final Messages EN = new Messages(() -> Locale.ENGLISH);
    private static final Messages UK = new Messages(() -> Locale.forLanguageTag("uk"));

    private static final VerificationExchange CHAT = new VerificationExchange(
            "POST",
            "http://127.0.0.1:11434/api/chat",
            "{\"model\":\"gemma\"}",
            "OK",
            200,
            120L,
            31.5,
            true,
            "Authorization: Bearer ••••",
            42,
            17);

    private static StageChip passed(final VerificationStage stage, final VerificationExchange exchange) {
        return new StageChip(stage, StageStatus.PASSED, null, null, Duration.ofMillis(1200), 7, exchange);
    }

    private static List<Metric> metricsOf(final TestDetail detail) {
        return detail.metrics();
    }

    // IF the inference figures were not all shown, THEN a slow model could not be told from a fast one.
    @Test
    void of_inferencePassed_isGreenWithTheSixFigures() {
        final TestDetail detail = TestDetails.of(
                EN, VerificationStage.INFERENCE, List.of(passed(VerificationStage.INFERENCE, CHAT)), false, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.OK);
        assertThat(detail.verdict()).isEqualTo("The model replied correctly. Translation can start.");
        assertThat(metricsOf(detail))
                .extracting(Metric::name, Metric::value)
                .containsExactly(
                        tuple("status", "200 OK"),
                        tuple("time", "1.2 s"),
                        tuple("firstToken", "120 ms"),
                        tuple("speed", "31.5 tok/s"),
                        tuple("tokens", "42 · 17"),
                        tuple("structured", "yes"));
    }

    // IF the request line were built from anything but the exchange, THEN it could name an address that was not used.
    @Test
    void of_inferencePassed_showsTheRequestTheCurlAndBothBodies() {
        final TestDetail detail = TestDetails.of(
                EN, VerificationStage.INFERENCE, List.of(passed(VerificationStage.INFERENCE, CHAT)), false, "Ollama");

        assertThat(detail.requestLine()).isEqualTo("POST http://127.0.0.1:11434/api/chat");
        assertThat(detail.curl()).contains("Authorization: Bearer ••••").doesNotContain("sk-");
        assertThat(detail.body()).isEqualTo("{\"model\":\"gemma\"}");
        assertThat(detail.response()).isEqualTo("OK");
    }

    @Test
    void of_connectionPassed_namesTheProviderAndShowsStatusTimeAndNoServerFigure() {
        final VerificationExchange tags =
                new VerificationExchange("GET", "http://127.0.0.1:11434/api/tags", "", "", 200, null, null, false);

        final TestDetail detail = TestDetails.of(
                EN, VerificationStage.CONNECTION, List.of(passed(VerificationStage.CONNECTION, tags)), false, "Ollama");

        assertThat(detail.verdict()).isEqualTo("Ollama answers.");
        assertThat(detail.body()).isEqualTo("(no body)");
        assertThat(metricsOf(detail))
                .extracting(Metric::name, Metric::value)
                .containsExactly(tuple("status", "200 OK"), tuple("time", "1.2 s"), tuple("server", "—"));
    }

    @Test
    void of_modelsPassed_showsTheCountAndThatTheChosenOneIsListed() {
        final TestDetail detail = TestDetails.of(
                EN, VerificationStage.MODELS, List.of(passed(VerificationStage.MODELS, CHAT)), false, "Ollama");

        assertThat(detail.verdict()).isEqualTo("The list of models was read and the chosen model is in it.");
        assertThat(metricsOf(detail))
                .extracting(Metric::name, Metric::value)
                .containsExactly(
                        tuple("status", "200 OK"),
                        tuple("time", "1.2 s"),
                        tuple("models", "7"),
                        tuple("chosen", "listed ✓"));
    }

    // IF a server without a list read as a failure, THEN it could not be told from a server that is down.
    @Test
    void of_modelsSoftPass_isAWarningThatSaysTheListCouldNotBeRead() {
        final StageChip soft = new StageChip(
                VerificationStage.MODELS,
                StageStatus.SOFT_PASS,
                null,
                "model list unavailable",
                Duration.ofMillis(5),
                0,
                null);

        final TestDetail detail = TestDetails.of(EN, VerificationStage.MODELS, List.of(soft), false, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.WARN);
        assertThat(detail.verdict()).isEqualTo("The server answers, but its list of models could not be read.");
    }

    // IF a failure with no known status said "No answer", THEN a server that did answer would look unreachable.
    @Test
    void of_failedWithoutAKnownStatusAfterAnAnswer_showsADashNotNoAnswer() {
        final AppError rejected = AppError.of(ErrorCode.upstream, "Upstream", "The server failed.");
        final StageChip failed = new StageChip(VerificationStage.CONNECTION, StageStatus.FAILED, rejected, null);

        final TestDetail detail = TestDetails.of(EN, VerificationStage.CONNECTION, List.of(failed), false, "Ollama");

        assertThat(metricsOf(detail)).extracting(Metric::name, Metric::value).contains(tuple("status", "—"));
    }

    @Test
    void of_failed_isRedWithTheReasonAndNoAnswerStatus() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.");
        final StageChip failed = new StageChip(VerificationStage.CONNECTION, StageStatus.FAILED, unreachable, null);

        final TestDetail detail = TestDetails.of(EN, VerificationStage.CONNECTION, List.of(failed), false, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.ERR);
        assertThat(detail.verdict()).isEqualTo("This test did not pass: The server did not answer.");
        assertThat(metricsOf(detail)).extracting(Metric::name, Metric::value).contains(tuple("status", "No answer"));
        assertThat(detail.requestLine()).isEmpty();
    }

    // IF the failure's own English words were shown, THEN a Ukrainian verdict would end in English.
    @Test
    void of_failedInUkrainian_namesTheFailureByItsCodeInUkrainian() {
        final AppError unreachable = AppError.of(ErrorCode.unreachable, "Unreachable", "The server did not answer.");
        final StageChip failed = new StageChip(VerificationStage.CONNECTION, StageStatus.FAILED, unreachable, null);

        final TestDetail detail = TestDetails.of(UK, VerificationStage.CONNECTION, List.of(failed), false, "Ollama");

        assertThat(detail.verdict())
                .isEqualTo("Перевірка не пройшла: За адресою моделі ШІ ніщо не відповідає. Перевірте, чи відкрито її"
                        + " програму.");
    }

    // IF a test the server never reached read as failed, THEN the person would blame the wrong stage.
    @Test
    void of_skipped_isAWarningThatSaysAnEarlierTestDidNotPass() {
        final StageChip skipped = new StageChip(VerificationStage.INFERENCE, StageStatus.SKIPPED, null, null);

        final TestDetail detail = TestDetails.of(EN, VerificationStage.INFERENCE, List.of(skipped), false, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.WARN);
        assertThat(detail.verdict()).isEqualTo("Not attempted: an earlier test did not pass.");
    }

    @Test
    void of_nothingRunYet_isInfoThatSaysInferenceChecksAllThree() {
        final TestDetail detail = TestDetails.of(EN, VerificationStage.MODELS, List.of(), false, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.INFO);
        assertThat(detail.verdict()).isEqualTo("Not tested yet. Test inference checks all three.");
        assertThat(detail.metrics()).isEmpty();
    }

    @Test
    void of_running_isInfoTesting() {
        final TestDetail detail = TestDetails.of(EN, VerificationStage.INFERENCE, List.of(), true, "Ollama");

        assertThat(detail.severity()).isEqualTo(Severity.INFO);
        assertThat(detail.verdict()).isEqualTo("Testing...");
    }

    @Test
    void of_inferencePassedInUkrainian_readsInUkrainian() {
        final TestDetail detail = TestDetails.of(
                UK, VerificationStage.INFERENCE, List.of(passed(VerificationStage.INFERENCE, CHAT)), false, "Ollama");

        assertThat(detail.verdict()).isEqualTo("Модель відповіла правильно. Можна починати переклад.");
        assertThat(metricsOf(detail)).extracting(Metric::label).contains("Швидкість", "Перший токен");
    }
}
