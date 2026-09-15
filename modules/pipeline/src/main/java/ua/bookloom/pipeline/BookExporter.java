package ua.bookloom.pipeline;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.TranslationRequest;

@Slf4j
final class BookExporter {

    private final DocumentPort documents;
    private final ExportMoveOperation moves;

    BookExporter(final DocumentPort documents) {
        this(documents, ExportMoveOperation.nio());
    }

    BookExporter(final DocumentPort documents, final ExportMoveOperation moves) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.moves = Objects.requireNonNull(moves, "moves");
    }

    Result<Path> export(
            final TranslationRequest request,
            final Document decidedDocument,
            final BooleanSupplier cancellationRequested) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(decidedDocument, "decidedDocument");
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        log.debug(
                "Export requested from {} to {} in {} with overwrite {}",
                request.source(),
                request.destination(),
                request.targetLanguage(),
                request.overwrite());
        try {
            return beginExport(request, decidedDocument, cancellationRequested);
        } catch (Throwable cause) {
            return Result.err(unexpectedError("start export", cause));
        }
    }

    private Result<Path> beginExport(
            final TranslationRequest request,
            final Document decidedDocument,
            final BooleanSupplier cancellationRequested) {
        final Path temporary = temporaryPath(request.destination());
        log.debug("Export temporary path is {}", temporary);
        final Result<Boolean> collision = hasTemporaryCollision(request, temporary);
        if (collision.isErr()) {
            return Result.err(errorOf(collision));
        }
        if (Boolean.TRUE.equals(collision.data())) {
            log.warn("Refused export because temporary path {} aliases source or destination", temporary);
            return Result.err(collisionError());
        }
        return new Attempt(request, decidedDocument, cancellationRequested, temporary).run();
    }

    private Result<Boolean> hasTemporaryCollision(final TranslationRequest request, final Path temporary) {
        try {
            final boolean collision = Files.isSymbolicLink(temporary)
                    || ExportPathAliases.aliases(temporary, request.source())
                    || ExportPathAliases.aliases(temporary, request.destination());
            log.debug("Export temporary collision check for {} returned {}", temporary, collision);
            return Result.ok(collision);
        } catch (Throwable cause) {
            return Result.err(unexpectedError("check the export temporary path", cause));
        }
    }

    private static Path temporaryPath(final Path destination) {
        final Path fileName = Objects.requireNonNull(destination.getFileName(), "destination file name");
        return destination.resolveSibling("." + fileName);
    }

    private static AppError collisionError() {
        return AppError.of(
                ErrorCode.validation,
                "This export path is unsafe",
                "The temporary export path refers to the source or destination, so the book was left untouched.");
    }

    private static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    private final class Attempt {

        private final TranslationRequest request;
        private final Document decidedDocument;
        private final BooleanSupplier cancellationRequested;
        private final Path temporary;
        private final List<Document> opened = new ArrayList<>();

        Attempt(
                final TranslationRequest request,
                final Document decidedDocument,
                final BooleanSupplier cancellationRequested,
                final Path temporary) {
            this.request = request;
            this.decidedDocument = decidedDocument;
            this.cancellationRequested = cancellationRequested;
            this.temporary = temporary;
        }

        Result<Path> run() {
            try {
                return reopenAndWrite();
            } catch (Throwable cause) {
                return fail(unexpectedError("export the translated book", cause));
            }
        }

        private Result<Path> reopenAndWrite() {
            log.debug("Opening export source {}", request.source());
            final Result<Document> reopened = documents.open(request.source());
            if (reopened.isErr()) {
                log.debug(
                        "Opening export source failed with {}",
                        errorOf(reopened).code());
                return fail(errorOf(reopened));
            }
            final Document fresh = Objects.requireNonNull(reopened.data(), "reopened source");
            opened.add(fresh);
            log.debug("Opened export source {} as document {}", request.source(), fresh.id());
            return validateSourceAndWrite(fresh);
        }

        private Result<Path> validateSourceAndWrite(final Document fresh) {
            final boolean matches = fresh.contentHash().equals(decidedDocument.contentHash());
            log.debug("Export source hash match for document {} is {}", fresh.id(), matches);
            if (!matches) {
                log.warn("Export source {} changed after translation began", request.source());
                return fail(sourceChangedError());
            }
            final Document applied = applyDecisions(fresh, decidedDocument);
            logDecisionCounts(applied);
            log.debug("Writing translated document {} to {}", fresh.id(), temporary);
            final Result<Path> written = documents.write(applied, temporary, request.targetLanguage());
            log.debug("Temporary export write result is {}", outcomeOf(written));
            return written.isErr() ? fail(errorOf(written)) : reopenTemporary();
        }

        private Result<Path> reopenTemporary() {
            log.debug("Opening written temporary book {}", temporary);
            final Result<Document> reopened = documents.open(temporary);
            if (reopened.isErr()) {
                log.debug(
                        "Opening written temporary book failed with {}",
                        errorOf(reopened).code());
                return fail(errorOf(reopened));
            }
            final Document verified = Objects.requireNonNull(reopened.data(), "reopened temporary book");
            opened.add(verified);
            return validateCountAndPublish(verified);
        }

        private Result<Path> validateCountAndPublish(final Document verified) {
            final int expected = segmentCount(decidedDocument);
            final int observed = segmentCount(verified);
            log.debug("Temporary export segment counts are expected {} and observed {}", expected, observed);
            if (expected != observed) {
                log.warn("Refused temporary export with {} segments; source snapshot has {}", observed, expected);
                return fail(countMismatchError());
            }
            final Result<Boolean> closed = closeAll();
            if (closed.isErr()) {
                return failAfterClose(errorOf(closed));
            }
            if (cancellationRequested.getAsBoolean()) {
                log.debug("Export cancelled after validation and closure, before publication");
                return failAfterClose(cancelledError());
            }
            return publish();
        }

        private Result<Path> publish() {
            log.debug("Publishing {} to {} with overwrite {}", temporary, request.destination(), request.overwrite());
            try {
                if (request.overwrite()) {
                    moveWithOverwrite();
                } else {
                    moves.move(temporary, request.destination());
                }
                log.debug("Published validated export at {}", request.destination());
                log.debug("Temporary export {} removed by publication move", temporary);
                return Result.ok(request.destination());
            } catch (UncheckedIOException failure) {
                return failAfterClose(moveError(Objects.requireNonNull(failure.getCause(), "move failure cause")));
            }
        }

        private void moveWithOverwrite() {
            try {
                moves.move(temporary, request.destination(), ATOMIC_MOVE, REPLACE_EXISTING);
            } catch (UncheckedIOException failure) {
                if (!(failure.getCause() instanceof AtomicMoveNotSupportedException)) {
                    throw failure;
                }
                log.warn("Atomic move is unsupported for {}; falling back to replacement", request.destination());
                moves.move(temporary, request.destination(), REPLACE_EXISTING);
            }
        }

        private Result<Path> fail(final AppError primary) {
            closeAll();
            return failAfterClose(primary);
        }

        private Result<Path> failAfterClose(final AppError primary) {
            deleteTemporary(primary);
            return Result.err(primary);
        }

        private Result<Boolean> closeAll() {
            @Nullable AppError firstError = null;
            final List<Document> closing = List.copyOf(opened);
            opened.clear();
            for (Document document : closing) {
                final Result<Boolean> closed = closeOne(document);
                if (firstError == null && closed.isErr()) {
                    firstError = errorOf(closed);
                }
            }
            return firstError == null ? Result.ok(Boolean.TRUE) : Result.err(firstError);
        }

        private Result<Boolean> closeOne(final Document document) {
            try {
                final Result<Boolean> closed = documents.close(document);
                log.debug("Close result for export document {} is {}", document.id(), outcomeOf(closed));
                return closed;
            } catch (Throwable cause) {
                return Result.err(unexpectedError("close export document " + document.id(), cause));
            }
        }

        private void deleteTemporary(final AppError primary) {
            try {
                final boolean deleted = Files.deleteIfExists(temporary);
                log.debug("Temporary export {} deleted: {}", temporary, deleted);
            } catch (Throwable cause) {
                log.debug(
                        "Temporary export {} deletion failed while preserving primary error {}",
                        temporary,
                        primary.code());
                log.error(
                        "Failed to clean temporary export {} while preserving primary error {}",
                        temporary,
                        primary.code(),
                        cause);
            }
        }
    }

    private static Document applyDecisions(final Document fresh, final Document decided) {
        final Map<String, Segment> decisions = decided.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .collect(Collectors.toMap(
                        Segment::id, Function.identity(), (first, ignored) -> first, LinkedHashMap::new));
        final List<Unit> units = fresh.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> copyDecision(segment, decisions.get(segment.id())))
                        .toList()))
                .toList();
        return fresh.withUnits(units);
    }

    private static Segment copyDecision(final Segment fresh, @Nullable final Segment decision) {
        return decision == null ? fresh : fresh.withDecision(decision.status(), decision.targetInner());
    }

    private static int segmentCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    private static void logDecisionCounts(final Document document) {
        final long accepted = countStatus(document, SegmentStatus.ACCEPTED);
        final long flagged = countStatus(document, SegmentStatus.FLAGGED);
        log.debug("Applied export decisions: accepted {}, flagged {}", accepted, flagged);
    }

    private static long countStatus(final Document document, final SegmentStatus status) {
        return document.units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.status() == status)
                .count();
    }

    private static String outcomeOf(final Result<?> result) {
        return result.isOk() ? "ok" : "error " + errorOf(result).code();
    }

    private static AppError moveError(final IOException cause) {
        if (cause instanceof FileAlreadyExistsException) {
            log.warn("Refused export because destination appeared before publication");
            return AppError.of(
                    ErrorCode.validation,
                    "This destination already exists",
                    "The translated book was not published because overwrite is disabled.",
                    null,
                    cause);
        }
        log.error("Failed to publish the validated temporary book", cause);
        return AppError.of(
                ErrorCode.internal,
                "This book could not be published",
                "The validated temporary book could not be moved to its destination.",
                null,
                cause);
    }

    private static AppError sourceChangedError() {
        return AppError.of(
                ErrorCode.validation,
                "The source book changed",
                "The source file changed after translation began, so this export was stopped.");
    }

    private static AppError countMismatchError() {
        return AppError.of(
                ErrorCode.validation,
                "The exported book failed validation",
                "The written book did not contain the same number of translatable segments as the source.");
    }

    private static AppError cancelledError() {
        return AppError.of(
                ErrorCode.cancelled,
                "Export cancelled",
                "The translated book was checked but not published because the job was cancelled.");
    }

    private static AppError unexpectedError(final String action, final Throwable cause) {
        log.error("Unexpected failure while attempting to {}", action, cause);
        return AppError.of(
                ErrorCode.internal,
                "This book could not be exported",
                "An unexpected error stopped the export before publication.",
                null,
                cause);
    }
}
