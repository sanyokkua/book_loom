package ua.bookloom.document.fb2;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open-FB2-document registry: what {@code Fb2Reader} parsed, held until {@code Fb2Writer} needs it again.
 *
 * <p>In-memory only, matching {@code OpenEpubRegistry} — an open document does not survive a process restart, and
 * making that durable is a future change. Concurrent because nothing confines document opening to one thread.
 */
@Singleton
public final class OpenFb2Registry {

    private final Map<String, ParsedFb2> byDocumentId = new ConcurrentHashMap<>();

    /** Guice constructs this directly; there is nothing to inject. */
    public OpenFb2Registry() {
        // No dependencies.
    }

    void put(String documentId, ParsedFb2 parsed) {
        byDocumentId.put(Objects.requireNonNull(documentId, "documentId"), Objects.requireNonNull(parsed, "parsed"));
    }

    Optional<ParsedFb2> find(String documentId) {
        return Optional.ofNullable(byDocumentId.get(Objects.requireNonNull(documentId, "documentId")));
    }
}
