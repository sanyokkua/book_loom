package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.DraftReplyParser;

/** A repair call whose model looped flags its segment, as a draft does, instead of failing the run. */
class RepairRepliesTest {

    private static final DraftReplyParser PARSER = new DraftReplyParser(new ObjectMapper());

    @Test
    void read_repeatLimitError_flagsNow() {
        final AppError looped = error(FailureReason.REPEAT_LIMIT);

        final Result<RepairReply> read =
                RepairReplies.read(Result.err(looped), text -> PARSER.parseRepair(text, "Source.", "uk"));

        assertThat(read.data()).isEqualTo(new RepairReply.FlagNow(looped));
    }

    // Every other reason an upstream fails for is the run's: a pause or an outage, never a flag.
    @ParameterizedTest
    @EnumSource(
            value = FailureReason.class,
            names = {"TERMINATED", "RUNNER_CRASHED", "SERVER_BUSY"})
    void read_otherUpstreamError_isTheRunsToRoute(final FailureReason reason) {
        final AppError failed = error(reason);

        final Result<RepairReply> read =
                RepairReplies.read(Result.err(failed), text -> PARSER.parseRepair(text, "Source.", "uk"));

        assertThat(read.error()).isEqualTo(failed);
    }

    private static Result<RepairReply> readTarget(final String reply, final String source) {
        return RepairReplies.read(
                Result.ok(new ChatResponse(reply, FinishReason.STOP)), text -> PARSER.parseRepair(text, source, "uk"));
    }

    // IF a quote code at an edge of a repair reply made the whole reply malformed, THEN a clean text the model wrote
    // would be thrown away (the U+001E glitch of the Oct 10 runs).
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "{\"target\":\"\\u001eВін відчинив старі двері.\"}",
                "{\"target\":\"Він відчинив старі двері.\\u001d\"}",
                "{\"target\":\"\\u001f Він відчинив старі двері.\"}",
                "{\"target\":\"\\u0001Він відчинив старі двері.\"}",
                "{\"target\":\"Він відчинив старі двері.\\u0007\"}",
                "{\"target\":\"\\u001eВін відчинив старі двері.\\u001d\"}",
                "{\"target\":\"\\u0001Він відчинив старі двері.\\u0007\"}"
            })
    void read_controlCodeAtAnEdgeOfAQuoteFreeText_returnsTheTextWithoutIt(final String reply) {
        final Result<RepairReply> read = readTarget(reply, "He opened the old door.");

        assertThat(read.data()).isEqualTo(new RepairReply.Rewritten("Він відчинив старі двері."));
    }

    // A code inside the text is not at an edge, so stripping the edges never hides a damaged middle.
    @Test
    void read_controlCodeInsideTheText_isStillMalformed() {
        final Result<RepairReply> read =
                readTarget("{\"target\":\"Він відчинив\\u0001 старі двері.\"}", "He opened the old door.");

        assertThat(read.data()).isInstanceOf(RepairReply.Malformed.class);
    }

    @Test
    void read_pairedCodesAroundAQuoteTheSourceHas_mapToGuillemets() {
        final Result<RepairReply> read =
                readTarget("{\"target\":\"\\u001eНе сьогодні\\u001d, — сказала вона.\"}", "\"Not tonight,\" she said.");

        assertThat(read.data()).isEqualTo(new RepairReply.Rewritten("«Не сьогодні», — сказала вона."));
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
