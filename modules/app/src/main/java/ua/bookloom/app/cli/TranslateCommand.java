package ua.bookloom.app.cli;

import com.google.inject.Inject;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Parses and executes the one-book translation command behind the public launcher. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
final class TranslateCommand {

    private static final String DEFAULT_TARGET_LANGUAGE = "uk";
    private static final String PSEUDO_PROVIDER = "pseudo";
    private static final String PSEUDO_MODEL = "uppercase";
    private static final String USAGE = "Usage: translate <book> [--to <lang>] [--from <lang>] [--overwrite]";
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");

    private final TranslationEngine engine;
    private final ChatModelFactory models;

    /** Runs one parsed command and reports only its user-facing result to the supplied stream. */
    int run(List<String> args, PrintStream out) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        log.info("translate command arguments={}", args);
        int exit;
        try {
            final Result<ParsedArguments> parsed = new ArgumentParser(args).parse();
            exit = parsed.isErr() ? usageFailure(parsed, out) : execute(parsed, out);
        } catch (Throwable cause) {
            exit = unexpectedFailure(out, cause);
        }
        log.info("translate command exitCode={}", exit);
        return exit;
    }

    private int execute(Result<ParsedArguments> parsed, PrintStream out) {
        final ParsedArguments arguments = dataOf(parsed);
        final Path destination = destination(arguments);
        log.debug(
                "translate command parsed source={} destination={} targetLanguage={} sourceLanguage={} overwrite={}",
                arguments.source(),
                destination,
                arguments.targetLanguage(),
                arguments.sourceLanguage(),
                arguments.overwrite());

        final Result<ChatModel> model = models.create(new ModelSelection(PSEUDO_PROVIDER, PSEUDO_MODEL));
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
            final AppError error = report.error() != null ? report.error() : stoppedError(report.end());
            return printError(error, out);
        }
        final Path written = Objects.requireNonNull(report.written(), "completed report must have a written path");
        out.println(
                "Completed: " + written + " (accepted=" + report.accepted() + ", flagged=" + report.flagged() + ")");
        return 0;
    }

    private int usageFailure(Result<ParsedArguments> parsed, PrintStream out) {
        final AppError error = errorOf(parsed);
        log.warn("Rejected translate command arguments reason={}", error.message());
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

    private static Path destination(ParsedArguments arguments) {
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

    private record ParsedArguments(
            Path source,
            BookFormat format,
            String targetLanguage,
            @Nullable String sourceLanguage,
            boolean overwrite) {}

    private static final class ArgumentParser {

        private final List<String> args;
        private @Nullable Path source;
        private String targetLanguage = DEFAULT_TARGET_LANGUAGE;
        private @Nullable String sourceLanguage;
        private boolean targetSpecified;
        private boolean sourceSpecified;
        private boolean overwrite;
        private boolean overwriteSpecified;

        private ArgumentParser(List<String> args) {
            this.args = List.copyOf(args);
        }

        private Result<ParsedArguments> parse() {
            log.debug("translate parser entry arguments={}", args);
            int index = 0;
            while (index < args.size()) {
                final Result<Integer> consumed = consume(index);
                if (consumed.isErr()) {
                    return Result.err(errorOf(consumed));
                }
                index = dataOf(consumed);
            }
            return finish();
        }

        private Result<Integer> consume(int index) {
            final String argument = args.get(index);
            if (!argument.startsWith("--")) {
                return consumeBook(index, argument);
            }
            return switch (argument) {
                case "--to" -> consumeLanguage(index, true);
                case "--from" -> consumeLanguage(index, false);
                case "--overwrite" -> consumeOverwrite(index);
                default -> invalidOption(argument, "unknown option");
            };
        }

        private Result<Integer> consumeBook(int index, String argument) {
            if (sourceSpecified) {
                return invalidOption("book", "more than one book path");
            }
            try {
                source = Path.of(argument);
            } catch (InvalidPathException exception) {
                return invalidOption("book", "book path is invalid");
            }
            sourceSpecified = true;
            log.debug("translate parser option=book path={} outcome=accepted", source);
            return Result.ok(index + 1);
        }

        private Result<Integer> consumeLanguage(int index, boolean target) {
            final String option = target ? "--to" : "--from";
            if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
                return invalidOption(option, target ? "--to needs a language" : "--from needs a language");
            }
            final String value = args.get(index + 1);
            if (target ? targetSpecified : sourceLanguage != null) {
                return invalidOption(option, target ? "--to was repeated" : "--from was repeated");
            }
            if (target) {
                targetLanguage = value;
                targetSpecified = true;
            } else {
                sourceLanguage = value;
            }
            log.debug("translate parser option={} value={} outcome=accepted", option, value);
            return Result.ok(index + 2);
        }

        private Result<Integer> consumeOverwrite(int index) {
            if (overwriteSpecified) {
                return invalidOption("--overwrite", "--overwrite was repeated");
            }
            overwrite = true;
            overwriteSpecified = true;
            log.debug("translate parser option=--overwrite outcome=accepted");
            return Result.ok(index + 1);
        }

        private Result<ParsedArguments> finish() {
            if (!sourceSpecified) {
                log.debug("translate parser check=source-present outcome=rejected");
                return invalid("book path is missing");
            }
            log.debug("translate parser check=source-present outcome=passed");
            final Path selectedSource = Objects.requireNonNull(source, "source");
            final Result<BookFormat> format = validateSource(selectedSource);
            if (format.isErr()) {
                return Result.err(errorOf(format));
            }
            final Result<Boolean> languages = validateLanguages();
            if (languages.isErr()) {
                return Result.err(errorOf(languages));
            }
            final BookFormat selectedFormat = dataOf(format);
            log.debug(
                    "translate parser validation outcome=accepted source={} format={}", selectedSource, selectedFormat);
            return Result.ok(
                    new ParsedArguments(selectedSource, selectedFormat, targetLanguage, sourceLanguage, overwrite));
        }

        private Result<BookFormat> validateSource(Path selectedSource) {
            final Path fileName = selectedSource.getFileName();
            final boolean regularFile = fileName != null && Files.isRegularFile(selectedSource);
            log.debug("translate parser check=regular-file path={} outcome={}", selectedSource, outcome(regularFile));
            if (!regularFile) {
                return invalid("book path is not a regular file");
            }
            final String name = Objects.requireNonNull(fileName).toString();
            final @Nullable BookFormat selectedFormat =
                    BookFormat.ofFileName(name).orElse(null);
            log.debug(
                    "translate parser check=format path={} format={} outcome={}",
                    selectedSource,
                    selectedFormat,
                    outcome(selectedFormat != null));
            if (selectedFormat == null) {
                return invalid("book type is not supported");
            }
            return Result.ok(selectedFormat);
        }

        private Result<Boolean> validateLanguages() {
            final boolean validTarget = LANGUAGE_CODE.matcher(targetLanguage).matches();
            log.debug(
                    "translate parser check=target-language value={} outcome={}", targetLanguage, outcome(validTarget));
            if (!validTarget) {
                return invalid("target language is invalid");
            }
            final boolean validSource = sourceLanguage == null
                    || LANGUAGE_CODE.matcher(sourceLanguage).matches();
            log.debug(
                    "translate parser check=source-language value={} outcome={}",
                    sourceLanguage == null ? "unspecified" : sourceLanguage,
                    outcome(validSource));
            return validSource ? Result.ok(Boolean.TRUE) : invalid("source language is invalid");
        }

        private <T> Result<T> invalid(String reason) {
            log.debug("translate parser branch=validation outcome=rejected reason={}", reason);
            return Result.err(AppError.of(ErrorCode.validation, "Invalid command arguments", reason));
        }

        private <T> Result<T> invalidOption(String option, String reason) {
            log.debug("translate parser option={} outcome=rejected reason={}", option, reason);
            return invalid(reason);
        }

        private static String outcome(boolean passed) {
            return passed ? "passed" : "rejected";
        }
    }
}
