package ua.bookloom.app.cli;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ExportJob;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.ExportService;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.LexiconService;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.RenderingConsistency;
import ua.bookloom.util.paths.DestinationPath;

/**
 * Parses and executes the one-book translation command behind the public launcher — the headless proof tool: it checks
 * the provider, opens the book with the brief the flags ask for, optionally reviews its names, runs it unattended
 * (recovering from outages like a window run) and exports. A run that did not complete — failed, stopped, or
 * interrupted with Ctrl+C — still exports what it translated unless {@code --no-partial} is given.
 *
 * <p>Exit codes: 0 the book completed and was written; 3 the run did not complete and what it translated was written;
 * 1 nothing was written; 2 invalid arguments.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@com.google.inject.Inject})
public final class TranslateCommand {

    /** The exit code of a run that did not complete but wrote what it translated. */
    static final int PARTIAL_EXIT = 3;

    /** How much longer a Ctrl+C waits for the export of what the stopped run translated. */
    private static final Duration PARTIAL_EXPORT_WAIT = Duration.ofSeconds(60);

    private static final String USAGE = "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite] "
            + "[--provider pseudo|ollama|lmstudio|openai-compatible] [--model <id>] [--base-url <url>] "
            + "[--timeout <seconds>] [--quality fast|balanced|max] [--names translate|transliterate|keep] "
            + "[--review-names] [--max-outage <duration>] [--report <file>] [--stop-after <segments>] [--no-partial]";

    private final TranslationEngine engine;
    private final ChatModelFactory models;
    private final ProviderSetup providers;
    private final BookOpener opener;
    private final NameReview names;
    private final ExportService exports;
    private final ShutdownCancellation shutdown;
    private final Clock clock;
    private final ModelCapabilities capabilities;
    private final LexiconService lexicon;

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
        } finally {
            shutdown.finished();
        }
        log.info("translate command exitCode={}", exit);
        return exit;
    }

    private int execute(TranslateArguments arguments, PrintStream out) {
        final Path destination =
                DestinationPath.destinationFor(arguments.source(), arguments.format(), arguments.targetLanguage());
        log.debug(
                "translate command parsed source={} destination={} targetLanguage={} sourceLanguage={} overwrite={}"
                        + " options={}",
                arguments.source(),
                destination,
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.overwrite(),
                arguments.options());
        if (destinationOccupied(destination, arguments.overwrite())) {
            return printError(destinationExists(), out);
        }
        final RunReport report = new RunReport(arguments, destination, clock.instant());
        final int exit = selectAndRun(arguments, destination, report, out);
        final @Nullable Path reportFile = arguments.options().report();
        if (reportFile != null
                && report.write(reportFile, clock.instant(), exit).isErr()) {
            out.println("The run report could not be written to " + reportFile);
        }
        return exit;
    }

    private static boolean destinationOccupied(Path destination, boolean overwrite) {
        final boolean occupied = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        log.debug("translate command check=destination-free occupied={} overwrite={}", occupied, overwrite);
        return occupied && !overwrite;
    }

    private int selectAndRun(TranslateArguments arguments, Path destination, RunReport report, PrintStream out) {
        final Result<ModelSelection> selection = providers.select(arguments);
        if (selection.isErr()) {
            final AppError error = errorOf(selection);
            return error.code() == ErrorCode.validation ? usageFailure(error, out) : printError(error, out);
        }
        final ModelSelection selected = dataOf(selection);
        report.selection(selected);
        log.info(
                "translate command provider={} model={} targetLanguage={} sourceLanguage={} baseUrl={}"
                        + " requestTimeout={}",
                selected.providerId(),
                selected.modelId(),
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.baseUrl(),
                arguments.requestTimeout());
        final boolean pseudo = ProviderSetup.PSEUDO_PROVIDER.equals(selected.providerId());
        if (!pseudo && !timed(report, "preflight", () -> providers.preflight(selected, out))) {
            return 1;
        }
        return translate(arguments, destination, selected, report, out);
    }

    private int translate(
            TranslateArguments arguments,
            Path destination,
            ModelSelection selection,
            RunReport report,
            PrintStream out) {
        final Result<ChatModel> model = models.create(selection);
        if (model.isErr()) {
            return printError(errorOf(model), out);
        }
        final Result<BookOpener.Opened> opened = opener.open(arguments);
        if (opened.isErr()) {
            return printError(errorOf(opened), out);
        }
        final String projectId = dataOf(opened).projectId();
        report.brief(dataOf(opened).brief());
        if (arguments.options().reviewNames()) {
            report.names(timed(report, "names", () -> names.review(projectId, dataOf(model), out)));
        }
        final Integer detected = capabilities.detectedTokens(selection).orElse(null);
        log.debug("translate command detectedContext={}", detected);
        final Result<TranslationJob> job = engine.newJob(
                new RunRequest(projectId, ReviewMode.UNATTENDED, detected, selection.modelId()), dataOf(model));
        if (job.isErr()) {
            return printError(errorOf(job), out);
        }
        return runAndExport(dataOf(job), selection, new Target(projectId, destination, arguments), report, out);
    }

    /** Where a run's book goes and how. */
    private record Target(String projectId, Path destination, TranslateArguments arguments) {}

    private int runAndExport(
            TranslationJob job, ModelSelection selection, Target target, RunReport report, PrintStream out) {
        final RunOptions options = target.arguments().options();
        final UnattendedRun watch =
                UnattendedRun.attach(job, providers.probeFor(selection), options.maxOutage(), options.stopAfter(), out);
        shutdown.hold(job::cancel);
        final Result<JobReport> result = timed(report, "translate", job::run);
        report.run(result, watch);
        report.lexicon(RenderingConsistency.of(lexiconOf(target.projectId())));
        if (result.isErr()) {
            return printError(errorOf(result), out);
        }
        final JobReport run = dataOf(result);
        log.info(
                "translate command run ended state={} accepted={} flagged={} stoppedBecause={}",
                run.end(),
                run.accepted(),
                run.flagged(),
                watch.stopReason());
        final boolean completed = run.end() == JobState.COMPLETED;
        if (!completed && !options.partialExport()) {
            return printError(endingError(run, watch), out);
        }
        return exportAndReport(run, completed, target, report, out, watch);
    }

    // Silent when the audit is quiet, so a clean run's output stays the one line it was.
    private static void printAudit(ExportReport written, PrintStream out) {
        final int doubted = written.suspicious().size();
        log.info("translate command audit suspicious={}", doubted);
        if (doubted > 0) {
            out.println("Audit: " + doubted + " accepted segment(s) look suspicious — "
                    + written.suspicious().stream()
                            .map(segment -> segment.locator() + " (" + String.join(", ", segment.checks()) + ")")
                            .collect(Collectors.joining("; ")));
        }
    }

    private List<LexiconEntry> lexiconOf(String projectId) {
        final List<LexiconEntry> held = lexicon.entries(projectId).data();
        return held == null ? List.of() : held;
    }

    private int exportAndReport(
            JobReport run, boolean completed, Target target, RunReport report, PrintStream out, UnattendedRun watch) {
        final Result<ExportReport> exported =
                timed(report, "export", () -> export(target.projectId(), target.destination(), target.arguments()));
        if (exported.isErr()) {
            return printError(errorOf(exported), out);
        }
        final ExportReport written = dataOf(exported);
        report.export(written, !completed);
        report.audit(written.suspicious());
        printAudit(written, out);
        if (completed) {
            out.println("Completed: " + written.destination() + " (accepted=" + run.accepted() + ", flagged="
                    + run.flagged() + ")");
            return 0;
        }
        final AppError ending = endingError(run, watch);
        out.println("Partial: " + written.destination() + " (accepted=" + run.accepted() + ", flagged="
                + run.flagged() + ", pending=" + written.pending() + ") — the run ended " + run.end() + ": "
                + ending.title());
        return PARTIAL_EXIT;
    }

    // After Ctrl+C the export of what the stopped run translated is not cancelled by the same stop; the stop waits
    // for it a while longer instead.
    private Result<ExportReport> export(String projectId, Path destination, TranslateArguments arguments) {
        final Result<ExportJob> job = exports.newExport(
                new ExportRequest(projectId, destination, arguments.overwrite(), Set.of(), false), null);
        if (job.isErr()) {
            return Result.err(errorOf(job));
        }
        final ExportJob exportJob = dataOf(job);
        if (shutdown.isRequested()) {
            shutdown.extendWaitBy(PARTIAL_EXPORT_WAIT);
        } else {
            shutdown.hold(exportJob::cancel);
        }
        return exportJob.run();
    }

    private <T> T timed(RunReport report, String phase, Supplier<T> work) {
        final Instant started = clock.instant();
        try {
            return work.get();
        } finally {
            report.phase(phase, Duration.between(started, clock.instant()));
        }
    }

    private static AppError endingError(JobReport run, UnattendedRun watch) {
        final @Nullable AppError error = run.error();
        if (error != null) {
            return error;
        }
        final @Nullable String stopped = watch.stopReason();
        if (stopped != null) {
            return AppError.of(ErrorCode.cancelled, "Stopped: " + stopped, "The run was stopped: " + stopped + ".");
        }
        return run.end() == JobState.CANCELLED
                ? AppError.of(
                        ErrorCode.cancelled,
                        "Translation cancelled",
                        "The translation was cancelled before the book was written.")
                : AppError.of(
                        ErrorCode.internal,
                        "Translation did not complete",
                        "The translation ended without producing a completed book.");
    }

    private static AppError destinationExists() {
        return AppError.of(
                ErrorCode.validation,
                "This destination already exists",
                "Choose a new destination or allow the existing file to be replaced.");
    }

    private int usageFailure(AppError error, PrintStream out) {
        log.warn("Rejected translate command arguments reason={}", error.message());
        out.println(error.title() + ": " + error.message());
        out.println(USAGE);
        return 2;
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

    private static <T> T dataOf(Result<T> result) {
        return Objects.requireNonNull(result.data(), "successful result data");
    }

    private static AppError errorOf(Result<?> result) {
        return Objects.requireNonNull(result.error(), "failed result error");
    }
}
