package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;

/** The request of each named call carries the level its {@link RequestDefaults} give it (15i.X2, finalize 4.8a). */
class ChatRequestsReasoningTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));

    private static RequestDefaults plan(final String spec) {
        return new RequestDefaults(
                ReasoningPlan.resolve(Map.of(ReasoningPlan.ENV_NAME, spec), null), SamplingMode.SERVER);
    }

    private static RequestDefaults stored(final ReasoningLevel level) {
        return new RequestDefaults(ReasoningPlan.stored(level), SamplingMode.SERVER);
    }

    // IF nothing is set, THEN every call is asked to think no longer than today: reasoning off.
    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_offDefaults_asksForReasoningOffOnEveryCall(final PromptName name) {
        assertThat(ChatRequests.build(name, MESSAGES, null, false, stored(ReasoningLevel.OFF))
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.OFF);
    }

    // IF the stored setting is Low, THEN every call carries Low.
    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_storedLow_asksForLowOnEveryCall(final PromptName name) {
        assertThat(ChatRequests.build(name, MESSAGES, null, false, stored(ReasoningLevel.LOW))
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.LOW);
    }

    @Test
    void build_storedOn_asksForOnOnTheDraft() {
        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false, stored(ReasoningLevel.ON))
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.ON);
    }

    @Test
    void build_planNamesTheBatchDraft_asksOnlyThatCallToThink() {
        final RequestDefaults defaults = plan("DRAFT_BATCH_JSON=on");

        assertThat(ChatRequests.build(PromptName.DRAFT_BATCH_JSON, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.ON);
        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.OFF);
        assertThat(ChatRequests.build(PromptName.REVIEWER, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.OFF);
    }

    // The arms the eval measures are told apart: a term reading and its summary share a call kind, not a plan entry.
    @Test
    void build_planNamesTheTermReading_leavesTheSummaryOff() {
        final RequestDefaults defaults = plan("TERM_READING=on,BRIEF_SYNTHESIS=low");

        assertThat(ChatRequests.build(PromptName.TERM_READING, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.ON);
        assertThat(ChatRequests.build(PromptName.BRIEF_SYNTHESIS, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.LOW);
        assertThat(ChatRequests.build(PromptName.TERM_SUMMARY, MESSAGES, null, false, defaults)
                        .reasoningEnabled())
                .isEqualTo(ReasoningLevel.OFF);
    }

    @Test
    void build_thinkingOn_changesNothingElseInTheRequest() {
        final ChatRequest off = ChatRequests.build(
                PromptName.REVIEWER, MESSAGES, new OutputLimit(40, 160), false, stored(ReasoningLevel.OFF));

        final ChatRequest on =
                ChatRequests.build(PromptName.REVIEWER, MESSAGES, new OutputLimit(40, 160), false, plan("REVIEWER=on"));

        assertThat(on)
                .isEqualTo(new ChatRequest(
                        off.messages(),
                        off.temperature(),
                        off.responseFormat(),
                        ReasoningLevel.ON,
                        off.contextWindow(),
                        off.expectedOutputTokens(),
                        off.maxOutputTokens(),
                        off.seed(),
                        off.callKind(),
                        off.sampling()));
    }
}
