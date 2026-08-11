package ua.bookloom.document.model;

/**
 * The archive's container, OPF, or spine is missing or malformed (EC-EPUB-2).
 *
 * <p>Unchecked and thrown from {@code EpubReader.read}. A later change's {@code DocumentService} — the port
 * boundary — catches it and classifies it to {@code ErrorCode.validation}; per {@code error-envelope.md} no
 * exception may cross the {@code :document} module edge, but this one is thrown and caught entirely inside it.
 */
public final class CorruptContainerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CorruptContainerException(String message) {
        super(message);
    }

    public CorruptContainerException(String message, Throwable cause) {
        super(message, cause);
    }
}
