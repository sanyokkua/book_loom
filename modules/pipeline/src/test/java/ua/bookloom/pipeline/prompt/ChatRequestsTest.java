package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;

/** Every generation request carries its temperature, format, reasoning off, context size and expected output. */
class ChatRequestsTest {

    private static final List<ChatMessage> MESSAGES = List.of(new ChatMessage(ChatRole.USER, "x"));
    private static final String SOURCE = "He opened the ⟦g0⟧old⟦g1⟧ door.";

    @Test
    void build_draft_carriesContextWindowOutputAllowanceAndDraftSettings() {
        final ChatRequest request =
                ChatRequests.build(PromptName.TRANSLATE, MESSAGES, OutputLimit.forSource(SOURCE, "en", "uk"), false);

        assertThat(request.contextWindow()).isEqualTo(16_384);
        assertThat(request.expectedOutputTokens()).isEqualTo(15);
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.reasoningEnabled()).isEqualTo(ReasoningLevel.OFF);
        assertThat(request.responseFormat()).extracting(ResponseFormat::name).isEqualTo(BatchSchema.FORMAT_NAME);
    }

    // Arm 5b kept the flat schema: every translate-family call asks for an id and a target per item, nothing more.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"TRANSLATE", "FORMAT_REASK", "REPAIR"})
    void build_translateFamily_schemaHasOnlyIdAndTarget(final PromptName name) {
        final ChatRequest request = ChatRequests.build(name, MESSAGES, new OutputLimit(16, 64), false);

        assertThat(request.responseFormat())
                .extracting(ResponseFormat::jsonSchema)
                .isEqualTo("{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\",\"items\":"
                        + "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},"
                        + "\"target\":{\"type\":\"string\"}},\"required\":[\"id\",\"target\"]}}},"
                        + "\"required\":[\"items\"]}");
    }

    @Test
    void build_lowerTemperature_carriesTheLowerOne() {
        assertThat(ChatRequests.build(PromptName.TRANSLATE, MESSAGES, new OutputLimit(16, 64), true)
                        .temperature())
                .isEqualTo(0.1);
    }

    @Test
    void build_structuralRepairOfThatDraft_statesTheDraftsAllowance() {
        assertThat(ChatRequests.build(
                                PromptName.FORMAT_REASK, MESSAGES, OutputLimit.forSource(SOURCE, "en", "uk"), false)
                        .expectedOutputTokens())
                .isEqualTo(15);
    }

    @Test
    void build_limit_carriesExpectedAndCap() {
        final ChatRequest request =
                ChatRequests.build(PromptName.TRANSLATE, MESSAGES, new OutputLimit(414, 661), false);

        assertThat(request.expectedOutputTokens()).isEqualTo(414);
        assertThat(request.maxOutputTokens()).isEqualTo(661);
    }

    @Test
    void build_noLimit_carriesNeither() {
        final ChatRequest request = ChatRequests.build(PromptName.TRANSLATE, MESSAGES, null, false);

        assertThat(request.expectedOutputTokens()).isNull();
        assertThat(request.maxOutputTokens()).isNull();
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void build_anyName_carriesThatNamesCallKindAndNoSeed(final PromptName name) {
        final ChatRequest request = ChatRequests.build(name, MESSAGES, null, false);

        assertThat(request.callKind()).isEqualTo(name.callKind());
        assertThat(request.seed()).isNull();
        // No sampler beyond the temperature: the model's profile or the person's Custom values decide them.
        assertThat(request.sampling()).isNull();
    }
}
