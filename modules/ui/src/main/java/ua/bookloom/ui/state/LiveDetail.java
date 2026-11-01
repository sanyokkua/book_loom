package ua.bookloom.ui.state;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.AdvancedCheck;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.TestDetail.Metric;
import ua.bookloom.ui.state.TestDetail.Severity;

/**
 * What the test card shows while a stage waits for the model: the request as it was sent, a waiting line where the
 * reply will be, and the time since the stage's first request, which grows as the card's clock ticks.
 */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LiveDetail {

    /**
     * The waiting card of one stage.
     *
     * @param sent the stage's requests so far, oldest first; not empty
     * @param steps how many stages the running test covers
     * @param now the time the card shows the wait up to
     */
    static TestDetail waiting(
            final Messages messages,
            final VerificationStage stage,
            final List<SentCall> sent,
            final int steps,
            final Instant now) {
        final VerificationExchange last = sent.getLast().request();
        final String body = stage == VerificationStage.ADVANCED ? listed(messages, sent) : last.shownRequestBody();
        final Duration shown = openFor(sent.getFirst().sentAt(), now);
        log.trace("test card waits on {}: {} requests, open {}", stage, sent.size(), shown);
        return new TestDetail(
                Severity.INFO,
                step(messages, stage, steps),
                last.method() + " " + last.url(),
                CurlCommand.of(last),
                body,
                messages.get(MessageKey.SETTINGS_TEST_WAITING),
                timeOnly(messages, shown),
                body,
                "");
    }

    /**
     * The card of a stage that has begun but sent no request yet, as a model that is still loading leaves it.
     *
     * @param steps how many stages the running test covers
     * @param begun when the stage began
     * @param now the time the card shows the wait up to
     */
    static TestDetail starting(
            final Messages messages,
            final VerificationStage stage,
            final int steps,
            final Instant begun,
            final Instant now) {
        final Duration shown = openFor(begun, now);
        log.trace("test card waits on {} before its first request, open {}", stage, shown);
        return new TestDetail(
                Severity.INFO,
                step(messages, stage, steps),
                "",
                "",
                "",
                waitsOnModel(stage) ? messages.get(MessageKey.SETTINGS_TEST_WAITING) : "",
                timeOnly(messages, shown),
                "",
                "");
    }

    // "Step 3 of 4 · Inference · waiting for the model to load or answer": the line a person reads while a model loads.
    private static String step(final Messages messages, final VerificationStage stage, final int steps) {
        return messages.get(
                MessageKey.SETTINGS_TEST_STEP,
                TestDetails.stepOf(stage),
                steps,
                CheckSentences.stageName(messages, stage),
                messages.get(
                        waitsOnModel(stage)
                                ? MessageKey.SETTINGS_TEST_STEP_MODEL
                                : MessageKey.SETTINGS_TEST_STEP_SERVER));
    }

    private static boolean waitsOnModel(final VerificationStage stage) {
        return stage == VerificationStage.INFERENCE || stage == VerificationStage.ADVANCED;
    }

    private static Duration openFor(final Instant since, final Instant now) {
        final Duration open = Duration.between(since, now);
        return open.isNegative() ? Duration.ZERO : open;
    }

    private static List<Metric> timeOnly(final Messages messages, final Duration shown) {
        return List.of(new Metric(
                "time",
                messages.get(MessageKey.SETTINGS_TEST_METRIC_TIME),
                CheckSentences.durationText(messages, shown)));
    }

    private static String listed(final Messages messages, final List<SentCall> sent) {
        final List<Map.Entry<AdvancedCheck, String>> bodies = sent.stream()
                .filter(call -> call.check() != null)
                .map(call -> Map.entry(
                        Objects.requireNonNull(call.check(), "check"),
                        call.request().shownRequestBody()))
                .toList();
        return AdvancedRows.listed(messages, bodies);
    }
}
