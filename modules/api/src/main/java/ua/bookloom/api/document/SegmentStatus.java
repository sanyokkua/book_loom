package ua.bookloom.api.document;

/**
 * A segment's position in the translation status machine
 * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine}).
 *
 * <pre>
 *         translate + pass QA/judge
 * PENDING ─────────────────────────► ACCEPTED
 *    │                                   │
 *    │ fail after N repair tries         │ backward-revision re-render
 *    ▼                                   ▼
 * FLAGGED ───(manual/assisted edit)──► REVISED
 * </pre>
 *
 * <p>{@link #ACCEPTED} and {@link #REVISED} are terminal-exportable; {@link #FLAGGED} is exportable too, carrying
 * the source or best draft through — export is allowed at any time, not only once every segment is accepted.
 *
 * <p>{@code add-document-skeleton-and-epub-roundtrip} never translates, so every segment it produces is
 * {@link #PENDING} and stays there; the other three constants ship now because the pipeline and the persistence
 * schema both key on the whole enum.
 */
public enum SegmentStatus {

    /** Parsed, not yet translated or judged. The only status this change ever produces. */
    PENDING,

    /** Translated and passed QA/judge. Terminal-exportable. */
    ACCEPTED,

    /** Failed after the repair budget was exhausted. Exportable, carrying the source/best draft through. */
    FLAGGED,

    /** Reached from {@link #FLAGGED} by a manual/assisted edit, or from {@link #ACCEPTED} by a backward-revision
     * re-render. Terminal-exportable. */
    REVISED
}
