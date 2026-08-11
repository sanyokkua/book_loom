package ua.bookloom.document.md;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open-Markdown-document registry, matching {@code OpenEpubRegistry}: in-memory only, so an open document does
 * not survive a process restart.
 */
@Singleton
public final class OpenMarkdownRegistry {

    private final Map<String, ParsedMarkdown> byDocumentId = new ConcurrentHashMap<>();

    /** Guice constructs this directly; there is nothing to inject. */
    public OpenMarkdownRegistry() {
        // No dependencies.
    }

    void put(String documentId, ParsedMarkdown parsed) {
        byDocumentId.put(Objects.requireNonNull(documentId, "documentId"), Objects.requireNonNull(parsed, "parsed"));
    }

    Optional<ParsedMarkdown> find(String documentId) {
        return Optional.ofNullable(byDocumentId.get(Objects.requireNonNull(documentId, "documentId")));
    }
}
