package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * Tokens per second is read from what the server says it generated, so each dialect's usage fields must reach the
 * finished-call event as reported, a reply without them must be estimated in the target script, and the client's own
 * retry of a timed-out attempt must stay inside the one call it belongs to.
 */
class ModelCallUsageWireMockTest {

    private static final ChatRequest REQUEST =
            new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "Translate: He opened the door.")));
    private static final String SEGMENT = "Book.md:0";
    private static final Duration GENEROUS = Duration.ofSeconds(5);

    private final List<JobEvent> events = new CopyOnWriteArrayList<>();

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void call_replyReportsUsage_finishesWithTheReportedCountsNotEstimated(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.replyWithUsage("Він відчинив двері.", 812, 96, 3_200_000_000L));

            calls(provider.model(GENEROUS, ignored -> {})).call(CallKind.DRAFT, SEGMENT, REQUEST);
        }

        assertThat(finished())
                .extracting(
                        ModelCallFinished::kind,
                        ModelCallFinished::segmentId,
                        finished -> usageOf(finished).prompt(),
                        finished -> usageOf(finished).completion(),
                        ModelCallFinished::outputChars,
                        ModelCallFinished::usageEstimated)
                .containsExactly(CallKind.DRAFT, SEGMENT, 812, 96, 19, false);
    }

    // Ollama states its own generation time; the rate would be wrong if the wall time replaced it.
    @ParameterizedTest
    @EnumSource(value = ProviderKind.class, names = "OLLAMA")
    void call_ollamaReportsEvalDuration_finishesWithThatGenerationTime(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.replyWithUsage("Він відчинив двері.", 812, 96, 3_200_000_000L));

            calls(provider.model(GENEROUS, ignored -> {})).call(CallKind.DRAFT, SEGMENT, REQUEST);
        }

        assertThat(usageOf(finished()).generation()).isEqualTo(Duration.ofMillis(3200));
    }

    // The client's second attempt is the same ask; a second start would restart the screen's waiting clock.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void call_firstAttemptTimesOutThenSucceeds_announcesOneStartAndOneFinish(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.reply("Він відчинив двері.", Duration.ofMillis(1500)),
                    provider.reply("Він відчинив двері.", Duration.ZERO)));

            calls(provider.model(Duration.ofMillis(300), ignored -> {})).call(CallKind.DRAFT, SEGMENT, REQUEST);

            assertThat(provider.chatRequests()).isEqualTo(2);
        }

        assertThat(events).filteredOn(ModelCallStarted.class::isInstance).hasSize(1);
        assertThat(events).filteredOn(ModelCallFinished.class::isInstance).hasSize(1);
    }

    // 300 Cyrillic characters at 3.0 per token and the 1.15 safety factor are 115 tokens.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void call_replyWithoutUsageOnAnEnglishToUkrainianRun_estimatesTheCompletionFromTheReply(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply("ж".repeat(300), Duration.ZERO));

            calls(provider.model(GENEROUS, ignored -> {})).call(CallKind.DRAFT, SEGMENT, REQUEST);
        }

        final ModelCallFinished finished = finished();
        assertThat(finished)
                .extracting(
                        f -> usageOf(f).prompt(),
                        f -> usageOf(f).completion(),
                        ModelCallFinished::outputChars,
                        ModelCallFinished::usageEstimated)
                .containsExactly(null, 115, 300, true);
        assertThat(usageOf(finished).generation()).isEqualTo(finished.elapsed());
    }

    private JobModelCalls calls(final ChatModel model) {
        final JobControl control = new JobControl(Set.of());
        return new JobModelCalls(
                onSent -> new CancellableChatModel(model, control, onSent), events::add, Clock.systemUTC(), "uk");
    }

    private ModelCallFinished finished() {
        return events.stream()
                .filter(ModelCallFinished.class::isInstance)
                .map(ModelCallFinished.class::cast)
                .reduce((first, second) -> {
                    throw new AssertionError("more than one finished call");
                })
                .orElseThrow(() -> new AssertionError("no finished call"));
    }

    private static TokenUsage usageOf(final ModelCallFinished finished) {
        final TokenUsage usage = finished.usage();
        assertThat(usage).isNotNull();
        return java.util.Objects.requireNonNull(usage, "usage");
    }
}
