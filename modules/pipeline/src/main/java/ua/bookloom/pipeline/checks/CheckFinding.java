package ua.bookloom.pipeline.checks;

import java.util.Objects;

/**
 * One defect a deterministic text check found.
 *
 * @param kind which check found it
 * @param span the exact place in the checked text
 * @param explanation a plain sentence naming what is wrong and how to put it right, written so the directed-fix
 *     prompt can repeat it as it is
 * @param blocking whether the segment may not be accepted with it; a soft finding only lowers the reviewer's trust
 */
public record CheckFinding(FindingKind kind, TextSpan span, String explanation, boolean blocking) {

    private static final int EXCERPT_LIMIT = 80;

    /** Rejects missing parts. */
    public CheckFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(explanation, "explanation");
    }

    /**
     * The finding as one line for a stored finding's note: the quoted span, then the explanation.
     *
     * @return the line; a long span is cut to its first 80 chars
     */
    public String note() {
        final String text = span.text();
        final String excerpt = text.length() <= EXCERPT_LIMIT ? text : text.substring(0, EXCERPT_LIMIT) + "…";
        return "\"" + excerpt + "\" — " + explanation;
    }
}
