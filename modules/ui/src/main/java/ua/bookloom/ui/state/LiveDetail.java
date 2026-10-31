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
     * @param now the time the card shows the wait up to
     */
    static TestDetail waiting(
            final Messages messages, final VerificationStage stage, final List<SentCall> sent, final Instant now) {
        final VerificationExchange last = sent.getLast().request();
        final String body = stage == VerificationStage.ADVANCED ? listed(messages, sent) : last.shownRequestBody();
        final Duration open = Duration.between(sent.getFirst().sentAt(), now);
        final Duration shown = open.isNegative() ? Duration.ZERO : open;
        log.trace("test card waits on {}: {} requests, open {}", stage, sent.size(), shown);
        return new TestDetail(
                Severity.INFO,
                messages.get(MessageKey.SETTINGS_TEST_VERDICT_RUNNING),
                last.method() + " " + last.url(),
                CurlCommand.of(last),
                body,
                messages.get(MessageKey.SETTINGS_TEST_WAITING),
                List.of(new Metric(
                        "time",
                        messages.get(MessageKey.SETTINGS_TEST_METRIC_TIME),
                        CheckSentences.durationText(messages, shown))),
                body,
                "");
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
