package ua.bookloom.document.epub;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry from a parsed {@code Document}'s id to the {@link ParsedEpub} state a later change's
 * {@code write()} needs to find again — the opaque {@code SkeletonHandle} that crosses the {@code :api} boundary
 * names a tree only {@code :document} can resolve (design.md D1).
 *
 * <p>In-memory only for this change: an open document does not survive a process restart. Making that durable is
 * a future change, not this one.
 */
@Singleton
final class OpenEpubRegistry {

    private final Map<String, ParsedEpub> byDocumentId = new ConcurrentHashMap<>();

    /**
     * Registers {@code parsed} under {@code documentId}.
     *
     * @param documentId the parsed {@code Document}'s own id
     * @param parsed the state to register under it
     */
    void put(String documentId, ParsedEpub parsed) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(parsed, "parsed");
        byDocumentId.put(documentId, parsed);
    }

    /**
     * Finds the state previously registered under {@code documentId}.
     *
     * @param documentId a previously-registered {@code Document}'s id
     * @return the registered state, or empty if no document is open under that id
     */
    Optional<ParsedEpub> find(String documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return Optional.ofNullable(byDocumentId.get(documentId));
    }
}
