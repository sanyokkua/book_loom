package ua.bookloom.pipeline.export;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;

/**
 * Refuses a destination the source book cannot be written to before any file is touched: an unsupported or different
 * file type, a different FB2 container, or a path that is the source itself under any alias.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class DestinationChecks {

    private static final String ZIPPED_FB2_SUFFIX = ".fb2.zip";

    /**
     * Runs the format, container and path-identity checks in that order and stops at the first refusal.
     *
     * @param source the non-null source book path
     * @param destination the non-null path the translated book would be written to
     * @return {@code true} when the destination may be written; {@code validation} for a refused destination, and
     *     {@code internal} when the two paths could not be compared safely
     */
    public static Result<Boolean> check(final Path source, final Path destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        log.debug("Checking destination {} against source {}", destination, source);
        final Result<Boolean> formats = checkFormats(source, destination);
        return formats.isErr() ? formats : checkIdentity(source, destination);
    }

    private static Result<Boolean> checkFormats(final Path source, final Path destination) {
        final Optional<BookFormat> sourceFormat = formatOf(source);
        final Optional<BookFormat> destinationFormat = formatOf(destination);
        final boolean supported = sourceFormat.isPresent() && destinationFormat.isPresent();
        log.debug(
                "Translation request check=supported-formats sourceFormat={} destinationFormat={} outcome={}",
                sourceFormat.orElse(null),
                destinationFormat.orElse(null),
                outcome(supported));
        if (!supported) {
            return rejected("supported-formats", unsupportedFormatError());
        }
        final boolean same = sourceFormat.equals(destinationFormat);
        log.debug("Translation request check=same-format outcome={}", outcome(same));
        return same ? checkContainers(source, destination) : rejected("same-format", mismatchedFormatError());
    }

    private static Result<Boolean> checkContainers(final Path source, final Path destination) {
        final boolean sourceZipped = isZippedFb2(source);
        final boolean destinationZipped = isZippedFb2(destination);
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

    private static Result<Boolean> checkIdentity(final Path sourcePath, final Path destinationPath) {
        try {
            final Path source = sourcePath.toAbsolutePath();
            final Path destination = destinationPath.toAbsolutePath();
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

    private static Result<Boolean> checkExistingIdentity(final Path source, final Path destination) {
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

    private static Result<Boolean> probeSource(final Path source) {
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

    private static Result<Boolean> probeDestination(final Path destination) {
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

    private static Result<Boolean> compareExistingIdentity(final Path source, final Path destination) {
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

    private static Result<Boolean> rejected(final String check, final AppError error) {
        log.warn("Refused translation request check={} code={}", check, error.code());
        return Result.err(error);
    }

    private static AppError identityError(final String check, final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "This translation job could not be prepared",
                "The source and destination files could not be compared safely.",
                null,
                cause);
        log.error("Unexpected translation request filesystem failure check={} code={}", check, error.code(), cause);
        return error;
    }

    private static Optional<BookFormat> formatOf(final Path path) {
        final Path fileName = path.getFileName();
        return fileName == null ? Optional.empty() : BookFormat.ofFileName(fileName.toString());
    }

    private static String outcome(final boolean passed) {
        return passed ? "passed" : "failed";
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
}
