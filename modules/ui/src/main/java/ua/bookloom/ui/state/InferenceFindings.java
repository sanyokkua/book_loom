package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.api.pipeline.CallKind;

/**
 * Keeps what a passing inference test of a saved provider measured: the seconds per segment on the model, and whether
 * it answered in the structured form for the session. Split from {@link ProviderTestRunner}; FX thread only.
 */
@Slf4j
final class InferenceFindings {

    private final ModelSpeedRecorder speeds;
    private final StructuredAnswers structured;

    InferenceFindings(final ModelSpeedRecorder speeds, final StructuredAnswers structured) {
        this.speeds = Objects.requireNonNull(speeds, "speeds");
        this.structured = Objects.requireNonNull(structured, "structured");
    }

    // Only a saved provider has a row to keep a speed on, and only the inference test measures one.
    void keep(final ProviderTest test, final TestSubject target, final List<StageChip> chips) {
        final String model = target.model().strip();
        final Optional<StageChip> inference = chips.stream()
                .filter(chip -> chip.stage() == VerificationStage.INFERENCE && chip.status() == StageStatus.PASSED)
                .findFirst();
        final boolean keep = test == ProviderTest.INFERENCE && target.stored() && !model.isEmpty();
        log.debug(
                "speed of {} kept {}: needs the inference test {}, a saved provider as saved {}, a model {}, a pass {}",
                target.config().id(),
                keep && inference.isPresent(),
                test == ProviderTest.INFERENCE,
                target.stored(),
                !model.isEmpty(),
                inference.isPresent());
        if (keep && inference.isPresent() && inference.get().elapsed() != null) {
            speeds.record(
                    target.config().id(), model, CallKind.DRAFT, inference.get().elapsed(), MeasuredFrom.TEST);
        }
        final VerificationExchange exchange = inference.map(StageChip::exchange).orElse(null);
        if (keep && exchange != null) {
            structured.record(target.config().id(), model, exchange.structured());
        }
    }
}
