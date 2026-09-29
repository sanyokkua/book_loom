package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.pipeline.export.DestinationChecks;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/** Validates a translation request and creates its single-run job without opening the source book. */
@Slf4j
public final class TranslationEngineImpl implements TranslationEngine {

    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");

    private final DocumentPort documents;
    private final ObjectMapper mapper;
    private final PromptTemplates templates;

    /** Creates an engine with the application-wide tolerant JSON mapper. */
    @Inject
    public TranslationEngineImpl(
            final DocumentPort documents, final ObjectMapper mapper, final PromptTemplates templates) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.templates = Objects.requireNonNull(templates, "templates");
    }

    @Override
    public Result<TranslationJob> newJob(final TranslationRequest request, final ChatModel model) {
        try {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(model, "model");
            logEntry(request);
            final Result<Boolean> languages = checkLanguages(request);
            if (languages.isErr()) {
                return failure(languages);
            }
            final Result<Boolean> destination = DestinationChecks.check(request.source(), request.destination());
            return destination.isErr()
                    ? failure(destination)
                    : Result.ok(new TranslationJobImpl(documents, request, model, mapper, templates));
        } catch (Throwable cause) {
            return Result.err(unexpectedBoundaryError(cause));
        }
    }

    private Result<Boolean> checkLanguages(final TranslationRequest request) {
        final Result<Boolean> target = checkLanguage("target-language", request.targetLanguage());
        if (target.isErr()) {
            return target;
        }
        final String sourceLanguage = request.sourceLanguage();
        if (sourceLanguage == null) {
            log.debug("Translation request check=source-language value=unspecified outcome=passed");
            return Result.ok(Boolean.TRUE);
        }
        return checkLanguage("source-language", sourceLanguage);
    }

    private Result<Boolean> checkLanguage(final String check, final String language) {
        final boolean valid = LANGUAGE_CODE.matcher(language).matches();
        log.debug("Translation request check={} value={} outcome={}", check, language, outcome(valid));
        return valid ? Result.ok(Boolean.TRUE) : rejected(check, invalidLanguageError());
    }

    private Result<Boolean> rejected(final String check, final AppError error) {
        log.warn("Refused translation request check={} code={}", check, error.code());
        return Result.err(error);
    }

    private AppError unexpectedBoundaryError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "This translation job could not be prepared",
                "An unexpected error prevented this translation job from being prepared.",
                null,
                cause);
        log.error("Unexpected translation engine boundary failure code={}", error.code(), cause);
        return error;
    }

    private static String outcome(final boolean passed) {
        return passed ? "passed" : "failed";
    }

    private static AppError invalidLanguageError() {
        return AppError.of(
                ErrorCode.validation,
                "This language code is not valid",
                "Use two or three letters followed only by optional hyphenated language subtags.");
    }

    private static void logEntry(final TranslationRequest request) {
        log.debug(
                "Preparing translation job source={} destination={} targetLanguage={} sourceLanguage={} overwrite={}",
                request.source(),
                request.destination(),
                request.targetLanguage(),
                request.sourceLanguage(),
                request.overwrite());
    }

    private static <T> Result<T> failure(final Result<?> result) {
        return Result.err(Objects.requireNonNull(result.error(), "result error"));
    }
}
