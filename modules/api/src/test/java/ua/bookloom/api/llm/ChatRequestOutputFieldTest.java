package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatRequestOutputFieldTest {

    private static ChatRequest request(final OutputLengthField field) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "hi")),
                0.3,
                null,
                null,
                null,
                null,
                100,
                null,
                null,
                null,
                field);
    }

    @Test
    void tenArgumentConstructor_leavesTheOutputFieldNull() {
        assertThat(new ChatRequest(List.of(), null, null, null, null, null, null, null, null, null).outputField())
                .isNull();
    }

    @Test
    void shortConstructors_leaveTheOutputFieldNull() {
        assertThat(new ChatRequest(List.of()).outputField()).isNull();
    }

    @Test
    void withoutResponseFormat_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS)
                        .withoutResponseFormat()
                        .outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void withoutReasoning_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS)
                        .withoutReasoning()
                        .outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void withSeed_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS).withSeed(7).outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void sizedTo_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS)
                        .sizedTo(4096, 50)
                        .outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void forRetry_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS)
                        .forRetry(3, 50)
                        .outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }

    @Test
    void withSampling_carriesTheOutputField() {
        assertThat(request(OutputLengthField.MAX_COMPLETION_TOKENS)
                        .withSampling(SamplingParams.NONE)
                        .outputField())
                .isEqualTo(OutputLengthField.MAX_COMPLETION_TOKENS);
    }
}
