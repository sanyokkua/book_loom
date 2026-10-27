package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

    @Test
    void sixArgConstructor_hasNoExchange() {
        final StageOutcome outcome =
                new StageOutcome(VerificationStage.MODELS, StageStatus.PASSED, null, null, Duration.ofMillis(41), 3);

        assertThat(outcome.exchange()).isNull();
    }

    @Test
    void canonicalConstructor_keepsTheExchange() {
        final VerificationExchange exchange = new VerificationExchange(
                "POST", "http://localhost:11434/api/chat", "{\"q\":1}", "{\"a\":2}", 200, 120L, 31.5, true);
        final StageOutcome outcome = new StageOutcome(
                VerificationStage.INFERENCE, StageStatus.PASSED, null, null, Duration.ofMillis(900), null, exchange);

        assertThat(outcome.exchange()).isEqualTo(exchange);
    }

    @Test
    void verificationExchange_blankMethod_isRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VerificationExchange("", "http://h/x", "", "", 200, null, null, false));
    }

    @Test
    void withElapsed_outcomeWithExchange_keepsTheExchange() {
        final VerificationExchange exchange =
                new VerificationExchange("GET", "http://h/api/tags", "", "{}", 200, null, null, false);
        final StageOutcome outcome = new StageOutcome(
                        VerificationStage.MODELS, StageStatus.PASSED, null, null, null, 2, exchange)
                .withElapsed(Duration.ofMillis(7));

        assertThat(outcome.elapsed()).isEqualTo(Duration.ofMillis(7));
        assertThat(outcome.exchange()).isEqualTo(exchange);
        assertThat(outcome.count()).isEqualTo(2);
    }
}
