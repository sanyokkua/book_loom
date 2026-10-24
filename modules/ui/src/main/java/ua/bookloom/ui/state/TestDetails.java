package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TestDetail.Metric;
import ua.bookloom.ui.state.TestDetail.Severity;

/** Works out what the test dialog shows for a test from the findings of the last run. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestDetails {

    private static final int FIRST_SUCCESS = 200;
    private static final int LAST_SUCCESS = 299;

    /**
     * The dialog's content for one test.
     *
     * @param messages the catalogue the words come from
     * @param stage the test shown
     * @param chips the findings of the last run, empty before any
     * @param running whether a test is under way
     * @param providerName the name the provider is shown by
     * @return the verdict, the request, the figures and the reply
     */
    public static TestDetail of(
            final Messages messages,
            final VerificationStage stage,
            final List<StageChip> chips,
            final boolean running,
            final String providerName) {
        final Optional<StageChip> found =
                chips.stream().filter(chip -> chip.stage() == stage).findFirst();
        if (running) {
            return plain(Severity.INFO, messages.get(MessageKey.SETTINGS_TEST_VERDICT_RUNNING));
        }
        if (found.isEmpty()) {
            return plain(Severity.INFO, messages.get(MessageKey.SETTINGS_TEST_VERDICT_NOT_RUN));
        }
        final StageChip chip = found.get();
        if (chip.status() == StageStatus.SKIPPED) {
            return plain(Severity.WARN, messages.get(MessageKey.SETTINGS_TEST_VERDICT_SKIPPED));
        }
        return measured(messages, chip, providerName);
    }

    private static TestDetail plain(final Severity severity, final String verdict) {
        return new TestDetail(severity, verdict, "", "", "", "", List.of(), "", "");
    }

    private static TestDetail measured(final Messages messages, final StageChip chip, final String providerName) {
        final @Nullable VerificationExchange exchange = chip.exchange();
        final String noBody = messages.get(MessageKey.SETTINGS_TEST_NO_BODY);
        return new TestDetail(
                severityOf(chip.status()),
                verdictOf(messages, chip, providerName),
                exchange == null ? "" : exchange.method() + " " + exchange.url(),
                exchange == null ? "" : CurlCommand.of(exchange),
                exchange == null ? "" : orElse(exchange.requestBody(), noBody),
                exchange == null ? "" : orElse(exchange.responseBody(), noBody),
                new TestMetrics(messages, chip, exchange).list(),
                exchange == null ? "" : exchange.requestBody(),
                exchange == null ? "" : exchange.responseBody());
    }

    private static String orElse(final String text, final String fallback) {
        return text.isEmpty() ? fallback : text;
    }

    private static Severity severityOf(final StageStatus status) {
        return switch (status) {
            case PASSED -> Severity.OK;
            case SOFT_PASS, SKIPPED -> Severity.WARN;
            case FAILED -> Severity.ERR;
        };
    }

    private static String verdictOf(final Messages messages, final StageChip chip, final String providerName) {
        if (chip.status() == StageStatus.FAILED) {
            return messages.get(MessageKey.SETTINGS_TEST_VERDICT_FAILED, reasonOf(messages, chip));
        }
        if (chip.status() == StageStatus.SOFT_PASS) {
            return messages.get(MessageKey.SETTINGS_TEST_VERDICT_MODELS_SOFT);
        }
        return switch (chip.stage()) {
            case CONNECTION -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_CONNECTION, providerName);
            case MODELS -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_MODELS);
            case INFERENCE -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_INFERENCE);
        };
    }

    private static String reasonOf(final Messages messages, final StageChip chip) {
        final @Nullable AppError error = chip.error();
        if (error != null) {
            return ErrorWords.message(messages, error);
        }
        final @Nullable String note = chip.note();
        return note == null ? "" : note;
    }

    /** The figures of a finished test, in the order the middle column shows them. */
    private record TestMetrics(
            Messages messages, StageChip chip, @Nullable VerificationExchange exchange) {

        List<Metric> list() {
            final List<Metric> figures = new ArrayList<>();
            figures.add(metric("status", MessageKey.SETTINGS_TEST_METRIC_STATUS, status()));
            figures.add(metric("time", MessageKey.SETTINGS_TEST_METRIC_TIME, time()));
            if (chip.status() == StageStatus.FAILED) {
                return figures;
            }
            switch (chip.stage()) {
                case CONNECTION -> figures.add(metric("server", MessageKey.SETTINGS_TEST_METRIC_SERVER, none()));
                case MODELS -> modelsFigures(figures);
                case INFERENCE -> inferenceFigures(figures);
            }
            return figures;
        }

        private void modelsFigures(final List<Metric> figures) {
            final @Nullable Integer count = chip.count();
            figures.add(metric(
                    "models", MessageKey.SETTINGS_TEST_METRIC_MODELS, count == null ? none() : count.toString()));
            figures.add(metric(
                    "chosen",
                    MessageKey.SETTINGS_TEST_METRIC_CHOSEN,
                    chip.status() == StageStatus.PASSED
                            ? messages.get(MessageKey.SETTINGS_TEST_METRIC_LISTED)
                            : none()));
        }

        private void inferenceFigures(final List<Metric> figures) {
            final @Nullable Long firstToken = exchange == null ? null : exchange.firstTokenMs();
            final @Nullable Double speed = exchange == null ? null : exchange.tokensPerSecond();
            figures.add(metric(
                    "firstToken",
                    MessageKey.SETTINGS_TEST_METRIC_FIRST_TOKEN,
                    firstToken == null
                            ? none()
                            : CheckSentences.durationText(messages, Duration.ofMillis(firstToken))));
            figures.add(metric(
                    "speed",
                    MessageKey.SETTINGS_TEST_METRIC_SPEED,
                    speed == null ? none() : messages.get(MessageKey.SETTINGS_TEST_METRIC_SPEED_VALUE, speed)));
            figures.add(metric("tokens", MessageKey.SETTINGS_TEST_METRIC_TOKENS, tokens()));
            figures.add(metric(
                    "structured",
                    MessageKey.SETTINGS_TEST_METRIC_STRUCTURED,
                    messages.get(
                            exchange != null && exchange.structured()
                                    ? MessageKey.SETTINGS_TEST_METRIC_YES
                                    : MessageKey.SETTINGS_TEST_METRIC_NO)));
        }

        private String tokens() {
            final @Nullable Integer prompt = exchange == null ? null : exchange.promptTokens();
            final @Nullable Integer reply = exchange == null ? null : exchange.completionTokens();
            return prompt == null && reply == null
                    ? none()
                    : messages.get(
                            MessageKey.SETTINGS_TEST_METRIC_TOKENS_VALUE,
                            prompt == null ? none() : prompt,
                            reply == null ? none() : reply);
        }

        private String status() {
            final int code = exchange == null ? 0 : exchange.httpStatus();
            if (code == 0) {
                return neverAnswered() ? messages.get(MessageKey.SETTINGS_TEST_STATUS_NONE) : none();
            }
            return code >= FIRST_SUCCESS && code <= LAST_SUCCESS
                    ? messages.get(MessageKey.SETTINGS_TEST_STATUS_OK, code)
                    : Integer.toString(code);
        }

        // "No answer" is said only when the failure is that nothing came back; any other failure without a status
        // is unknown, which the dash says.
        private boolean neverAnswered() {
            final @Nullable AppError error = chip.error();
            return chip.status() == StageStatus.FAILED
                    && error != null
                    && (error.code() == ErrorCode.unreachable || error.code() == ErrorCode.timeout);
        }

        private String time() {
            final @Nullable Duration elapsed = chip.elapsed();
            return elapsed == null ? none() : CheckSentences.durationText(messages, elapsed);
        }

        private String none() {
            return messages.get(MessageKey.SETTINGS_TEST_METRIC_NONE);
        }

        private Metric metric(final String name, final MessageKey label, final String value) {
            return new Metric(name, messages.get(label), value);
        }
    }
}
