package ua.bookloom.llm.verify;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;

/** Builds the passed/soft-passed/failed {@link StageOutcome} shapes {@link ProviderVerifierImpl} reports. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
final class StageOutcomes {

    static final String MODEL_LIST_UNAVAILABLE = "model list unavailable";

    private StageOutcomes() {}

    static StageOutcome passed(VerificationStage stage) {
        return passed(stage, null);
    }

    static StageOutcome passed(VerificationStage stage, @Nullable String note) {
        return new StageOutcome(stage, StageStatus.PASSED, null, note);
    }

    static StageOutcome softPass(@Nullable AppError error, int count) {
        log.warn(
                "Provider verification stage soft-passed stage={} code={} note={}",
                VerificationStage.MODELS,
                error == null ? "none" : error.code(),
                MODEL_LIST_UNAVAILABLE);
        return withCount(
                new StageOutcome(VerificationStage.MODELS, StageStatus.SOFT_PASS, error, MODEL_LIST_UNAVAILABLE),
                count);
    }

    static StageOutcome failed(VerificationStage stage, AppError error) {
        log.warn("Provider verification stage failed stage={} code={}", stage, error.code());
        return new StageOutcome(stage, StageStatus.FAILED, error, null);
    }

    static StageOutcome withCount(StageOutcome outcome, int count) {
        return new StageOutcome(
                outcome.stage(), outcome.status(), outcome.error(), outcome.note(), outcome.elapsed(), count);
    }

    static StageOutcome modelUnavailable(AppError original) {
        final AppError unavailable = AppError.of(
                ErrorCode.modelUnavailable,
                "Model unavailable",
                "The provider could not find the selected model.",
                original.details(),
                original.cause());
        return failed(VerificationStage.INFERENCE, unavailable);
    }
}
