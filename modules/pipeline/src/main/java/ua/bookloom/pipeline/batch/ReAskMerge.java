package ua.bookloom.pipeline.batch;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.pipeline.prompt.StepAnnouncer;

/** Folds a re-ask's reply into the first reply and ends the re-ask's step. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReAskMerge {

    // A stopped re-ask ends the attempt and leaves its step open; a failed or unreadable one leaves the missing ids to
    // their single drafts.
    static Result<BatchReply> merge(
            final BatchReply first, final Result<BatchReply> second, final StepAnnouncer steps, final String stepKey) {
        final AppError failure = second.error();
        if (failure != null && failure.code() == ErrorCode.cancelled) {
            log.info("The re-ask for the missing ids was stopped code={}; no single draft follows", failure.code());
            return Result.err(failure);
        }
        final BatchReply again = second.data();
        if (again == null || !again.readable()) {
            log.warn(
                    "The re-ask for the missing ids brought nothing code={}; they fall back one by one",
                    failure == null ? "unreadable" : failure.code());
            steps.ended(StepKind.RE_ASK, stepKey, StepResult.FAILED);
            return Result.ok(first);
        }
        steps.ended(StepKind.RE_ASK, stepKey, StepResult.DONE);
        return Result.ok(withAnswers(first, again));
    }

    private static BatchReply withAnswers(final BatchReply first, final BatchReply again) {
        final List<ItemOutcome> merged = first.outcomes().stream()
                .map(outcome -> outcome.status() == ItemStatus.MISSING
                        ? again.outcome(outcome.id())
                                .filter(second -> second.status() != ItemStatus.EXTRA)
                                .orElse(outcome)
                        : outcome)
                .toList();
        log.debug("Re-ask answered ids={}", again.acceptedIds());
        return new BatchReply(true, merged, first.cut());
    }
}
