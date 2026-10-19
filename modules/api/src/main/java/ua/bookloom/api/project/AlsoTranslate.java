package ua.bookloom.api.project;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ua.bookloom.api.document.SegmentKind;

/**
 * The Book Brief's switches for auxiliary text, each turning translation of one auxiliary-unit category on or off
 * ({@code specs/book-brief/spec.md} "Choose which auxiliary text is also translated").
 *
 * <p>Only records of the auxiliary unit consult this record — a body {@link SegmentKind#TITLE} (a running-head or
 * title-page heading) is always translated, regardless of these switches.
 *
 * @param navigationLabels whether nav/NCX table-of-contents labels and page titles are translated
 * @param altText whether image alt text is translated
 * @param metadata whether the book's title, author and description are translated
 * @param frontmatter whether Markdown frontmatter values are translated
 */
public record AlsoTranslate(boolean navigationLabels, boolean altText, boolean metadata, boolean frontmatter) {

    private static final List<SegmentKind> AUXILIARY_ONLY_KINDS = List.of(
            SegmentKind.NAV_LABEL,
            SegmentKind.TITLE,
            SegmentKind.ALT,
            SegmentKind.METADATA_TITLE,
            SegmentKind.METADATA_AUTHOR,
            SegmentKind.METADATA_DESCRIPTION,
            SegmentKind.FRONTMATTER_VALUE);

    /**
     * Returns the switches a newly opened book starts with — navigation and alt text and metadata on, frontmatter
     * off.
     *
     * @return the default switches
     */
    public static AlsoTranslate defaults() {
        return new AlsoTranslate(true, true, true, false);
    }

    /**
     * Reports whether a run translates an auxiliary segment of the given kind.
     *
     * @param auxiliaryKind the non-null kind of an auxiliary-unit segment
     * @return {@code true} if this run translates that kind, {@code false} otherwise
     */
    public boolean covers(final SegmentKind auxiliaryKind) {
        return switch (auxiliaryKind) {
            case NAV_LABEL, TITLE -> navigationLabels;
            case ALT -> altText;
            case METADATA_TITLE, METADATA_AUTHOR, METADATA_DESCRIPTION -> metadata;
            case FRONTMATTER_VALUE -> frontmatter;
            default -> true;
        };
    }

    /**
     * Returns the auxiliary kinds this run keeps as source rather than translating.
     *
     * @return an unmodifiable copy of the auxiliary kinds {@link #covers} answers false for
     */
    public Set<SegmentKind> keptKinds() {
        return AUXILIARY_ONLY_KINDS.stream().filter(kind -> !covers(kind)).collect(Collectors.toUnmodifiableSet());
    }
}
