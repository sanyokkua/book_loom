package ua.bookloom.llm.client.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ReasoningLevel;

/** The reasoning object OpenRouter is sent for models whose catalog row does not say how to switch them. */
class OpenRouterReasoningTest {

    private static final ModelFacts MANDATORY_NO_EFFORTS = new ModelFacts(
            null,
            null,
            null,
            null,
            null,
            List.of("thinking"),
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
            true);

    // IF a router were sent no reasoning control (or "none"), THEN it could land on a model that cannot stop reasoning
    // and spend the whole cap thinking or be refused; the lowest effort is a value every landing accepts.
    @Test
    void object_routerModel_holdsEffortAtLow() {
        assertThat(OpenRouterReasoning.object("openrouter/free", ModelFacts.unknown(), ReasoningLevel.OFF))
                .isEqualTo(Map.of("effort", "low"));
    }

    // IF a model that always reasons and lists no effort were sent nothing, THEN it would think at its default effort
    // on every call; the docs say a model listing none takes every effort, so the smallest is asked for.
    @Test
    void object_mandatoryReasoningWithNoListedEffort_asksForMinimal() {
        assertThat(OpenRouterReasoning.object("vendor/always-thinks", MANDATORY_NO_EFFORTS, ReasoningLevel.OFF))
                .isEqualTo(Map.of("effort", "minimal"));
    }
}
