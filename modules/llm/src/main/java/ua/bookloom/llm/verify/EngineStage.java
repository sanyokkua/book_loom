package ua.bookloom.llm.verify;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.engine.EngineDetector;
import ua.bookloom.llm.engine.EngineRegistry;

/**
 * Detects the engine after the connection stage, and fails that stage, naming the engine found, when a provider saved
 * as Ollama-native is another engine: Ollama's native requests mean nothing to LM Studio or llama-server. A detection
 * that finds nothing to name leaves the stage as it was.
 */
@Slf4j
final class EngineStage {

    // A connection that failed this way cannot be asked anything else (a refused key is refused on every route); one
    // that answered with another error can, and a llama-server saved as Ollama answers Ollama's version route with 404.
    private static final Set<ErrorCode> UNASKABLE = EnumSet.of(
            ErrorCode.unreachable, ErrorCode.timeout, ErrorCode.missingCredential, ErrorCode.cancelled, ErrorCode.auth);

    private final EngineRegistry engines;

    EngineStage(final EngineRegistry engines) {
        this.engines = Objects.requireNonNull(engines, "engines");
    }

    /**
     * The connection stage with the engine found.
     *
     * @param config the provider tested
     * @param connection the connection stage's outcome, with its elapsed time
     * @return the stage, failed for an Ollama provider whose server is another engine, and the detection, or none
     *     when nothing answered or the server could not be asked
     */
    Checked check(final ProviderConfig config, final StageOutcome connection) {
        if (EngineDetector.isDocumentsAddress(config)) {
            final AppError documents =
                    Objects.requireNonNull(unsuitable(config, Suitability.NOT_SUITABLE_DOCUMENTS), "documents");
            return new Checked(
                    withElapsed(StageOutcomes.failed(VerificationStage.CONNECTION, documents), connection), null);
        }
        final AppError failure = connection.error();
        if (connection.status() == StageStatus.FAILED && failure != null && UNASKABLE.contains(failure.code())) {
            log.debug("Engine detection skipped provider={} code={}", config.id(), failure.code());
            return new Checked(connection, null);
        }
        final Result<EngineDetection> found = engines.detect(config);
        final EngineDetection detection = found.data();
        if (detection == null) {
            log.debug(
                    "Engine detection gave no answer provider={} code={}",
                    config.id(),
                    Objects.requireNonNull(found.error(), "error").code());
            return new Checked(connection, null);
        }
        return judged(config, connection, detection);
    }

    // An app that cannot translate a book fails the stage first; then an Ollama provider whose server is another one.
    private static Checked judged(
            final ProviderConfig config, final StageOutcome connection, final EngineDetection detection) {
        final @Nullable AppError unsuitable = unsuitable(config, detection.suitability());
        if (unsuitable != null) {
            return new Checked(
                    withElapsed(StageOutcomes.failed(VerificationStage.CONNECTION, unsuitable), connection), detection);
        }
        final boolean mismatch = config.kind() == ProviderKind.OLLAMA
                && detection.engine() != ServerEngine.OLLAMA
                && detection.engine() != ServerEngine.GENERIC;
        log.debug("Engine kind check provider={} engine={} mismatch={}", config.id(), detection.engine(), mismatch);
        return new Checked(mismatch ? notOllama(config, connection, detection.engine()) : connection, detection);
    }

    private static StageOutcome notOllama(
            final ProviderConfig config, final StageOutcome connection, final ServerEngine found) {
        final AppError error = AppError.of(
                ErrorCode.validation,
                "Not an Ollama server",
                "The server at this address is " + found.label() + ", not Ollama.",
                SafeDetails.empty().withEndpoint(config.baseUrl()).render(),
                null);
        return withElapsed(StageOutcomes.failed(VerificationStage.CONNECTION, error), connection);
    }

    // An app that cannot translate a book is stopped here, before any chat request; Anthropic and Gemini hosts are only
    // named by detection ("not supported yet") and not refused, since no requirement stops them at this stage.
    private static @Nullable AppError unsuitable(final ProviderConfig config, final Suitability suitability) {
        final @Nullable FailureReason reason =
                switch (suitability) {
                    case NOT_SUITABLE_STRUCTURED -> FailureReason.NOT_SUITABLE_STRUCTURED;
                    case NOT_SUITABLE_DOCUMENTS -> FailureReason.NOT_SUITABLE_DOCUMENTS;
                    case SUITABLE, NOT_SUPPORTED_YET -> null;
                };
        log.debug(
                "Engine suitability check provider={} suitability={} refused={}",
                config.id(),
                suitability,
                reason != null);
        if (reason == null) {
            return null;
        }
        final String message = reason == FailureReason.NOT_SUITABLE_STRUCTURED
                ? "This app does not support structured replies, so BookLoom cannot use it."
                : "This address chats with documents, not with a model.";
        log.warn("Provider refused at the connection test reason={} provider={}", reason.token(), config.id());
        return AppError.of(
                ErrorCode.validation,
                "This app cannot be used",
                message,
                SafeDetails.empty()
                        .withReason(reason)
                        .withEndpoint(config.baseUrl())
                        .render(),
                null);
    }

    private static StageOutcome withElapsed(final StageOutcome failed, final StageOutcome connection) {
        final Duration elapsed = connection.elapsed();
        return elapsed == null ? failed : failed.withElapsed(elapsed);
    }

    /**
     * The connection stage after the engine check.
     *
     * @param outcome the stage's outcome
     * @param engine the engine found, or null when none was
     */
    record Checked(StageOutcome outcome, @Nullable EngineDetection engine) {}
}
