package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.pipeline.PromptSection;

/**
 * What one segment's call was sent besides the run's fixed frame (design D8): the frame is stored once per project as a
 * {@link PromptFrame} under {@code frameHash}, so a segment keeps only the key to it and the sections of its own call.
 * The review desk's "What the model saw" joins the two back together, after a restart too.
 *
 * @param frameHash the {@link PromptFrame#hash()} of the fixed frame the call was sent with, or empty when no call was
 *     made for the segment (a memory reuse or a shortcut that needed none)
 * @param sections the call's own parts as sent, the user message's blocks in order (masked, as sent); empty when no
 *     call was made
 * @param chapterSummaryKeys the keys of the chapter summaries the call carried, oldest first; always empty until
 *     chapter summaries exist (group 12)
 * @param retry {@code true} when the sections are the context a review retry rebuilt and sent, so the desk can label
 *     them as such after a restart; {@code false} for a run's own call
 */
public record ContextSnapshot(
        String frameHash, List<PromptSection> sections, List<String> chapterSummaryKeys, boolean retry) {

    /** Rejects a missing part and copies the lists, so a caller-held list cannot change the record. */
    public ContextSnapshot {
        Objects.requireNonNull(frameHash, "frameHash");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        chapterSummaryKeys = List.copyOf(Objects.requireNonNull(chapterSummaryKeys, "chapterSummaryKeys"));
    }

    /**
     * The snapshot of a segment no call was made for.
     *
     * @return a snapshot with no frame, no sections and no summary keys
     */
    public static ContextSnapshot noCall() {
        return new ContextSnapshot("", List.of(), List.of(), false);
    }
}
