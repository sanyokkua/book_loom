package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ResponseFormat;

/** Every generation request carries its temperature, format, reasoning off, context size and expected output. */
class ChatRequestsTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));
    private static final String SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";

    @Test
    void build_draft_carriesContextWindowOutputAllowanceAndDraftSettings() {
        final ChatRequest request =
                ChatRequests.build(PromptName.DRAFT, MESSAGES, OutputLimit.forSource(SOURCE, "en", "uk"), false);

        assertThat(request.contextWindow()).isEqualTo(16_384);
        assertThat(request.expectedOutputTokens()).isEqualTo(16);
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.reasoningEnabled()).isFalse();
        assertThat(request.responseFormat()).extracting(ResponseFormat::name).isEqualTo("draft");
    }

    @Test
    void build_lowerTemperature_carriesTheLowerOne() {
        assertThat(ChatRequests.build(PromptName.DRAFT, MESSAGES, new OutputLimit(16, 64), true)
                        .temperature())
                .isEqualTo(0.1);
    }

    @Test
    void build_structuralRepairOfThatDraft_statesTheDraftsAllowance() {
        assertThat(ChatRequests.build(
                                PromptName.STRUCTURAL_REPAIR,
                                MESSAGES,
                                OutputLimit.forSource(SOURCE, "en", "uk"),
                                false)
                        .expectedOutputTokens())
                .isEqualTo(16);
    }

    @Test
    void build_limit_carriesExpectedAndCap() {
        final ChatRequest request = ChatRequests.build(PromptName.DRAFT, MESSAGES, new OutputLimit(414, 661), false);

        assertThat(request.expectedOutputTokens()).isEqualTo(414);
        assertThat(request.maxOutputTokens()).isEqualTo(661);
    }

    @Test
    void build_noLimit_carriesNeither() {
        final ChatRequest request = ChatRequests.build(PromptName.DRAFT, MESSAGES, null, false);

        assertThat(request.expectedOutputTokens()).isNull();
        assertThat(request.maxOutputTokens()).isNull();
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_anyName_carriesThatNamesCallKindAndNoSeed(final PromptName name) {
        final ChatRequest request = ChatRequests.build(name, MESSAGES, null, false);

        assertThat(request.callKind()).isEqualTo(name.callKind());
        assertThat(request.seed()).isNull();
    }
}
