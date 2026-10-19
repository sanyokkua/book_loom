package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Contract tests for {@link StageOutcome}. */
class StageOutcomeTest {

    @Test
    void fourArgConstructor_defaultsElapsedAndCountToNull() {
        final StageOutcome outcome = new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null);

        assertThat(outcome.elapsed()).isNull();
        assertThat(outcome.count()).isNull();
    }

    @Test
    void sixArgConstructor_preservesElapsedAndCount() {
        final StageOutcome outcome =
                new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null, Duration.ofMillis(41), 3);

        assertThat(outcome.elapsed()).isEqualTo(Duration.ofMillis(41));
        assertThat(outcome.count()).isEqualTo(3);
    }
}
