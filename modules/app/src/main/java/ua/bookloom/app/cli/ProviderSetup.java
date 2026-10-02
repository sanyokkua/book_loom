package ua.bookloom.app.cli;

import com.google.inject.Inject;
import java.io.PrintStream;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.pipeline.ProviderProbe;

/**
 * The command's provider: registers the one its flags name (a preset with any overrides, or a custom OpenAI-compatible
 * endpoint), checks it before any book work — printing one line per stage — and gives the probe a run checks it with
 * while it waits through an outage. The offline pseudo model needs none of this.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class ProviderSetup {

    static final String PSEUDO_PROVIDER = "pseudo";
    private static final String PSEUDO_MODEL = "uppercase";
    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai-compatible";

    private final ProviderConfigs providerConfigs;
    private final ProviderVerifier verifier;

    /**
     * Registers the provider the arguments name and answers the selection to translate with.
     *
     * @param arguments the parsed command; never null
     * @return the selection, or the error that the registration answered ({@code validation} for a bad configuration)
     */
    Result<ModelSelection> select(TranslateArguments arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (PSEUDO_PROVIDER.equals(arguments.providerId())) {
            return Result.ok(new ModelSelection(PSEUDO_PROVIDER, PSEUDO_MODEL));
        }
        final ProviderConfig base = OPENAI_COMPATIBLE_PROVIDER.equals(arguments.providerId())
                ? customConfig(arguments)
                : presetConfig(arguments.providerId());
        final Result<ProviderConfig> registered = providerConfigs.register(applyOverrides(base, arguments));
        if (registered.isErr()) {
            final AppError error = Objects.requireNonNull(registered.error(), "error");
            return Result.err(AppError.of(
                    ErrorCode.validation,
                    "Invalid command arguments",
                    error.message(),
                    error.details(),
                    error.cause()));
        }
        return Result.ok(
                new ModelSelection(arguments.providerId(), Objects.requireNonNull(arguments.modelId(), "modelId")));
    }

    /**
     * Checks the provider and model before the book is touched, printing each stage.
     *
     * @param selection the selection to check; never null
     * @param out where each stage's line goes; never null
     * @return {@code true} if every stage passed or passed softly, {@code false} after printing why not
     */
    boolean preflight(ModelSelection selection, PrintStream out) {
        log.debug("translate command preflight provider={} model={}", selection.providerId(), selection.modelId());
        final Result<VerificationReport> result = verifier.verify(selection, VerificationPolicy.PREFLIGHT);
        if (result.isErr()) {
            final AppError error = Objects.requireNonNull(result.error(), "error");
            out.println(error.title() + ": " + error.message());
            return false;
        }
        for (final StageOutcome outcome :
                Objects.requireNonNull(result.data(), "report").stages()) {
            if (!printStage(outcome, out)) {
                return false;
            }
        }
        log.debug("translate command preflight passed");
        return true;
    }

    /**
     * The probe a run checks the provider with while it waits through an outage.
     *
     * @param selection the run's selection; never null
     * @return the provider's probe, or one that always passes for the offline pseudo model
     */
    ProviderProbe probeFor(ModelSelection selection) {
        return PSEUDO_PROVIDER.equals(selection.providerId())
                ? ProviderProbe.ASSUME_REACHABLE
                : ProviderProbe.of(verifier, selection);
    }

    private static boolean printStage(StageOutcome outcome, PrintStream out) {
        final String stage = outcome.stage().name().toLowerCase(Locale.ROOT);
        log.debug("translate command preflight stage={} status={} note={}", stage, outcome.status(), outcome.note());
        final String note = outcome.note();
        switch (outcome.status()) {
            case PASSED, SOFT_PASS -> out.println(note == null ? stage + ": ok" : stage + ": ok (" + note + ")");
            case SKIPPED -> out.println(stage + ": skipped");
            case FAILED -> {
                final AppError error = Objects.requireNonNull(outcome.error(), "failed stage error");
                out.println(stage + ": failed - " + error.title());
                out.println(error.message());
                return false;
            }
        }
        return true;
    }

    private static ProviderConfig customConfig(TranslateArguments arguments) {
        return new ProviderConfig(
                OPENAI_COMPATIBLE_PROVIDER,
                ProviderKind.OPENAI_COMPATIBLE,
                Objects.requireNonNull(arguments.baseUrl(), "baseUrl"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }

    private ProviderConfig presetConfig(String providerId) {
        final Optional<ProviderConfig> preset = providerConfigs.find(providerId);
        return preset.orElseThrow(() -> new IllegalStateException("missing configured provider: " + providerId));
    }

    private static ProviderConfig applyOverrides(ProviderConfig config, TranslateArguments arguments) {
        final URI baseUrl = arguments.baseUrl();
        final Duration requestTimeout = arguments.requestTimeout();
        final ProviderConfig withUrl = baseUrl == null ? config : config.withBaseUrl(baseUrl);
        return requestTimeout == null ? withUrl : withUrl.withRequestTimeout(requestTimeout);
    }
}
