package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.i18n.RaiserWords;

/**
 * One entry of the review desk's list.
 *
 * @param segmentId the segment's stable id, which every action names
 * @param locator the human-readable locator the person reads
 * @param badge the main finding of a flagged segment, or {@code null} when it is not flagged or has no finding
 * @param doubtCheck the name of the check that doubts an accepted segment, from its first audit finding, or {@code null}
 *     when the segment is flagged (its badge speaks then) or no audit check doubts it
 * @param keptAsSource whether the record was kept as source by choice, so the row says so
 * @param marker what became of the segment, as the row's marker shows it
 * @param line why it is listed (the badge's code word) then its text, with no formatting tokens; the cell words the
 *     why in the window's language from {@code badge}
 * @param fullText the text the row reads, whole, for its hover: the person's edit, else the model's, else the source
 */
public record ReviewRow(
        String segmentId,
        String locator,
        @Nullable FindingBadge badge,
        @Nullable String doubtCheck,
        boolean keptAsSource,
        DeskMarker marker,
        String line,
        String fullText) {

    private static final Pattern TOKEN = Pattern.compile("⟦g?\\d+⟧");
    private static final String SEPARATOR = " · ";

    /** Rejects a missing identity. */
    public ReviewRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(fullText, "fullText");
    }

    /**
     * The row for a segment the desk listed. Logs nothing: a list of a whole book builds thousands.
     *
     * @param view a view read from the review desk
     * @param later whether the person left it for later in this application run
     * @return its row; only a flagged segment gets a badge
     */
    public static ReviewRow of(final SegmentView view, final boolean later) {
        Objects.requireNonNull(view, "view");
        final FindingBadge badge = view.status() == SegmentStatus.FLAGGED
                ? FindingBadge.of(view.findings(), view.judgeScore()).orElse(null)
                : null;
        final String doubt = view.status() == SegmentStatus.FLAGGED ? null : doubtOf(view);
        final String text = readable(view);
        final String line = badge == null ? text : badge.code() + SEPARATOR + text;
        return new ReviewRow(
                view.segmentId(),
                view.locator(),
                badge,
                doubt,
                view.path() == SegmentPath.SOURCE_KEPT,
                DeskMarker.of(view, later),
                line,
                text);
    }

    private static @Nullable String doubtOf(final SegmentView view) {
        return view.findings().stream()
                .map(QaFinding::raisedBy)
                .filter(raisedBy -> raisedBy.startsWith(RaiserWords.AUDIT_PREFIX))
                .map(RaiserWords::checkOf)
                .findFirst()
                .orElse(null);
    }

    private static String readable(final SegmentView view) {
        final String machine = view.maskedMachineTarget() != null ? view.maskedMachineTarget() : view.maskedSource();
        final String masked = view.maskedUserTarget() != null ? view.maskedUserTarget() : machine;
        return TOKEN.matcher(masked).replaceAll("").replaceAll("\\s+", " ").strip();
    }
}
