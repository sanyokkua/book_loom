package ua.bookloom.app.cli;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;

/** Scripted provider seams for a whole command run: a model that fails chosen drafts, a probe, a clock. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CliRunFakes {

    private static final Map<String, String> TRANSLATIONS = Map.of("One.", "Один.", "Two.", "Два.", "Three.", "Три.");

    /** Any call that is not a draft — a summary, a scan — gets a reply every reader tolerates. */
    private static final String OTHER_REPLY =
            "{\"target\":\"\",\"summary\":\"\",\"issues\":[],\"terms\":[],\"score\":0.95,\"verdict\":\"accept\","
                    + "\"findings\":[],\"deferrals\":[]}";

    /** A model that answers each draft with its translation, except the drafts it is told to fail. */
    static final class ScriptedModels implements ChatModelFactory {

        private final Set<Integer> failingDrafts;
        private final ErrorCode failure;
        private final AtomicInteger drafts = new AtomicInteger();

        /**
         * @param failingDrafts the draft calls, counted from one, that answer {@code failure}
         * @param failure the error those calls answer
         */
        ScriptedModels(Set<Integer> failingDrafts, ErrorCode failure) {
            this.failingDrafts = Set.copyOf(failingDrafts);
            this.failure = failure;
        }

        @Override
        public Result<ChatModel> create(ModelSelection selection) {
            return Result.ok(this::answer);
        }

        private Result<ChatResponse> answer(ChatRequest request) {
            final String user = request.messages().getLast().content();
            final int start = user.lastIndexOf("<Text>\n");
            if (start < 0) {
                return Result.ok(new ChatResponse(OTHER_REPLY, FinishReason.STOP));
            }
            if (failingDrafts.contains(drafts.incrementAndGet())) {
                return Result.err(AppError.of(failure, "Provider failure", "The scripted provider failed."));
            }
            final String text = user.substring(start + "<Text>\n".length(), user.indexOf("\n</Text>", start));
            return Result.ok(new ChatResponse(
                    "{\"target\":\"" + TRANSLATIONS.getOrDefault(text, text) + "\"}", FinishReason.STOP));
        }
    }

    /** A provider whose preflight passes and whose recovery probe fails a set number of times before it passes. */
    static final class ScriptedProbe implements ProviderVerifier {

        private final AtomicInteger failuresLeft;

        ScriptedProbe(int failures) {
            this.failuresLeft = new AtomicInteger(failures);
        }

        @Override
        public Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy) {
            if (policy == VerificationPolicy.CONNECTION_AND_MODELS && failuresLeft.getAndDecrement() > 0) {
                return Result.ok(new VerificationReport(List.of(new StageOutcome(
                        VerificationStage.CONNECTION,
                        StageStatus.FAILED,
                        AppError.of(ErrorCode.unreachable, "Down", "Nothing is listening."),
                        null))));
            }
            return Result.ok(new VerificationReport(List.of(
                    new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null),
                    new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null),
                    new StageOutcome(VerificationStage.INFERENCE, StageStatus.PASSED, null, null))));
        }

        @Override
        public Result<VerificationReport> verifyConnection(String providerId) {
            return verify(new ModelSelection(providerId, "any"), VerificationPolicy.CONNECTION_AND_MODELS);
        }
    }

    /** A clock a recovery wait advances instead of sleeping. */
    static final class ScriptedClock extends Clock {

        private static final Instant START = Instant.parse("2026-10-02T02:00:00Z");
        private final AtomicReference<Instant> now = new AtomicReference<>(START);

        /** The recovery timer: moves the clock by the wait and leaves nothing to wait. */
        long advanceBy(Duration delay) {
            now.updateAndGet(instant -> instant.plus(delay));
            return 0;
        }

        long elapsedMinutes() {
            return Duration.between(START, instant()).toMinutes();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Objects.requireNonNull(now.get(), "now");
        }
    }
}
