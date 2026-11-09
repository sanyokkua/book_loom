package ua.bookloom.pipeline.checks;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.qa.CheckLevel;

/**
 * One defect a deterministic text check found.
 *
 * @param kind which check found it
 * @param span the exact place in the checked text
 * @param explanation a plain sentence naming what is wrong and how to put it right, written so the repair
 *     prompt can repeat it as it is
 * @param blocking whether the segment may not be accepted with it; a soft finding only lowers the segment's confidence
 * @param level the level this finding is raised at when it differs from its check's default for a non-blocking finding
 *     (a lost number repairs, an extra one is a note), or {@code null} to take the default; a block level makes the
 *     finding blocking whatever {@code blocking} says
 */
public record CheckFinding(
        FindingKind kind,
        TextSpan span,
        String explanation,
        boolean blocking,
        @Nullable CheckLevel level) {

    private static final int EXCERPT_LIMIT = 80;

    /** Rejects missing parts. */
    public CheckFinding {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(span, "span");
        Objects.requireNonNull(explanation, "explanation");
        if (level != null) {
            blocking = level == CheckLevel.BLOCK;
        }
    }

    /** A finding that blocks or takes its check's default soft level. */
    public CheckFinding(final FindingKind kind, final TextSpan span, final String explanation, final boolean blocking) {
        this(kind, span, explanation, blocking, blocking ? CheckLevel.BLOCK : null);
    }

    /** A finding raised at the given level. */
    public CheckFinding(final FindingKind kind, final TextSpan span, final String explanation, final CheckLevel level) {
        this(kind, span, explanation, level == CheckLevel.BLOCK, level);
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
