package ua.bookloom.llm;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.RetryRule;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * Decides whether a failed chat request is sent again, and how. The matched error row's {@link RetryRule} decides,
 * because the same status means different things on different engines; a failure no row explains keeps the code's own
 * rule, and {@link RetryPolicy} keeps the counts, the backoff and the timeout ladder (design D18).
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RetryChoice {

    /** The resend was not made because the reason's one resend was already spent on this call. */
    static final String ONCE_USED = "once-used";

    /** The resend was not made because the call's attempts are spent. */
    static final String ATTEMPTS_SPENT = "attempts-spent";

    /** How the next request of a failed call goes out, if at all. */
    enum Resend {

        /** Not sent again: the failure goes to the caller. */
        NONE,

        /** The same request, once, after a check that the server answers at all. */
        SAME,

        /** The same request with another seed, once. */
        NEW_SEED,

        /** The original request with a new seed and a lower cap, as after every timeout. */
        VARIED_AFTER_TIMEOUT,

        /** The same request after a growing pause, within the attempt budget. */
        BACKOFF
    }

    /**
     * What to do after one failed request.
     *
     * @param resend how the next request goes out, or {@link Resend#NONE}
     * @param reason the failure's reason, or {@code null} when its details name none
     * @param rule the matched row's rule, or {@code null} when no row has the reason
     * @param why the word the decision is logged with
     */
    record Decision(
            Resend resend,
            @Nullable FailureReason reason,
            @Nullable RetryRule rule,
            String why) {

        /**
         * Whether a resend the failure would have earned was refused because its budget is spent.
         *
         * @return {@code true} if the call gave up, {@code false} if it was never to be repeated
         */
        boolean isGaveUp() {
            return ONCE_USED.equals(why) || ATTEMPTS_SPENT.equals(why);
        }
    }

    /**
     * Chooses how a failed request is sent again.
     *
     * @param error the failure the request answered
     * @param failedAttempt the attempt that failed, counted from one
     * @param timedOutBefore how many earlier attempts of this call timed out
     * @param resentOnce the reasons whose one resend this call has already made
     * @param policy the attempt counts, the backoff and the timeout ladder
     * @return the decision; never null
     */
    static Decision decide(
            final AppError error,
            final int failedAttempt,
            final int timedOutBefore,
            final Set<FailureReason> resentOnce,
            final RetryPolicy policy) {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(resentOnce, "resentOnce");
        Objects.requireNonNull(policy, "policy");
        final Optional<FailureReason> reason = FailureReason.in(error.details());
        final Decision decision = error.code() == ErrorCode.timeout
                ? ladder(policy.shouldRetryTimeout(failedAttempt, timedOutBefore + 1), reason)
                : byRule(error.code(), reason, failedAttempt, resentOnce, policy);
        log.debug(
                "Retry rule chosen reason={} rule={} failedAttempt={} resend={} why={}",
                decision.reason() == null ? "none" : decision.reason().token(),
                decision.rule(),
                failedAttempt,
                decision.resend(),
                decision.why());
        return decision;
    }

    private static Decision ladder(final boolean retry, final Optional<FailureReason> reason) {
        return new Decision(
                retry ? Resend.VARIED_AFTER_TIMEOUT : Resend.NONE,
                reason.orElse(null),
                null,
                retry ? "timeout-ladder" : ATTEMPTS_SPENT);
    }

    private static Decision byRule(
            final ErrorCode code,
            final Optional<FailureReason> reason,
            final int failedAttempt,
            final Set<FailureReason> resentOnce,
            final RetryPolicy policy) {
        final Optional<RetryRule> rule = reason.flatMap(HttpErrorMapper::retryRule);
        if (rule.isEmpty()) {
            return backoff(code, reason.orElse(null), null, failedAttempt, policy, "unmatched");
        }
        // A rule is only ever found through a reason.
        final FailureReason found = reason.orElseThrow();
        return switch (rule.get()) {
            case NONE -> new Decision(Resend.NONE, found, RetryRule.NONE, "rule-none");
            case AUTO_WAIT -> new Decision(Resend.NONE, found, RetryRule.AUTO_WAIT, "run-waits");
            case SAME -> once(Resend.SAME, RetryRule.SAME, found, failedAttempt, resentOnce);
            case NEW_SEED -> once(Resend.NEW_SEED, RetryRule.NEW_SEED, found, failedAttempt, resentOnce);
            case BACKOFF -> backoff(code, found, RetryRule.BACKOFF, failedAttempt, policy, "rule-backoff");
        };
    }

    private static Decision once(
            final Resend resend,
            final RetryRule rule,
            final FailureReason reason,
            final int failedAttempt,
            final Set<FailureReason> resentOnce) {
        if (resentOnce.contains(reason)) {
            return new Decision(Resend.NONE, reason, rule, ONCE_USED);
        }
        return failedAttempt < RetryPolicy.MAX_ATTEMPTS
                ? new Decision(resend, reason, rule, "rule-once")
                : new Decision(Resend.NONE, reason, rule, ATTEMPTS_SPENT);
    }

    private static Decision backoff(
            final ErrorCode code,
            final @Nullable FailureReason reason,
            final @Nullable RetryRule rule,
            final int failedAttempt,
            final RetryPolicy policy,
            final String why) {
        if (policy.shouldRetry(code, failedAttempt)) {
            return new Decision(Resend.BACKOFF, reason, rule, why);
        }
        return new Decision(Resend.NONE, reason, rule, code.isRetryable() ? ATTEMPTS_SPENT : "not-retryable");
    }
}
