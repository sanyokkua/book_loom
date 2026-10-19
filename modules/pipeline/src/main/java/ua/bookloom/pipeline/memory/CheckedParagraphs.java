package ua.bookloom.pipeline.memory;

import com.google.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * Remembers, for the length of the application run, which paragraphs the consistency pass has already checked and in
 * what state, so an export of a book nothing changed in sends no call it already paid for. A paragraph is kept as a
 * hash of everything its check showed the model (its target, its neighbours, the glossary lines, the summary, the style
 * sheet), so any change to any of them makes the memory miss. Losing the memory costs only calls, never correctness,
 * which is why it is not stored (ADR-0034).
 */
@Slf4j
@Singleton
public final class CheckedParagraphs {

    private static final char KEY_SEPARATOR = '\0';

    private final Map<String, String> hashes = new ConcurrentHashMap<>();

    /**
     * Whether the paragraph was last checked in exactly this state.
     *
     * @param projectId the non-null project
     * @param segmentId the non-null segment
     * @param hash the non-null fingerprint of what its check would show the model
     * @return {@code true} if the same state was checked before, {@code false} otherwise
     */
    public boolean has(final String projectId, final String segmentId, final String hash) {
        Objects.requireNonNull(hash, "hash");
        final boolean known = hash.equals(hashes.get(key(projectId, segmentId)));
        log.debug("Checked paragraph lookup projectId={} segmentId={} known={}", projectId, segmentId, known);
        return known;
    }

    /**
     * Records the state a paragraph was just checked in, replacing any earlier one.
     *
     * @param projectId the non-null project
     * @param segmentId the non-null segment
     * @param hash the non-null fingerprint of what the check showed the model
     */
    public void remember(final String projectId, final String segmentId, final String hash) {
        Objects.requireNonNull(hash, "hash");
        hashes.put(key(projectId, segmentId), hash);
        log.debug("Checked paragraph remembered projectId={} segmentId={}", projectId, segmentId);
    }

    private static String key(final String projectId, final String segmentId) {
        return Objects.requireNonNull(projectId, "projectId")
                + KEY_SEPARATOR
                + Objects.requireNonNull(segmentId, "segmentId");
    }
}
