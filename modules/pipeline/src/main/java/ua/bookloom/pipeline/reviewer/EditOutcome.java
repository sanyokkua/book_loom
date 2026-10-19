package ua.bookloom.pipeline.reviewer;

import java.util.List;
import java.util.Objects;

/**
 * What applying a segment's edits came to.
 *
 * @param text the candidate with every verified edit applied; the candidate itself when none was
 * @param applied the verified edits, in the order applied
 * @param failed the edits whose quote was found but whose application was refused, each with why; the reviewer's
 *     evidence that something is wrong there
 * @param ignored how many edits named a quote the candidate does not hold, or changed nothing
 * @param notes the fluency and style edits, which are only noted for the person
 */
public record EditOutcome(
        String text, List<ReviewEdit> applied, List<FailedEdit> failed, int ignored, List<ReviewEdit> notes) {

    /** The kind of the low note left for a reviewer edit the code refused for what the edit did; the run summary counts it. */
    public static final String IGNORED_EDIT_KIND = "ignored-edit";

    /** Copies the lists and rejects a missing component. */
    public EditOutcome {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(applied, "applied");
        Objects.requireNonNull(failed, "failed");
        Objects.requireNonNull(notes, "notes");
        applied = List.copyOf(applied);
        failed = List.copyOf(failed);
        notes = List.copyOf(notes);
    }

    /**
     * The refused edits that stand as evidence of a defect in the draft: a meaning swap or an ambiguous quote.
     *
     * @return the refusals that may earn a directed fix; never null
     */
    public List<FailedEdit> evidenced() {
        return failed.stream().filter(edit -> !edit.reason().disprovesEdit()).toList();
    }

    /**
     * The refused edits whose own refusal disproves them, which leave the draft untouched and unflagged.
     *
     * @return the refusals that are only set aside; never null
     */
    public List<FailedEdit> disproved() {
        return failed.stream().filter(edit -> edit.reason().disprovesEdit()).toList();
    }

    /**
     * An edit that could not be applied.
     *
     * @param edit the edit as written
     * @param reason why it was refused
     */
    public record FailedEdit(ReviewEdit edit, Verification.FailureReason reason) {

        /** Rejects a missing component. */
        public FailedEdit {
            Objects.requireNonNull(edit, "edit");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
