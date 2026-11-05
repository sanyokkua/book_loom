package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SourceSize;

/**
 * Every named call asks for thinking off, whatever the environment says; the model's profile and the person's choice on
 * Settings › Models decide what is sent (design D20). The call also states the source it sends, so a model that thinks
 * on every call is given room in proportion.
 */
class ChatRequestsReasoningTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_anyCall_asksForReasoningOff(final PromptName name) {
        assertThat(ChatRequests.build(name, MESSAGES, null, false).reasoningEnabled())
                .isEqualTo(ReasoningLevel.OFF);
    }

    @Test
    void build_limitWithASourceSize_carriesItToTheRequest() {
        final OutputLimit limit = new OutputLimit(40, 160, new SourceSize(300, 4));

        assertThat(ChatRequests.build(PromptName.TRANSLATE, MESSAGES, limit, false)
                        .source())
                .isEqualTo(new SourceSize(300, 4));
    }

    @Test
    void build_noLimit_statesNoSource() {
        assertThat(ChatRequests.build(PromptName.TRANSLATE, MESSAGES, null, false)
                        .source())
                .isNull();
    }
}
