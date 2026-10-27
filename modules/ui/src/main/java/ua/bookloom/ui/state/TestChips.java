package ua.bookloom.ui.state;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;

/** Turns what the verifier reported for a stage into the chip the screen shows, and logs it. */
// Checkstyle parses source text before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class TestChips {

    static StageChip of(final VerificationStage stage, final @Nullable StageOutcome outcome) {
        return outcome == null
                ? new StageChip(stage, StageStatus.SKIPPED, null, null)
                : new StageChip(
                        stage,
                        outcome.status(),
                        outcome.error(),
                        outcome.note(),
                        outcome.elapsed(),
                        outcome.count(),
                        outcome.exchange());
    }

    static boolean isPass(final StageChip chip) {
        return chip.status() == StageStatus.PASSED || chip.status() == StageStatus.SOFT_PASS;
    }

    static void log(final StageChip chip) {
        final Duration elapsed = chip.elapsed();
        log.info(
                "stage {}: {} in {} ms, {} model(s)",
                chip.stage(),
                chip.status(),
                elapsed == null ? "?" : elapsed.toMillis(),
                chip.count() == null ? "-" : chip.count());
        final AppError error = chip.error();
        if (chip.status() == StageStatus.FAILED) {
            log.warn("stage {} failed with {}", chip.stage(), error == null ? "no error code" : error.code());
        }
    }
}
