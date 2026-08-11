package ua.bookloom.document.txt;

import com.google.inject.Singleton;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open-plain-text-document registry, matching the other formats': in-memory only, so an open document does
 * not survive a process restart.
 */
@Singleton
public final class OpenTxtRegistry {

    private final Map<String, ParsedTxt> byDocumentId = new ConcurrentHashMap<>();

    /** Guice constructs this directly; there is nothing to inject. */
    public OpenTxtRegistry() {
        // No dependencies.
    }

    void put(String documentId, ParsedTxt parsed) {
        byDocumentId.put(Objects.requireNonNull(documentId, "documentId"), Objects.requireNonNull(parsed, "parsed"));
    }

    Optional<ParsedTxt> find(String documentId) {
        return Optional.ofNullable(byDocumentId.get(Objects.requireNonNull(documentId, "documentId")));
    }

    /**
     * The open document's state: the original bytes, never mutated, plus the encoding they were read with.
     *
     * @param originalBytes the source file's bytes exactly as read — the buffer every span indexes and the one
     *     reassembly copies from
     * @param charset the encoding the text was decoded with, and the one a target string must encode back into
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: the immutable source buffer, defensively copied both
    // ways; equals()/hashCode() are never used for this internal carrier.
    record ParsedTxt(byte[] originalBytes, Charset charset) {

        ParsedTxt {
            Objects.requireNonNull(originalBytes, "originalBytes");
            Objects.requireNonNull(charset, "charset");
            originalBytes = originalBytes.clone();
        }

        @Override
        public byte[] originalBytes() {
            return originalBytes.clone();
        }
    }
}
