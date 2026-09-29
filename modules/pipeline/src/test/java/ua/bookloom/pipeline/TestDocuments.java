package ua.bookloom.pipeline;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;

/** The real document port and a forwarding wrapper every suite that needs a real book shares. */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestDocuments {

    /** Builds the real four-format document port through its Guice module. */
    public static DocumentPort documents() {
        return Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    /** A document port that forwards every call, for a test that changes one behaviour. */
    public abstract static class ForwardingPort implements DocumentPort {

        protected final DocumentPort delegate;

        protected ForwardingPort(final DocumentPort delegate) {
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

        // The export passes the brief's source language; forwarding only the three-argument write would drop it.
        @Override
        public Result<Path> write(
                final Document document,
                final Path destination,
                @Nullable final String sourceLanguage,
                final String targetLanguage) {
            return delegate.write(document, destination, sourceLanguage, targetLanguage);
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
}
