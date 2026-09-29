package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.pipeline.TestDocuments;

/** Shared real-document setup and narrow failure seams for exporter tests. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookExporterTestSupport {

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
        final List<Segment> segments = new ArrayList<>(unit.segments());
        segments.set(0, segments.getFirst().withDecision(status, targetInner));
        final List<Unit> units = new ArrayList<>(document.units());
        units.set(0, unit.withSegments(segments));
        return document.withUnits(units);
    }

    /** The decisions of {@code decided}, each target checked in its source placeholders' order. */
    static EffectiveTargets sourceOrder(final Document decided) {
        return new EffectiveTargets(decided, Map.of());
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

    public static class RecordingDocumentPort extends TestDocuments.ForwardingPort {

        private final List<Document> opened = new ArrayList<>();
        private final List<Document> closed = new ArrayList<>();
        private final List<Document> written = new ArrayList<>();
        private final List<Path> writeDestinations = new ArrayList<>();

        public RecordingDocumentPort(final DocumentPort delegate) {
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
        public Result<Path> write(
                final Document document,
                final Path destination,
                @Nullable final String sourceLanguage,
                final String targetLanguage) {
            written.add(document);
            writeDestinations.add(destination);
            return super.write(document, destination, sourceLanguage, targetLanguage);
        }

        @Override
        public Result<Boolean> close(final Document document) {
            closed.add(document);
            return super.close(document);
        }

        public List<Document> openedDocuments() {
            return List.copyOf(opened);
        }

        public List<Document> closedDocuments() {
            return List.copyOf(closed);
        }

        public List<Document> writtenDocuments() {
            return List.copyOf(written);
        }

        public List<Path> writeDestinations() {
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

    public static final class SingleCloseFailurePort extends RecordingDocumentPort {

        private final int failingIndex;
        private final AppError failure;
        private final AtomicInteger closeCount = new AtomicInteger();

        public SingleCloseFailurePort(final DocumentPort delegate, final int failingIndex, final AppError failure) {
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
        public Result<Path> write(
                final Document document,
                final Path destination,
                @Nullable final String sourceLanguage,
                final String targetLanguage) {
            return Result.err(failure);
        }
    }

    public static final class OpenFailurePort extends TestDocuments.ForwardingPort {

        private final AppError failure;

        public OpenFailurePort(final DocumentPort delegate, final AppError failure) {
            super(delegate);
            this.failure = failure;
        }

        @Override
        public Result<Document> open(final Path source) {
            return Result.err(failure);
        }
    }

    public static final class ThrowingMoveOperation implements ExportMoveOperation {

        private final RuntimeException failure;

        public ThrowingMoveOperation(final RuntimeException failure) {
            this.failure = failure;
        }

        @Override
        public Path move(final Path source, final Path destination, final CopyOption... options) {
            throw failure;
        }
    }

    public static final class ScriptedMoveOperation implements ExportMoveOperation {

        private final List<Result<Path>> results = new ArrayList<>();
        private final List<List<CopyOption>> optionCalls = new ArrayList<>();
        private int index;

        public ScriptedMoveOperation answer(final Result<Path> result) {
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

        public List<List<CopyOption>> optionCalls() {
            return List.copyOf(optionCalls);
        }
    }

    public static final class DestinationCreatingMoveOperation implements ExportMoveOperation {

        private final String content;
        private final List<List<CopyOption>> optionCalls = new ArrayList<>();

        public DestinationCreatingMoveOperation(final String content) {
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

        public List<List<CopyOption>> optionCalls() {
            return List.copyOf(optionCalls);
        }
    }

    /** Blocks inside the n-th close so a test can act while the export holds its documents open. */
    public static final class BlockingClosePort extends TestDocuments.ForwardingPort {

        private static final long WAIT_SECONDS = 5;

        private final AtomicInteger closes = new AtomicInteger();
        private final int blockingClose;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        public BlockingClosePort(final DocumentPort delegate, final int blockingClose) {
            super(delegate);
            this.blockingClose = blockingClose;
        }

        @Override
        public Result<Boolean> close(final Document document) {
            final Result<Boolean> result = super.close(document);
            if (closes.incrementAndGet() == blockingClose) {
                entered.countDown();
                await(released);
            }
            return result;
        }

        public void awaitBlockingClose() {
            await(entered);
        }

        public void releaseClose() {
            released.countDown();
        }

        private static void await(final CountDownLatch latch) {
            try {
                if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting for export close");
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for export close", cause);
            }
        }
    }
}
