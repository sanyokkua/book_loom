package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;

/** The request of each named call says whether the model should think, from the {@link ReasoningPlan} (15i.X2). */
class ChatRequestsReasoningTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));

    @AfterEach
    void clearProperty() {
        System.clearProperty(ReasoningPlan.PROPERTY_NAME);
    }

    // IF nothing is set, THEN every call is asked to think no longer than today: reasoning off.
    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_noPlan_asksForReasoningOffOnEveryCall(final PromptName name) {
        assertThat(ChatRequests.build(name, MESSAGES, null, false).reasoningEnabled())
                .isFalse();
    }

    @Test
    void build_planNamesTheBatchDraft_asksOnlyThatCallToThink() {
        System.setProperty(ReasoningPlan.PROPERTY_NAME, "DRAFT_BATCH_JSON=on");

        assertThat(ChatRequests.build(PromptName.DRAFT_BATCH_JSON, MESSAGES, null, false)
                        .reasoningEnabled())
                .isTrue();
        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false).reasoningEnabled())
                .isFalse();
        assertThat(ChatRequests.build(PromptName.REVIEWER, MESSAGES, null, false)
                        .reasoningEnabled())
                .isFalse();
    }

    // The arms the eval measures are told apart: a term reading and its summary share a call kind, not a plan entry.
    @Test
    void build_planNamesTheTermReading_leavesTheSummaryOff() {
        System.setProperty(ReasoningPlan.PROPERTY_NAME, "TERM_READING=on,BRIEF_SYNTHESIS=on");

        assertThat(ChatRequests.build(PromptName.TERM_READING, MESSAGES, null, false)
                        .reasoningEnabled())
                .isTrue();
        assertThat(ChatRequests.build(PromptName.BRIEF_SYNTHESIS, MESSAGES, null, false)
                        .reasoningEnabled())
                .isTrue();
        assertThat(ChatRequests.build(PromptName.TERM_SUMMARY, MESSAGES, null, false)
                        .reasoningEnabled())
                .isFalse();
    }

    @Test
    void build_thinkingOn_changesNothingElseInTheRequest() {
        final ChatRequest off = ChatRequests.build(PromptName.REVIEWER, MESSAGES, new OutputLimit(40, 160), false);
        System.setProperty(ReasoningPlan.PROPERTY_NAME, "REVIEWER=on");

        final ChatRequest on = ChatRequests.build(PromptName.REVIEWER, MESSAGES, new OutputLimit(40, 160), false);

        assertThat(on)
                .isEqualTo(new ChatRequest(
                        off.messages(),
                        off.temperature(),
                        off.responseFormat(),
                        true,
                        off.contextWindow(),
                        off.expectedOutputTokens(),
                        off.maxOutputTokens(),
                        off.seed(),
                        off.callKind(),
                        off.sampling()));
    }
}
