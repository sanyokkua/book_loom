package ua.bookloom.api.document;

/**
 * A segment's position in the translation status machine
 * ({@code 02_Architecture/03_DOCUMENT_MODEL.md#segment-status-machine}).
 *
 * <pre>
 *         translate + pass the checks
 * PENDING ─────────────────────────► ACCEPTED
 *    │                                   │
 *    │ fail after the one repair         │ a person's edit or a kept Proofread rewrite
 *    ▼                                   ▼
 * FLAGGED ───(manual/assisted edit)──► REVISED
 * </pre>
 *
 * <p>{@link #ACCEPTED} and {@link #REVISED} are terminal-exportable; {@link #FLAGGED} is exportable too, carrying
 * the source or best draft through — export is allowed at any time, not only once every segment is accepted.
 *
 *
 * <p>The document engine produces only {@link #PENDING}; the pipeline and the persistence schema key on the whole
 * enum.
 */
public enum SegmentStatus {

    /** Parsed, not yet translated. */
    PENDING,

    /** Translated and passed the checks. Terminal-exportable. */
    ACCEPTED,

    /** Failed after its one repair call. Exportable, carrying the source/best draft through. */
    FLAGGED,

    /** Reached from {@link #FLAGGED} or {@link #ACCEPTED} by a person's edit, or from {@link #ACCEPTED} by a
     * Proofread rewrite that was kept. Terminal-exportable. */
    REVISED
}
