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

    /**
     * Restores {@code segment}'s placeholders into {@code translatedMasked}, after first validating that its
     * placeholder multiset matches {@code segment}'s masked form. Implemented by {@code :document}'s masking
     * machinery; exposed here so {@code :pipeline} — which may not depend on {@code :document}
     * (`architecture-layering.md`) — has a seam to call unmask through.
     *
     * <p>{@code format} is required because the escaping rule applied to the text between tokens, and the
     * structural check performed on the result, both differ by format, and {@link Segment} itself carries no format
     * — its {@link SkeletonAnchor} distinguishes a tree-shaped anchor from a buffer-shaped one, but not EPUB from
     * FB2, nor Markdown from TXT.
     *
     * <p>A multiset mismatch or a format-specific structure-check failure is {@code ErrorCode.validation} — the
     * frozen rule calls this "a hard gate", not a translation defect the caller can appeal. An unexpected failure
     * is {@code ErrorCode.internal}. No exception crosses this method.
     *
     * @param format the segment's book format, needed only to select the escaping/structure rule
     * @param segment the segment whose placeholder map supplies each token's mapped fragment
     * @param translatedMasked the translated text to validate and restore
     * @return the restored content, or a failed result carrying {@code ErrorCode.validation} when the placeholder
     *     multiset does not match — nothing is restored and {@code translatedMasked} is left unaltered
     */
    Result<String> unmask(BookFormat format, Segment segment, String translatedMasked);
}
