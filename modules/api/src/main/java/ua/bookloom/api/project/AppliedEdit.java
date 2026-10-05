package ua.bookloom.api.project;

import java.util.Objects;
import java.util.Optional;

/**
 * One find-and-replace edit the reviewer made to a segment's target and the app verified, kept on the segment as a
 * {@link QaFinding} so the review list can show it as a diff without a new stored field.
 *
 * <p>The finding's note carries both texts joined by {@link #SEPARATOR}, a symbol no book text uses, because a quote or
 * a replacement may hold any ordinary character and so no ordinary separator could be split on safely.
 *
 * @param criterion what the reviewer says was wrong, such as {@code gender} or {@code invented-word}
 * @param quote the text the edit replaced, as it stood in the target
 * @param replacement the text that stands there now; empty when the edit deleted the quote
 */
public record AppliedEdit(String criterion, String quote, String replacement) {

    /** The {@link QaFinding#raisedBy()} of a finding that records an applied edit. */
    public static final String RAISED_BY = "reviewer-edit";

    /** The symbol for a record separator, which joins the quote to its replacement in the finding's note. */
    public static final char SEPARATOR = '␞';

    /** Rejects a missing component. */
    public AppliedEdit {
        Objects.requireNonNull(criterion, "criterion");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * Records this edit as a low finding, which never blocks acceptance.
     *
     * @return the finding that {@link #from} reads back into this edit
     */
    public QaFinding toFinding() {
        return new QaFinding(criterion, Severity.LOW, quote + SEPARATOR + replacement, RAISED_BY);
    }

    /**
     * Reads an applied edit back from a segment's finding.
     *
     * @param finding any finding of a segment; never null
     * @return the edit it records, or empty when the finding was raised by something else
     */
    public static Optional<AppliedEdit> from(final QaFinding finding) {
        Objects.requireNonNull(finding, "finding");
        final int at = finding.note().indexOf(SEPARATOR);
        if (!RAISED_BY.equals(finding.raisedBy()) || at < 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedEdit(
                finding.kind(), finding.note().substring(0, at), finding.note().substring(at + 1)));
    }
}
