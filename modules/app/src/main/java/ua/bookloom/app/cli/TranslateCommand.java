package ua.bookloom.app.cli;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Parses and executes the one-book translation command behind the public launcher. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@com.google.inject.Inject})
public final class TranslateCommand {

    private static final String PSEUDO_PROVIDER = "pseudo";
    private static final String PSEUDO_MODEL = "uppercase";
    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai-compatible";
    private static final String USAGE = "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite] "
            + "[--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] "
            + "[--base-url <url>] [--timeout <seconds>]";

    private final TranslationEngine engine;
    private final ChatModelFactory models;
    private final ProviderConfigs providerConfigs;
    private final ProviderVerifier verifier;

    /** Runs one parsed command and reports only its user-facing result to the supplied stream. */
    public int run(List<String> args, PrintStream out) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        log.info("translate command arguments={}", args);
        int exit;
        try {
            final Result<TranslateArguments> parsed = TranslateArguments.parse(args);
            exit = parsed.isErr() ? usageFailure(errorOf(parsed), out) : execute(dataOf(parsed), out);
        } catch (Throwable cause) {
            exit = unexpectedFailure(out, cause);
        }
        log.info("translate command exitCode={}", exit);
        return exit;
    }

    private int execute(TranslateArguments arguments, PrintStream out) {
        final Path destination = destination(arguments);
        log.debug(
                "translate command parsed source={} destination={} targetLanguage={} sourceLanguage={} overwrite={}",
                arguments.source(),
                destination,
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.overwrite());
        final Result<ModelSelection> selection = selectModel(arguments);
        if (selection.isErr()) {
            return selectionFailure(errorOf(selection), out);
        }
        final ModelSelection selected = dataOf(selection);
        log.info(
                "translate command provider={} model={} targetLanguage={} sourceLanguage={} overwrite={} baseUrl={} requestTimeout={}",
                selected.providerId(),
                selected.modelId(),
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.overwrite(),
                arguments.baseUrl(),
                arguments.requestTimeout());
        if (!PSEUDO_PROVIDER.equals(selected.providerId()) && !preflight(selected, out)) {
            return 1;
        }
        return translate(arguments, destination, selected, out);
    }

    private Result<ModelSelection> selectModel(TranslateArguments arguments) {
        if (PSEUDO_PROVIDER.equals(arguments.providerId())) {
            return Result.ok(new ModelSelection(PSEUDO_PROVIDER, PSEUDO_MODEL));
        }
        final Result<ProviderConfig> configured = providerConfig(arguments);
        if (configured.isErr()) {
            return Result.err(errorOf(configured));
        }
        final Result<ProviderConfig> registered = providerConfigs.register(dataOf(configured));
        if (registered.isErr()) {
            return Result.err(usageError(errorOf(registered)));
        }
        return Result.ok(
                new ModelSelection(arguments.providerId(), Objects.requireNonNull(arguments.modelId(), "modelId")));
    }

    private Result<ProviderConfig> providerConfig(TranslateArguments arguments) {
        final ProviderConfig base = OPENAI_COMPATIBLE_PROVIDER.equals(arguments.providerId())
                ? customConfig(arguments)
                : presetConfig(arguments.providerId());
        return Result.ok(applyOverrides(base, arguments));
    }

    private ProviderConfig customConfig(TranslateArguments arguments) {
        return new ProviderConfig(
                OPENAI_COMPATIBLE_PROVIDER,
                ProviderKind.OPENAI_COMPATIBLE,
                Objects.requireNonNull(arguments.baseUrl(), "baseUrl"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }

    private ProviderConfig presetConfig(String providerId) {
        final Optional<ProviderConfig> preset = providerConfigs.find(providerId);
        if (preset.isEmpty()) {
            throw new IllegalStateException("missing configured provider: " + providerId);
        }
        return preset.orElseThrow();
    }

    private static ProviderConfig applyOverrides(ProviderConfig config, TranslateArguments arguments) {
        final URI baseUrl = arguments.baseUrl();
        final Duration requestTimeout = arguments.requestTimeout();
        final ProviderConfig withUrl = baseUrl == null ? config : config.withBaseUrl(baseUrl);
        return requestTimeout == null ? withUrl : withUrl.withRequestTimeout(requestTimeout);
    }

    private boolean preflight(ModelSelection selection, PrintStream out) {
        final Result<VerificationReport> result = verifier.verify(selection, VerificationPolicy.PREFLIGHT);
        if (result.isErr()) {
            printError(errorOf(result), out);
            return false;
        }
        for (StageOutcome outcome : dataOf(result).stages()) {
            if (!printStage(outcome, out)) {
                return false;
            }
        }
        return true;
    }

    private boolean printStage(StageOutcome outcome, PrintStream out) {
        final String stage = outcome.stage().name().toLowerCase(Locale.ROOT);
        log.debug("translate command preflight stage={} status={} note={}", stage, outcome.status(), outcome.note());
        return switch (outcome.status()) {
            case PASSED -> successfulStage(stage, outcome, out);
            case SOFT_PASS -> softPassedStage(stage, outcome, out);
            case SKIPPED -> skippedStage(stage, out);
            case FAILED -> failedStage(stage, outcome, out);
        };
    }

    private static boolean successfulStage(String stage, StageOutcome outcome, PrintStream out) {
        final String note = outcome.note();
        out.println(note == null ? stage + ": ok" : stage + ": ok (" + note + ")");
        return true;
    }

    private static boolean softPassedStage(String stage, StageOutcome outcome, PrintStream out) {
        out.println(stage + ": ok (" + Objects.requireNonNull(outcome.note(), "note") + ")");
        return true;
    }

    private static boolean skippedStage(String stage, PrintStream out) {
        out.println(stage + ": skipped");
        return true;
    }

    private static boolean failedStage(String stage, StageOutcome outcome, PrintStream out) {
        final AppError error = Objects.requireNonNull(outcome.error(), "failed stage error");
        out.println(stage + ": failed - " + error.title());
        out.println(error.message());
        return false;
    }

    private int translate(TranslateArguments arguments, Path destination, ModelSelection selection, PrintStream out) {
        final Result<ChatModel> model = models.create(selection);
        if (model.isErr()) {
            return errorFailure(model, out);
        }
        final TranslationRequest request = new TranslationRequest(
                arguments.source(),
                destination,
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.overwrite());
        final Result<TranslationJob> job = engine.newJob(request, dataOf(model));
        if (job.isErr()) {
            return errorFailure(job, out);
        }
        final TranslationJob translationJob = dataOf(job);
        translationJob.pauseAt(Set.of());
        return report(translationJob.run(), out);
    }

    private int report(Result<JobReport> result, PrintStream out) {
        if (result.isErr()) {
            return errorFailure(result, out);
        }
        final JobReport report = dataOf(result);
        log.debug(
                "translate command report state={} accepted={} flagged={} written={}",
                report.end(),
                report.accepted(),
                report.flagged(),
                report.written());
        if (report.end() != JobState.COMPLETED) {
            return printError(report.error() != null ? report.error() : stoppedError(report.end()), out);
        }
        final Path written = Objects.requireNonNull(report.written(), "completed report must have a written path");
        out.println(
                "Completed: " + written + " (accepted=" + report.accepted() + ", flagged=" + report.flagged() + ")");
        return 0;
    }

    private int selectionFailure(AppError error, PrintStream out) {
        return error.code() == ErrorCode.validation ? usageFailure(error, out) : printError(error, out);
    }

    private int usageFailure(AppError error, PrintStream out) {
        log.warn("Rejected translate command arguments reason={}", error.message());
        out.println(error.title() + ": " + error.message());
        out.println(USAGE);
        return 2;
    }

    private int errorFailure(Result<?> result, PrintStream out) {
        return printError(errorOf(result), out);
    }

    private int unexpectedFailure(PrintStream out, Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Translation command failed",
                "An unexpected error prevented the translation command from completing.",
                null,
                cause);
        log.error("Unexpected translate command failure code={}", error.code(), cause);
        return printError(error, out);
    }

    private int printError(AppError error, PrintStream out) {
        log.debug("translate command error code={} title={}", error.code(), error.title());
        out.println(error.title() + ": " + error.message());
        return 1;
    }

    private static AppError usageError(AppError error) {
        return AppError.of(
                ErrorCode.validation, "Invalid command arguments", error.message(), error.details(), error.cause());
    }

    private static Path destination(TranslateArguments arguments) {
        final String fileName =
                Objects.requireNonNull(arguments.source().getFileName()).toString();
        final String suffix = arguments.format().matchedSuffix(fileName);
        final String outputName =
                fileName.substring(0, fileName.length() - suffix.length()) + "." + arguments.targetLanguage() + suffix;
        final Path parent = arguments.source().getParent();
        return parent == null ? Path.of(outputName) : parent.resolve(outputName);
    }

    private static AppError stoppedError(JobState state) {
        return AppError.of(
                state == JobState.CANCELLED ? ErrorCode.cancelled : ErrorCode.internal,
                state == JobState.CANCELLED ? "Translation cancelled" : "Translation did not complete",
                state == JobState.CANCELLED
                        ? "The translation was cancelled before the book was written."
                        : "The translation ended without producing a completed book.");
    }

    private static <T> T dataOf(Result<T> result) {
        return Objects.requireNonNull(result.data(), "successful result data");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "failed result error");
    }
}
