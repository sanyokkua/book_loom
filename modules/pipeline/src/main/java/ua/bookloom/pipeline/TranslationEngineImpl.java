package ua.bookloom.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.pipeline.TranslationEngine;
import ua.bookloom.api.pipeline.TranslationJob;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Validates a translation request and creates its single-run job without opening the source book. */
@Slf4j
public final class TranslationEngineImpl implements TranslationEngine {

    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");
    private static final String ZIPPED_FB2_SUFFIX = ".fb2.zip";

    private final DocumentPort documents;
    private final ObjectMapper mapper;

    /** Creates an engine with the application-wide tolerant JSON mapper. */
    @Inject
    public TranslationEngineImpl(final DocumentPort documents, final ObjectMapper mapper) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
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
            final Result<Boolean> formats = checkFormats(request);
            if (formats.isErr()) {
                return failure(formats);
            }
            final Result<Boolean> identity = checkIdentity(request);
            return identity.isErr()
                    ? failure(identity)
                    : Result.ok(new TranslationJobImpl(documents, request, model, mapper));
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

    private Result<Boolean> checkFormats(final TranslationRequest request) {
        final Optional<BookFormat> source = formatOf(request.source());
        final Optional<BookFormat> destination = formatOf(request.destination());
        final boolean supported = source.isPresent() && destination.isPresent();
        log.debug(
                "Translation request check=supported-formats sourceFormat={} destinationFormat={} outcome={}",
                source.orElse(null),
                destination.orElse(null),
                outcome(supported));
        if (!supported) {
            return rejected("supported-formats", unsupportedFormatError());
        }
        final boolean same = source.equals(destination);
        log.debug("Translation request check=same-format outcome={}", outcome(same));
        return same ? checkContainers(request) : rejected("same-format", mismatchedFormatError());
    }

    private Result<Boolean> checkContainers(final TranslationRequest request) {
        final boolean sourceZipped = isZippedFb2(request.source());
        final boolean destinationZipped = isZippedFb2(request.destination());
        final boolean same = sourceZipped == destinationZipped;
        log.debug(
                "Translation request check=same-container sourceZippedFb2={} destinationZippedFb2={} outcome={}",
                sourceZipped,
                destinationZipped,
                outcome(same));
        return same ? Result.ok(Boolean.TRUE) : rejected("same-container", mismatchedFormatError());
    }

    /** FB2 export re-emits the source's container, so a zipped and a plain FB2 name are different file types. */
    private static boolean isZippedFb2(final Path path) {
        final String name =
                Objects.requireNonNull(path.getFileName(), "file name").toString();
        final int start = name.length() - ZIPPED_FB2_SUFFIX.length();
        return start >= 0 && name.regionMatches(true, start, ZIPPED_FB2_SUFFIX, 0, ZIPPED_FB2_SUFFIX.length());
    }

    private Result<Boolean> checkIdentity(final TranslationRequest request) {
        try {
            final Path source = request.source().toAbsolutePath();
            final Path destination = request.destination().toAbsolutePath();
            final Path normalizedSource = source.normalize();
            final Path normalizedDestination = destination.normalize();
            final boolean sameNormalizedPath = normalizedSource.equals(normalizedDestination);
            log.debug(
                    "Translation request check=normalized-path source={} destination={} outcome={}",
                    normalizedSource,
                    normalizedDestination,
                    outcome(!sameNormalizedPath));
            if (sameNormalizedPath) {
                return rejected("normalized-path", sameFileError());
            }
            return checkExistingIdentity(source, destination);
        } catch (Throwable cause) {
            return Result.err(identityError("path-identity", cause));
        }
    }

    private Result<Boolean> checkExistingIdentity(final Path source, final Path destination) {
        final Result<Boolean> sourceProbe = probeSource(source);
        if (sourceProbe.isErr() || !Objects.requireNonNull(sourceProbe.data(), "source probe")) {
            return sourceProbe;
        }
        final Result<Boolean> destinationProbe = probeDestination(destination);
        if (destinationProbe.isErr() || !Objects.requireNonNull(destinationProbe.data(), "destination probe")) {
            return destinationProbe;
        }
        return compareExistingIdentity(source, destination);
    }

    private Result<Boolean> probeSource(final Path source) {
        try {
            Files.readAttributes(source, BasicFileAttributes.class);
            log.debug("Translation request check=source-identity-probe outcome=present");
            return Result.ok(Boolean.TRUE);
        } catch (NoSuchFileException cause) {
            log.debug("Translation request check=source-identity-probe outcome=deferred reason=source-missing");
            return Result.ok(Boolean.FALSE);
        } catch (AccessDeniedException cause) {
            log.debug("Translation request check=source-identity-probe outcome=deferred reason=source-unreadable");
            return Result.ok(Boolean.FALSE);
        } catch (IOException cause) {
            return Result.err(identityError("source-identity", cause));
        }
    }

    private Result<Boolean> probeDestination(final Path destination) {
        try {
            Files.readAttributes(destination, BasicFileAttributes.class);
            log.debug("Translation request check=destination-identity-probe outcome=present");
            return Result.ok(Boolean.TRUE);
        } catch (NoSuchFileException cause) {
            log.debug(
                    "Translation request check=destination-identity-probe outcome=deferred reason=destination-missing");
            return Result.ok(Boolean.FALSE);
        } catch (IOException cause) {
            return Result.err(identityError("destination-identity", cause));
        }
    }

    private Result<Boolean> compareExistingIdentity(final Path source, final Path destination) {
        try {
            final boolean sameFile = Files.isSameFile(source, destination);
            log.debug("Translation request check=same-file outcome={}", outcome(!sameFile));
            return sameFile ? rejected("same-file", sameFileError()) : Result.ok(Boolean.TRUE);
        } catch (NoSuchFileException cause) {
            log.debug("Translation request check=same-file outcome=deferred reason=entry-disappeared");
            return Result.ok(Boolean.FALSE);
        } catch (IOException cause) {
            return Result.err(identityError("same-file", cause));
        }
    }

    private Result<Boolean> rejected(final String check, final AppError error) {
        log.warn("Refused translation request check={} code={}", check, error.code());
        return Result.err(error);
    }

    private AppError identityError(final String check, final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "This translation job could not be prepared",
                "The source and destination files could not be compared safely.",
                null,
                cause);
        log.error("Unexpected translation request filesystem failure check={} code={}", check, error.code(), cause);
        return error;
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

    private static Optional<BookFormat> formatOf(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? Optional.empty() : BookFormat.ofFileName(fileName.toString());
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

    private static AppError unsupportedFormatError() {
        return AppError.of(
                ErrorCode.validation,
                "This book format is not supported",
                "Choose an EPUB, FB2, Markdown or TXT source and destination.");
    }

    private static AppError mismatchedFormatError() {
        return AppError.of(
                ErrorCode.validation,
                "The source and destination formats differ",
                "Choose a destination with the same file type as the source book.");
    }

    private static AppError sameFileError() {
        return AppError.of(
                ErrorCode.validation,
                "The source and destination are the same file",
                "Choose a different destination so the source book remains unchanged.");
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
