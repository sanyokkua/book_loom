package ua.bookloom.ui.state;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ProbeCall;
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
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestDetails {

    private static final int FIRST_SUCCESS = 200;
    private static final int LAST_SUCCESS = 299;
    // The skipped verdict's select word when no earlier stage is known to have failed.
    private static final String EARLIER = "earlier";

    /**
     * The dialog's content for one test.
     *
     * @param messages the catalogue the words come from
     * @param stage the test shown
     * @param chips the findings of the last run, empty before any
     * @param running whether a test is under way
     * @param providerName the name the provider is shown by
     * @param localProgram whether the provider is Ollama or LM Studio on this computer ({@link TestSubject#isLocalProgram})
     * @param modelChosen whether the test was asked about a chosen model
     * @return the verdict, the request, the figures and the reply
     */
    public static TestDetail of(
            final Messages messages,
            final VerificationStage stage,
            final List<StageChip> chips,
            final boolean running,
            final String providerName,
            final boolean localProgram,
            final boolean modelChosen) {
        return of(messages, stage, chips, running, new Subject(providerName, localProgram, modelChosen), TestLive.NONE);
    }

    /**
     * The dialog's content for one test, with what the running test has said so far: a stage that has ended shows
     * its result, a stage whose request is out shows the request and waits for the reply.
     *
     * @param messages the catalogue the words come from
     * @param stage the test shown
     * @param chips the findings of the last run, empty before any and while a test runs
     * @param running whether a test covering {@code stage} is under way
     * @param subject who the test was about
     * @param live what the running test has sent and finished, and the time now
     * @return the verdict, the request, the figures and the reply
     */
    public static TestDetail of(
            final Messages messages,
            final VerificationStage stage,
            final List<StageChip> chips,
            final boolean running,
            final Subject subject,
            final TestLive live) {
        Objects.requireNonNull(live, "live");
        if (running) {
            return runningDetail(messages, stage, subject, live);
        }
        final Optional<StageChip> found =
                chips.stream().filter(chip -> chip.stage() == stage).findFirst();
        final Optional<VerificationStage> failed = failedBefore(stage, chips);
        if (found.isEmpty() && failed.isEmpty()) {
            return plain(Severity.INFO, messages.get(MessageKey.SETTINGS_TEST_VERDICT_NOT_RUN));
        }
        if (found.isEmpty() || found.get().status() == StageStatus.SKIPPED) {
            log.debug(
                    "test {} not attempted: {} failed first",
                    stage,
                    failed.map(Enum::name).orElse("an earlier test"));
            return plain(
                    Severity.WARN,
                    messages.get(
                            MessageKey.SETTINGS_TEST_VERDICT_SKIPPED,
                            failed.map(Enum::name).orElse(EARLIER)));
        }
        return measured(messages, found.get(), subject);
    }

    private static TestDetail runningDetail(
            final Messages messages, final VerificationStage stage, final Subject subject, final TestLive live) {
        final Optional<StageChip> ended =
                live.finished().stream().filter(chip -> chip.stage() == stage).findFirst();
        if (ended.isPresent()) {
            return measured(messages, ended.get(), subject);
        }
        final List<SentCall> sent =
                live.sent().stream().filter(call -> call.stage() == stage).toList();
        if (!sent.isEmpty()) {
            return LiveDetail.waiting(messages, stage, sent, live.steps(), live.now());
        }
        return live.started().stream()
                .filter(begun -> begun.stage() == stage)
                .findFirst()
                .map(begun -> LiveDetail.starting(messages, stage, live.steps(), begun.startedAt(), live.now()))
                .orElseGet(() -> plain(Severity.INFO, messages.get(MessageKey.SETTINGS_TEST_VERDICT_RUNNING)));
    }

    /**
     * The place of a stage among the stages a test goes through, counted from one.
     *
     * @param stage the stage
     * @return how many stages up to and including {@code stage} a test runs
     */
    public static int stepOf(final VerificationStage stage) {
        return EnumSet.range(VerificationStage.CONNECTION, stage).size();
    }

    /**
     * Who a test was about, as far as the words of its result depend on it.
     *
     * @param providerName the name the provider is shown by
     * @param localProgram whether it is Ollama or LM Studio on this computer
     * @param modelChosen whether the test was asked about a chosen model
     */
    public record Subject(String providerName, boolean localProgram, boolean modelChosen) {

        /** Rejects a missing name. */
        public Subject {
            Objects.requireNonNull(providerName, "providerName");
        }
    }

    /**
     * The stage before {@code stage} that failed in the last run, which is why {@code stage} was never tried: Test
     * connection alone reports no later stage at all, Test inference reports them as skipped.
     *
     * @param stage the test asked about
     * @param chips the findings of the last run, empty before any
     * @return the failed earlier stage if there is one, or empty if none failed
     */
    public static Optional<VerificationStage> failedBefore(final VerificationStage stage, final List<StageChip> chips) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(chips, "chips");
        return chips.stream()
                .filter(chip -> chip.stage().compareTo(stage) < 0 && chip.status() == StageStatus.FAILED)
                .map(StageChip::stage)
                .findFirst();
    }

    private static TestDetail plain(final Severity severity, final String verdict) {
        return new TestDetail(severity, verdict, "", "", "", "", List.of(), "", "");
    }

    // The card shows and copies the bodies laid out for reading; Copy as curl repeats the body as it was sent.
    private static TestDetail measured(final Messages messages, final StageChip chip, final Subject subject) {
        final @Nullable VerificationExchange exchange = chip.exchange();
        final String noBody = messages.get(MessageKey.SETTINGS_TEST_NO_BODY);
        final String body = exchange == null ? "" : shownBody(messages, exchange, true);
        final String reply = exchange == null ? "" : shownBody(messages, exchange, false);
        return new TestDetail(
                severityOf(chip.status()),
                verdictOf(messages, chip, subject),
                exchange == null ? "" : exchange.method() + " " + exchange.url(),
                exchange == null ? "" : CurlCommand.of(exchange),
                exchange == null ? "" : orElse(body, noBody),
                exchange == null ? "" : orElse(reply, noBody),
                new TestMetrics(messages, chip, exchange, subject.modelChosen()).list(),
                body,
                reply,
                chip.stage() == VerificationStage.ADVANCED && chip.status() != StageStatus.FAILED
                        ? AdvancedRows.of(messages, chip.note(), callsOf(exchange))
                        : List.of());
    }

    // A stage of several calls lists each call's body under its heading; a stage of one shows that one.
    private static List<ProbeCall> callsOf(final @Nullable VerificationExchange exchange) {
        return exchange == null ? List.of() : exchange.calls();
    }

    private static String shownBody(final Messages messages, final VerificationExchange exchange, final boolean sent) {
        if (exchange.calls().isEmpty()) {
            return sent ? exchange.shownRequestBody() : exchange.shownResponseBody();
        }
        return AdvancedRows.listed(
                messages,
                exchange.calls().stream()
                        .map(call -> Map.entry(call.check(), sent ? call.shownRequestBody() : call.shownResponseBody()))
                        .toList());
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

    private static String verdictOf(final Messages messages, final StageChip chip, final Subject subject) {
        final String providerName = subject.providerName();
        final boolean localProgram = subject.localProgram();
        final @Nullable AppError error = chip.error();
        if (chip.status() == StageStatus.FAILED && error != null && isNotOpen(error, localProgram)) {
            return failureOf(messages, error, providerName, localProgram);
        }
        if (chip.status() == StageStatus.FAILED) {
            return messages.get(MessageKey.SETTINGS_TEST_VERDICT_FAILED, reasonOf(messages, chip));
        }
        if (chip.status() == StageStatus.SOFT_PASS) {
            return messages.get(MessageKey.SETTINGS_TEST_VERDICT_MODELS_SOFT);
        }
        return switch (chip.stage()) {
            case CONNECTION -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_CONNECTION, providerName);
            case MODELS ->
                messages.get(
                        subject.modelChosen()
                                ? MessageKey.SETTINGS_TEST_VERDICT_MODELS
                                : MessageKey.SETTINGS_TEST_VERDICT_MODELS_LISTED);
            case INFERENCE -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_INFERENCE);
            case ADVANCED -> messages.get(MessageKey.SETTINGS_TEST_VERDICT_ADVANCED);
        };
    }

    /**
     * The words a failed test is explained in: for Ollama or LM Studio on this computer that nothing answered, that the
     * app is not open; otherwise the failure's own words.
     *
     * @param messages the catalogue the words come from
     * @param error the failure
     * @param providerName the name the provider is shown by
     * @param localProgram whether the provider is Ollama or LM Studio on this computer
     * @return one sentence; never blank
     */
    public static String failureOf(
            final Messages messages, final AppError error, final String providerName, final boolean localProgram) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(providerName, "providerName");
        if (isNotOpen(error, localProgram)) {
            log.debug("test failure of {} worded as its app not open", providerName);
            return messages.get(MessageKey.SETTINGS_TEST_NOT_OPEN, providerName);
        }
        return ErrorWords.message(messages, error);
    }

    // A local app that is closed refuses the connection; any answer, even an error, means it is open.
    private static boolean isNotOpen(final AppError error, final boolean localProgram) {
        return localProgram && error.code() == ErrorCode.unreachable;
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
            Messages messages, StageChip chip, @Nullable VerificationExchange exchange, boolean modelChosen) {

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
                // The checks' verdicts and their calls' figures are the card's check rows.
                case ADVANCED -> log.trace("advanced figures are worded in the check rows");
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
                    chip.status() == StageStatus.PASSED && modelChosen
                            ? messages.get(MessageKey.SETTINGS_TEST_METRIC_LISTED)
                            : none()));
        }

        private void inferenceFigures(final List<Metric> figures) {
            final @Nullable Long load = exchange == null ? null : exchange.loadMs();
            if (load != null) {
                figures.add(metric(
                        "load",
                        MessageKey.SETTINGS_TEST_METRIC_LOAD,
                        CheckSentences.durationText(messages, Duration.ofMillis(load))));
            }
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
            if (prompt == null && reply == null) {
                return none();
            }
            final Object shownPrompt = prompt == null ? none() : prompt;
            final Object shownReply = reply == null ? none() : reply;
            return messages.get(MessageKey.SETTINGS_TEST_METRIC_TOKENS_VALUE, shownPrompt, shownReply);
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
