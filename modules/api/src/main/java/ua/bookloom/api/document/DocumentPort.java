package ua.bookloom.api.document;

import java.nio.file.Path;
import ua.bookloom.api.Result;

/**
 * Opens a book file into a {@link Document} and writes a {@link Document} back out in its original format.
 * Implemented by {@code :document} ({@code DocumentService}).
 *
 * <p>Both methods are boundary methods: per {@code 02_Architecture/09_ERROR_HANDLING.md#boundary-discipline}, no
 * exception may cross this interface. A failure — a corrupt container, a DRM refusal, an unexpected throwable — is
 * always returned as a failed {@link Result}, never thrown.
 */
public interface DocumentPort {

    /**
     * Parses a book file into an immutable skeleton plus ordered segments.
     *
     * @param source the book file to open
     * @return the parsed document, or a failed result (for example {@code ErrorCode.validation} for a corrupt
     *     container, or a DRM-blocked outcome for encrypted content)
     */
    Result<Document> open(Path source);

    /**
     * Reassembles {@code document} and writes it to {@code destination} in its original format, setting the target
     * language.
     *
     * @param document the document to reassemble, in the state its segments should be written back in
     * @param destination the file to write
     * @param targetLanguage the language to declare in the written book (for example an ISO 639-1 code)
     * @return the path written, or a failed result
     */
    Result<Path> write(Document document, Path destination, String targetLanguage);
}
