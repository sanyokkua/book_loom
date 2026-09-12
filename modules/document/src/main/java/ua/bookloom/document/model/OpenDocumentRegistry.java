package ua.bookloom.document.model;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds what a format's reader parsed until that format's writer needs it again, keyed by the parsed
 * {@code Document}'s id — the opaque {@code SkeletonHandle} that crosses the {@code :api} boundary names state only
 * {@code :document} can resolve. One subclass per format exists purely so Guice can inject each as its own
 * singleton; the behaviour is identical.
 *
 * <p>In-memory only: an open document does not survive a process restart. Concurrent because nothing confines
 * document opening to one thread.
 *
 * @param <T> the per-format parsed state a reader registers and a writer looks up
 */
public abstract class OpenDocumentRegistry<T> {

    private final Map<String, T> byDocumentId = new ConcurrentHashMap<>();

    protected OpenDocumentRegistry() {
        // Subclasses carry no state of their own.
    }

    /**
     * Registers {@code parsed} under {@code documentId}, replacing any state already held under that id.
     *
     * @param documentId the parsed {@code Document}'s own id
     * @param parsed the state to hold until the matching writer asks for it
     */
    public final void put(String documentId, T parsed) {
        byDocumentId.put(Objects.requireNonNull(documentId, "documentId"), Objects.requireNonNull(parsed, "parsed"));
    }

    /**
     * Looks up what the reader registered under {@code documentId}.
     *
     * @param documentId a previously-registered {@code Document}'s id
     * @return the registered state, or empty if no document is open under that id
     */
    public final Optional<T> find(String documentId) {
        return Optional.ofNullable(byDocumentId.get(Objects.requireNonNull(documentId, "documentId")));
    }

    /**
     * Forgets the state held under {@code documentId}, releasing the parsed trees or bytes it referenced.
     *
     * @param documentId a previously-registered {@code Document}'s id
     * @return the state that was held, or empty if no document was open under that id
     */
    public final Optional<T> close(String documentId) {
        return Optional.ofNullable(byDocumentId.remove(Objects.requireNonNull(documentId, "documentId")));
    }
}
