package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.DocumentModule;

/** Shared real-document setup and narrow failure seams for exporter tests. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BookExporterTestSupport {

    static DocumentPort documents() {
        return Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    static Document opened(final DocumentPort documents, final Path source) {
        final Result<Document> result = documents.open(source);
        assertThat(result.error()).isNull();
        return Objects.requireNonNull(result.data(), "opened document");
    }

    static Document snapshot(final DocumentPort documents, final Path source) {
        final Document snapshot = opened(documents, source);
        assertThat(documents.close(snapshot).data()).isTrue();
        return snapshot;
    }

    static Document onlyDecision(
            final Document document, final SegmentStatus status, @Nullable final String targetInner) {
        final Unit unit = document.units().getFirst();
        final Segment decided = unit.segments().getFirst().withDecision(status, targetInner);
        return document.withUnits(List.of(unit.withSegments(List.of(decided))));
    }

    static int segmentCount(final Document document) {
        return document.units().stream()
                .mapToInt(unit -> unit.segments().size())
                .sum();
    }

    static String zipEntry(final Path archive, final String entryName) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            return new String(
                    Objects.requireNonNull(zip.getInputStream(zip.getEntry(entryName)), "zip entry")
                            .readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    static AppError error(final ErrorCode code, @Nullable final Throwable cause) {
        return AppError.of(code, "Export test failure", "The scripted export operation failed.", null, cause);
    }

    static AppError errorOf(final Result<?> result) {
        return Objects.requireNonNull(result.error(), "error");
    }

    abstract static class ForwardingDocumentPort implements DocumentPort {

        final DocumentPort delegate;

        ForwardingDocumentPort(final DocumentPort delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public Result<Document> open(final Path source) {
            return delegate.open(source);
        }

        @Override
        public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
            return delegate.write(document, destination, targetLanguage);
        }

        @Override
        public Result<Boolean> close(final Document document) {
            return delegate.close(document);
        }

        @Override
        public Result<String> unmask(final BookFormat format, final Segment segment, final String translatedMasked) {
            return delegate.unmask(format, segment, translatedMasked);
        }
    }

    static class RecordingDocumentPort extends ForwardingDocumentPort {

        private final List<Document> opened = new ArrayList<>();
        private final List<Document> closed = new ArrayList<>();
        private final List<Document> written = new ArrayList<>();
        private final List<Path> writeDestinations = new ArrayList<>();

        RecordingDocumentPort(final DocumentPort delegate) {
            super(delegate);
        }

        @Override
        public Result<Document> open(final Path source) {
            final Result<Document> result = super.open(source);
            if (result.data() != null) {
                opened.add(result.data());
            }
            return result;
        }

        @Override
        public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
            written.add(document);
            writeDestinations.add(destination);
            return super.write(document, destination, targetLanguage);
        }

        @Override
        public Result<Boolean> close(final Document document) {
            closed.add(document);
            return super.close(document);
        }

        List<Document> openedDocuments() {
            return List.copyOf(opened);
        }

        List<Document> closedDocuments() {
            return List.copyOf(closed);
        }

        List<Document> writtenDocuments() {
            return List.copyOf(written);
        }

        List<Path> writeDestinations() {
            return List.copyOf(writeDestinations);
        }
    }

    static final class TemporaryOpenFailurePort extends RecordingDocumentPort {

        private final Predicate<Path> temporaryPath;
        private final AppError failure;

        TemporaryOpenFailurePort(
                final DocumentPort delegate, final Predicate<Path> temporaryPath, final AppError failure) {
            super(delegate);
            this.temporaryPath = temporaryPath;
            this.failure = failure;
        }

        @Override
        public Result<Document> open(final Path source) {
            return temporaryPath.test(source) ? Result.err(failure) : super.open(source);
        }
    }

    static final class CountMismatchPort extends RecordingDocumentPort {

        private final Predicate<Path> temporaryPath;

        CountMismatchPort(final DocumentPort delegate, final Predicate<Path> temporaryPath) {
            super(delegate);
            this.temporaryPath = temporaryPath;
        }

        @Override
        public Result<Document> open(final Path source) {
            final Result<Document> opened = super.open(source);
            if (!temporaryPath.test(source) || opened.data() == null) {
                return opened;
            }
            final Document document = opened.data();
            final Unit first = document.units().getFirst();
            final Unit shortened = first.withSegments(
                    first.segments().subList(0, first.segments().size() - 1));
            return Result.ok(document.withUnits(List.of(shortened)));
        }
    }

    static final class CloseFailurePort extends RecordingDocumentPort {

        private final AppError firstFailure;
        private final AppError secondFailure;
        private final AtomicInteger closeCount = new AtomicInteger();

        CloseFailurePort(final DocumentPort delegate, final AppError firstFailure, final AppError secondFailure) {
            super(delegate);
            this.firstFailure = firstFailure;
            this.secondFailure = secondFailure;
        }

        @Override
        public Result<Boolean> close(final Document document) {
            super.close(document);
            return Result.err(closeCount.getAndIncrement() == 0 ? firstFailure : secondFailure);
        }
    }

    static final class SingleCloseFailurePort extends RecordingDocumentPort {

        private final int failingIndex;
        private final AppError failure;
        private final AtomicInteger closeCount = new AtomicInteger();

        SingleCloseFailurePort(final DocumentPort delegate, final int failingIndex, final AppError failure) {
            super(delegate);
            this.failingIndex = failingIndex;
            this.failure = failure;
        }

        @Override
        public Result<Boolean> close(final Document document) {
            final Result<Boolean> actual = super.close(document);
            return closeCount.getAndIncrement() == failingIndex ? Result.err(failure) : actual;
        }
    }

    static final class AbsentClosePort extends RecordingDocumentPort {

        AbsentClosePort(final DocumentPort delegate) {
            super(delegate);
        }

        @Override
        public Result<Boolean> close(final Document document) {
            super.close(document);
            return Result.ok(Boolean.FALSE);
        }
    }

    static final class WriteFailurePort extends RecordingDocumentPort {

        private final AppError failure;

        WriteFailurePort(final DocumentPort delegate, final AppError failure) {
            super(delegate);
            this.failure = failure;
        }

        @Override
        public Result<Path> write(final Document document, final Path destination, final String targetLanguage) {
            return Result.err(failure);
        }
    }

    static final class OpenFailurePort extends ForwardingDocumentPort {

        private final AppError failure;

        OpenFailurePort(final DocumentPort delegate, final AppError failure) {
            super(delegate);
            this.failure = failure;
        }

        @Override
        public Result<Document> open(final Path source) {
            return Result.err(failure);
        }
    }

    static final class ThrowingMoveOperation implements ExportMoveOperation {

        private final RuntimeException failure;

        ThrowingMoveOperation(final RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Path move(final Path source, final Path destination, final CopyOption... options) {
            throw failure;
        }
    }

    static final class ScriptedMoveOperation implements ExportMoveOperation {

        private final List<Result<Path>> results = new ArrayList<>();
        private final List<List<CopyOption>> optionCalls = new ArrayList<>();
        private int index;

        ScriptedMoveOperation answer(final Result<Path> result) {
            results.add(result);
            return this;
        }

        @Override
        public Path move(final Path source, final Path destination, final CopyOption... options) {
            optionCalls.add(List.of(options));
            final Result<Path> result = results.get(index++);
            if (result.isOk()) {
                try {
                    Files.move(source, destination, options);
                    return destination;
                } catch (IOException cause) {
                    throw new UncheckedIOException(cause);
                }
            }
            final Throwable cause = errorOf(result).cause();
            if (cause instanceof IOException ioCause) {
                throw new UncheckedIOException(ioCause);
            }
            throw new IllegalStateException("scripted move failure requires an IOException cause", cause);
        }

        List<List<CopyOption>> optionCalls() {
            return List.copyOf(optionCalls);
        }
    }

    static final class DestinationCreatingMoveOperation implements ExportMoveOperation {

        private final String content;
        private final List<List<CopyOption>> optionCalls = new ArrayList<>();

        DestinationCreatingMoveOperation(final String content) {
            this.content = content;
        }

        @Override
        public Path move(final Path source, final Path destination, final CopyOption... options) {
            optionCalls.add(List.of(options));
            try {
                Files.writeString(destination, content);
                throw new UncheckedIOException(new java.nio.file.FileAlreadyExistsException(destination.toString()));
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            }
        }

        List<List<CopyOption>> optionCalls() {
            return List.copyOf(optionCalls);
        }
    }
}
