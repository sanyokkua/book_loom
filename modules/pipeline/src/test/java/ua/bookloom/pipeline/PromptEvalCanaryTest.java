package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * The {@code promptEval} canary: proof that the second local-only tag is partitioned exactly like the first.
 *
 * <p>Prompt evals call the <em>production</em> prompt builder against a real local model and score the result with
 * an embedding scorer. That makes them the one place in the project where an embedding model is loaded at all —
 * it exists in this test harness and never in application runtime — and it is why they cannot run on a CI runner
 * ({@code docs/specification/04_Build_and_Release/06_TESTING_STRATEGY.md#prompt-evals}, DD-35).
 *
 * <p>The evals themselves arrive with the prompts they score, one per pipeline prompt. This class proves the tag
 * is excluded from {@code check} and reachable from {@code ./gradlew promptEval}.
 *
 * <p><strong>Required environment:</strong> {@code BOOKLOOM_PROMPT_EVAL_URL} — the endpoint serving both the
 * model under evaluation and the embedding model used to score it.
 */
@Tag("promptEval")
class PromptEvalCanaryTest {

    // Covers task 5.3/5.5: the `promptEval` task reaches a `promptEval`-tagged test when its endpoint is
    // configured, and skips cleanly when the variable is absent.
    @Test
    @EnabledIfEnvironmentVariable(named = "BOOKLOOM_PROMPT_EVAL_URL", matches = "\\S+")
    void promptEval_evaluationEndpointConfigured_readsAnAbsoluteEndpoint() {
        assertThat(URI.create(System.getenv("BOOKLOOM_PROMPT_EVAL_URL")).isAbsolute())
                .as("BOOKLOOM_PROMPT_EVAL_URL must be an absolute URL, e.g. http://localhost:11434")
                .isTrue();
    }
}
