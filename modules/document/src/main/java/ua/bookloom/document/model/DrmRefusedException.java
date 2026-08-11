package ua.bookloom.document.model;

/**
 * The book is protected and cannot be imported: an EPUB whose {@code META-INF/encryption.xml} encrypts something
 * that is not a manifest-declared font (ADR-0026), or a {@code .fb2.zip} whose member is zip-encrypted (EC-DRM-1).
 * Thrown before any {@code Document}, {@code Unit} or {@code Segment} exists, so no partial import can result.
 *
 * <p>Lives in {@code document.model} rather than in one format's package because both zip-backed formats can
 * raise it and the port boundary classifies it once for both.
 *
 * <p>Unchecked and thrown rather than returned: {@code DocumentService} is the boundary that catches it and
 * builds the {@code ErrorCode.validation} envelope whose <em>message</em> — protected, not malformed — is what
 * distinguishes this refusal from a corrupt container.
 */
public final class DrmRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message what was encrypted and why that refuses the book; never a file-system path
     */
    public DrmRefusedException(String message) {
        super(message);
    }

    /**
     * @param message what could not be adjudicated
     * @param cause the parse failure that prevented adjudication; internal-only, never shown to the user
     */
    public DrmRefusedException(String message, Throwable cause) {
        super(message, cause);
    }
}
