package ua.bookloom.app.cli;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;

/** Parsed, validated arguments for one translate command invocation. */
@Slf4j
record TranslateArguments(
        Path source,
        BookFormat format,
        String targetLanguage,
        @Nullable String sourceLanguage,
        boolean overwrite,
        String providerId,
        @Nullable String modelId,
        @Nullable URI baseUrl,
        @Nullable Duration requestTimeout) {

    private static final String DEFAULT_TARGET_LANGUAGE = "uk";
    private static final String PSEUDO_PROVIDER = "pseudo";
    private static final String OLLAMA_PROVIDER = "ollama";
    private static final String LM_STUDIO_PROVIDER = "lmstudio";
    private static final String OPENAI_COMPATIBLE_PROVIDER = "openai-compatible";
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");

    /** Parses one complete argument list or returns a user-facing validation error. */
    static Result<TranslateArguments> parse(List<String> args) {
        Objects.requireNonNull(args, "args");
        return new Parser(args).parse();
    }

    private static final class Parser {

        private final List<String> args;
        private @Nullable Path source;
        private String targetLanguage = DEFAULT_TARGET_LANGUAGE;
        private @Nullable String sourceLanguage;
        private boolean targetSpecified;
        private boolean sourceSpecified;
        private boolean overwrite;
        private boolean overwriteSpecified;
        private String providerId = PSEUDO_PROVIDER;
        private @Nullable String modelId;
        private @Nullable URI baseUrl;
        private @Nullable Duration requestTimeout;
        private boolean providerSpecified;
        private boolean modelSpecified;
        private boolean baseUrlSpecified;
        private boolean timeoutSpecified;

        private Parser(List<String> args) {
            this.args = List.copyOf(args);
        }

        private Result<TranslateArguments> parse() {
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
                case "--provider" -> consumeProvider(index);
                case "--model" -> consumeModel(index);
                case "--base-url" -> consumeBaseUrl(index);
                case "--timeout" -> consumeTimeout(index);
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
            final Result<String> value =
                    requiredValue(index, option, target ? "--to needs a language" : "--from needs a language");
            if (value.isErr()) {
                return Result.err(errorOf(value));
            }
            if (target ? targetSpecified : sourceLanguage != null) {
                return invalidOption(option, target ? "--to was repeated" : "--from was repeated");
            }
            if (target) {
                targetLanguage = dataOf(value);
                targetSpecified = true;
            } else {
                sourceLanguage = dataOf(value);
            }
            log.debug("translate parser option={} value={} outcome=accepted", option, dataOf(value));
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

        private Result<Integer> consumeProvider(int index) {
            final Result<String> value = requiredValue(index, "--provider", "--provider needs a provider");
            if (value.isErr()) {
                return Result.err(errorOf(value));
            }
            if (providerSpecified) {
                return invalidOption("--provider", "--provider was repeated");
            }
            providerId = dataOf(value);
            providerSpecified = true;
            log.debug("translate parser option=--provider value={} outcome=accepted", providerId);
            return Result.ok(index + 2);
        }

        private Result<Integer> consumeModel(int index) {
            final Result<String> value = requiredValue(index, "--model", "--model needs a model id");
            if (value.isErr()) {
                return Result.err(errorOf(value));
            }
            if (modelSpecified) {
                return invalidOption("--model", "--model was repeated");
            }
            modelId = dataOf(value);
            modelSpecified = true;
            log.debug("translate parser option=--model value={} outcome=accepted", modelId);
            return Result.ok(index + 2);
        }

        private Result<Integer> consumeBaseUrl(int index) {
            final Result<String> value = requiredValue(index, "--base-url", "--base-url needs a URL");
            if (value.isErr()) {
                return Result.err(errorOf(value));
            }
            if (baseUrlSpecified) {
                return invalidOption("--base-url", "--base-url was repeated");
            }
            try {
                baseUrl = URI.create(dataOf(value));
            } catch (IllegalArgumentException exception) {
                return invalidOption("--base-url", "--base-url is invalid");
            }
            baseUrlSpecified = true;
            log.debug("translate parser option=--base-url value={} outcome=accepted", baseUrl);
            return Result.ok(index + 2);
        }

        private Result<Integer> consumeTimeout(int index) {
            final Result<String> value = requiredValue(index, "--timeout", "--timeout needs seconds");
            if (value.isErr()) {
                return Result.err(errorOf(value));
            }
            if (timeoutSpecified) {
                return invalidOption("--timeout", "--timeout was repeated");
            }
            final Result<Duration> parsed = parseTimeout(dataOf(value));
            if (parsed.isErr()) {
                return Result.err(errorOf(parsed));
            }
            requestTimeout = dataOf(parsed);
            timeoutSpecified = true;
            log.debug("translate parser option=--timeout value={} outcome=accepted", requestTimeout);
            return Result.ok(index + 2);
        }

        private Result<TranslateArguments> finish() {
            if (!sourceSpecified) {
                log.debug("translate parser check=source-present outcome=rejected");
                return invalid("book path is missing");
            }
            log.debug("translate parser check=source-present outcome=passed");
            final Path selectedSource = Objects.requireNonNull(source, "source");
            return validateAndCreate(selectedSource);
        }

        private Result<TranslateArguments> validateAndCreate(Path selectedSource) {
            final Result<BookFormat> format = validateSource(selectedSource);
            if (format.isErr()) {
                return Result.err(errorOf(format));
            }
            final Result<Boolean> languages = validateLanguages();
            if (languages.isErr()) {
                return Result.err(errorOf(languages));
            }
            final Result<Boolean> provider = validateProvider();
            if (provider.isErr()) {
                return Result.err(errorOf(provider));
            }
            log.debug(
                    "translate parser validation outcome=accepted source={} format={}", selectedSource, dataOf(format));
            return Result.ok(new TranslateArguments(
                    selectedSource,
                    dataOf(format),
                    targetLanguage,
                    sourceLanguage,
                    overwrite,
                    providerId,
                    modelId,
                    baseUrl,
                    requestTimeout));
        }

        private Result<BookFormat> validateSource(Path selectedSource) {
            final Path fileName = selectedSource.getFileName();
            final boolean regularFile = fileName != null && Files.isRegularFile(selectedSource);
            log.debug("translate parser check=regular-file path={} outcome={}", selectedSource, outcome(regularFile));
            if (!regularFile) {
                return invalid("book path is not a regular file");
            }
            final @Nullable BookFormat format = BookFormat.ofFileName(
                            Objects.requireNonNull(fileName).toString())
                    .orElse(null);
            log.debug(
                    "translate parser check=format path={} format={} outcome={}",
                    selectedSource,
                    format,
                    outcome(format != null));
            return format == null ? invalid("book type is not supported") : Result.ok(format);
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
                    "translate parser check=source-language value={} outcome={}", sourceLanguage, outcome(validSource));
            return validSource ? Result.ok(Boolean.TRUE) : invalid("source language is invalid");
        }

        private Result<Boolean> validateProvider() {
            if (!isKnownProvider()) {
                return invalid("--provider must be pseudo, ollama, lmstudio or openai-compatible");
            }
            if (!PSEUDO_PROVIDER.equals(providerId) && (modelId == null || modelId.isBlank())) {
                return invalid("--model is required for provider " + providerId);
            }
            if (OPENAI_COMPATIBLE_PROVIDER.equals(providerId) && baseUrl == null) {
                return invalid("--base-url is required for provider openai-compatible");
            }
            log.debug("translate parser check=provider provider={} outcome=passed", providerId);
            return Result.ok(Boolean.TRUE);
        }

        private boolean isKnownProvider() {
            return PSEUDO_PROVIDER.equals(providerId)
                    || OLLAMA_PROVIDER.equals(providerId)
                    || LM_STUDIO_PROVIDER.equals(providerId)
                    || OPENAI_COMPATIBLE_PROVIDER.equals(providerId);
        }

        private Result<String> requiredValue(int index, String option, String missingReason) {
            if (index + 1 >= args.size() || args.get(index + 1).startsWith("--")) {
                return invalidOption(option, missingReason);
            }
            return Result.ok(args.get(index + 1));
        }

        private Result<Duration> parseTimeout(String value) {
            try {
                final long seconds = Long.parseLong(value);
                return seconds > 0
                        ? Result.ok(Duration.ofSeconds(seconds))
                        : invalid("--timeout must be a positive whole number");
            } catch (NumberFormatException exception) {
                return invalid("--timeout must be a positive whole number");
            }
        }

        private <T> Result<T> invalid(String reason) {
            log.debug("translate parser branch=validation outcome=rejected reason={}", reason);
            return Result.err(AppError.of(ErrorCode.validation, "Invalid command arguments", reason));
        }

        private <T> Result<T> invalidOption(String option, String reason) {
            log.debug("translate parser option={} outcome=rejected reason={}", option, reason);
            return invalid(reason);
        }

        private static <T> T dataOf(Result<T> result) {
            return Objects.requireNonNull(result.data(), "successful result data");
        }

        private static AppError errorOf(Result<?> result) {
            return Objects.requireNonNull(result.error(), "failed result error");
        }

        private static String outcome(boolean passed) {
            return passed ? "passed" : "rejected";
        }
    }
}
