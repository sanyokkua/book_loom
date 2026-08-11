package ua.bookloom.document.model;

/**
 * {@code EpubWriter.write} was called for a {@code Document} id this process never {@code open()}ed — a stale id,
 * or one produced by a different process. Reachable in practice ({@link OpenEpubRegistry} is in-memory only and
 * does not survive a restart), and distinct from {@link CorruptContainerException}: the source archive is not at
 * fault here, so a later change's {@code DocumentService} classifies this to {@code ErrorCode.internal} rather
 * than {@code ErrorCode.validation}.
 *
 * <p>Unchecked and thrown from {@code EpubWriter.write}; see {@link CorruptContainerException} for why this is
 * thrown rather than returned.
 */
public final class DocumentNotOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DocumentNotOpenException(String documentId) {
        super("No open EPUB document for id: " + documentId);
    }
}
