package ua.bookloom.ui.state;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The one short word a flagged row carries: why the segment was flagged, chosen from its main finding.
 *
 * <p>The main finding is the one of highest severity; on a tie a name outranks a wrong language, which outranks an
 * omission, which outranks any other kind. Kinds are compared in lower case because the reviewer writes them itself; its {@code terminology} is a name that was not honoured too.
 */
public enum FindingBadge {
    /** A locked or glossary name was not honoured. */
    NAME(MessageKey.REVIEW_BADGE_NAME, 3, "name", MessageKey.REVIEW_WHY_NAME),
    /** The target reads as the wrong language. */
    WRONG_LANGUAGE(MessageKey.REVIEW_BADGE_WRONG_LANGUAGE, 2, "language", MessageKey.REVIEW_WHY_WRONG_LANGUAGE),
    /** Part of the source is missing from the target. */
    OMISSION(MessageKey.REVIEW_BADGE_OMISSION, 1, "omission", MessageKey.REVIEW_WHY_OMISSION),
    /** Any other kind of finding, or only a score. */
    LOW_SCORE(MessageKey.REVIEW_BADGE_LOW_SCORE, 0, "score", MessageKey.REVIEW_WHY_LOW_SCORE);

    private static final String GLOSSARY = "glossary";
    private static final String TERMINOLOGY = "terminology";
    private static final String LANGUAGE = "language";
    private static final String OMISSION_KIND = "omission";

    private final MessageKey label;
    private final int rank;
    private final String code;
    private final MessageKey why;

    FindingBadge(final MessageKey label, final int rank, final String code, final MessageKey why) {
        this.label = label;
        this.rank = rank;
        this.code = code;
        this.why = why;
    }

    /**
     * The badge's language-neutral code word, which a desk row's plain line starts with.
     *
     * @return the code; never null
     */
    public String code() {
        return code;
    }

    /**
     * The catalogue entry saying in plain words what may be wrong with a segment of this badge.
     *
     * @return the key; never null
     */
    public MessageKey why() {
        return why;
    }

    /**
     * The catalogue entry naming this badge.
     *
     * @return the key; never null
     */
    public MessageKey label() {
        return label;
    }

    /**
     * Chooses the badge of a flagged segment.
     *
     * @param findings the segment's findings; never null
     * @param judgeScore a stored score, or {@code null} — the reviewer gives none, so only a record from before it has one
     * @return the badge of the main finding, {@link #LOW_SCORE} when only a score exists, or empty when the
     *     segment has neither
     */
    public static Optional<FindingBadge> of(final List<QaFinding> findings, final @Nullable Double judgeScore) {
        Objects.requireNonNull(findings, "findings");
        return findings.stream()
                .max(Comparator.comparing(QaFinding::severity).thenComparingInt(finding -> ofKind(finding.kind()).rank))
                .map(finding -> ofKind(finding.kind()))
                .or(() -> Optional.ofNullable(judgeScore).map(score -> LOW_SCORE));
    }

    private static FindingBadge ofKind(final String kind) {
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case GLOSSARY, TERMINOLOGY -> NAME;
            case LANGUAGE -> WRONG_LANGUAGE;
            case OMISSION_KIND -> OMISSION;
            default -> LOW_SCORE;
        };
    }
}
