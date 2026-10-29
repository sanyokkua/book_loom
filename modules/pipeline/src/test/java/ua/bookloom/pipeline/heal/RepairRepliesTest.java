package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.pipeline.prompt.DraftReplyParser;

/** A repair call whose model looped flags its segment, as a draft does, instead of failing the run. */
class RepairRepliesTest {

    private static final DraftReplyParser PARSER = new DraftReplyParser(new ObjectMapper());

    @Test
    void read_repeatLimitError_flagsNow() {
        final AppError looped = error(FailureReason.REPEAT_LIMIT);

        final Result<RepairReply> read = RepairReplies.read(Result.err(looped), PARSER, "Source.", "uk");

        assertThat(read.data()).isEqualTo(new RepairReply.FlagNow(looped));
    }

    // Every other reason an upstream fails for is the run's: a pause or an outage, never a flag.
    @ParameterizedTest
    @EnumSource(
            value = FailureReason.class,
            names = {"TERMINATED", "RUNNER_CRASHED", "SERVER_BUSY"})
    void read_otherUpstreamError_isTheRunsToRoute(final FailureReason reason) {
        final AppError failed = error(reason);

        final Result<RepairReply> read = RepairReplies.read(Result.err(failed), PARSER, "Source.", "uk");

        assertThat(read.error()).isEqualTo(failed);
    }

    private static AppError error(final FailureReason reason) {
        return AppError.of(
                ErrorCode.upstream,
                "Provider failed",
                "The provider failed.",
                SafeDetails.empty().withReason(reason).render(),
                null);
    }
}
