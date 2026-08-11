package ua.bookloom.document.model;

/**
 * Target text supplied for a segment cannot be parsed as the inline markup its format requires — for example a
 * bare {@code &} or {@code <} that does not open a valid entity or element (EC-DOC-1).
 *
 * <p>Unchecked and thrown from {@link Jdom2TreeNode#replaceChildren}. A later change's {@code DocumentService} —
 * the port boundary — catches this specific type and classifies it to {@code ErrorCode.validation}: the model
 * produced foreseeable, malformed output, which is the caller's problem to act on, not a bug in this application.
 * Deliberately distinct from {@link CorruptContainerException} and {@link DrmRefusedException} so the port
 * boundary can name each situation precisely rather than folding three different messages into one; this one must
 * not describe a protected or corrupt book, and the other two must not describe a malformed translation.
 */
public final class MalformedFragmentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MalformedFragmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
