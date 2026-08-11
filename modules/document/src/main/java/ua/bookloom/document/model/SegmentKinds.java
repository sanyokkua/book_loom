package ua.bookloom.document.model;

import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;

/**
 * Maps a block's tag name to its {@link SegmentKind} — and to nothing else. Under ADR-0027 the tag name no longer
 * decides <em>whether</em> an element is a segment; that is the structural text test in {@link BlockSegmentWalker}.
 * An unrecognised tag therefore degrades to {@link SegmentKind#PARAGRAPH} rather than to silence, which is what
 * makes the structural rule safe against the next converter's tag taste.
 *
 * <p>{@link SegmentKind#FOOTNOTE}, {@link SegmentKind#CAPTION} and {@link SegmentKind#TITLE} are deliberately
 * never produced. Each needs a semantic judgement this structural rule does not make — an FB2 note body is prose
 * in a {@code <p>} like any other, and a caption is an HTML {@code figcaption} only by convention — and emitting a
 * distinct kind buys nothing until something downstream treats it differently, which nothing does yet.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentKinds {

    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6", "subtitle", "title");
    private static final Set<String> TABLE_CELL_TAGS = Set.of("td", "th");
    private static final String LIST_ITEM_TAG = "li";
    private static final String VERSE_LINE_TAG = "v";
    private static final String FB2_SECTION_TITLE_TAG = "title";

    /**
     * Classifies a segment-bearing block.
     *
     * @param tagName the block's lower-cased tag name, or {@code null} for a node that has none
     * @param ancestorTags the lower-cased tag names of the block's ancestors within the walked unit, outermost
     *     first — needed because an FB2 section title holds its text in child {@code <p>} elements, so the
     *     paragraph's own tag cannot tell a heading from body prose
     * @return the segment's kind; {@link SegmentKind#PARAGRAPH} for anything unrecognised
     */
    public static SegmentKind of(@Nullable String tagName, List<String> ancestorTags) {
        if (tagName == null) {
            return SegmentKind.PARAGRAPH;
        }
        if (VERSE_LINE_TAG.equals(tagName)) {
            return SegmentKind.VERSE_LINE;
        }
        if (LIST_ITEM_TAG.equals(tagName)) {
            return SegmentKind.LIST_ITEM;
        }
        if (TABLE_CELL_TAGS.contains(tagName)) {
            return SegmentKind.TABLE_CELL;
        }
        if (HEADING_TAGS.contains(tagName) || ancestorTags.contains(FB2_SECTION_TITLE_TAG)) {
            return SegmentKind.HEADING;
        }
        return SegmentKind.PARAGRAPH;
    }
}
