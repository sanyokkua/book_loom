package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelFactsTest {

    @Test
    void unknown_hasNothingAndIsAChatModel() {
        final ModelFacts facts = ModelFacts.unknown();

        assertThat(facts.contextLength()).isNull();
        assertThat(facts.capabilities()).isEmpty();
        assertThat(facts.kind()).isEqualTo(ModelKind.LLM);
    }

    @Test
    void kind_capabilitiesWithoutCompletion_isEmbedding() {
        assertThat(withCapabilities(List.of("embedding")).kind()).isEqualTo(ModelKind.EMBEDDING);
    }

    @Test
    void kind_visionAndCompletion_isStillAChatModel() {
        assertThat(withCapabilities(List.of("completion", "vision")).kind()).isEqualTo(ModelKind.LLM);
    }

    @Test
    void new_nonPositiveContext_isRefused() {
        assertThatThrownBy(() -> new ModelFacts(
                        null, null, null, null, null, List.of(), List.of(), null, null, null, null, null, null, null, 0,
                        null, List.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ModelFacts withCapabilities(final List<String> capabilities) {
        return new ModelFacts(
                null,
                null,
                null,
                null,
                null,
                capabilities,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                false);
    }
}
