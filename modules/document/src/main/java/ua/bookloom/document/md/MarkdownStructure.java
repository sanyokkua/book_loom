package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.StructureNode;

/**
 * Builds a Markdown book's structure tree from its heading segments and their commonmark levels (task 4.4): text
 * before the first heading is an untitled leading node, and each heading's own node counts every segment from
 * itself to the next heading of the same or a shallower level.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownStructure {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("⟦g\\d+⟧");

    /**
     * Builds the top-level structure nodes.
     *
     * @param segments the unit's segments, in document order
     * @param headingLevels every {@code Heading} node's commonmark level, in document order — matched positionally
     *     to this unit's {@code HEADING}-kind segments
     * @return the top-level nodes, in document order
     */
    static List<StructureNode> build(List<Segment> segments, List<Integer> headingLevels) {
        final List<Boundary> boundaries = boundariesOf(segments, headingLevels);
        final List<StructureNode> topLevel = new ArrayList<>();
        final int firstHeadingIndex =
                boundaries.isEmpty() ? segments.size() : boundaries.get(0).segmentIndex();
        if (firstHeadingIndex > 0) {
            topLevel.add(new StructureNode("", null, firstHeadingIndex, List.of()));
        }
        topLevel.addAll(nest(boundaries, countsOf(boundaries, segments.size()), 0, boundaries.size()));
        return topLevel;
    }

    private static List<Boundary> boundariesOf(List<Segment> segments, List<Integer> headingLevels) {
        final List<Boundary> boundaries = new ArrayList<>();
        int levelIndex = 0;
        for (int i = 0; i < segments.size() && levelIndex < headingLevels.size(); i++) {
            final Segment segment = segments.get(i);
            if (segment.kind() == SegmentKind.HEADING) {
                boundaries.add(new Boundary(i, headingLevels.get(levelIndex), titleOf(segment)));
                levelIndex++;
            }
        }
        return boundaries;
    }

    private static String titleOf(Segment segment) {
        return PLACEHOLDER_TOKEN.matcher(segment.sourceInner()).replaceAll("").strip();
    }

    private static int[] countsOf(List<Boundary> boundaries, int segmentCount) {
        final int[] counts = new int[boundaries.size()];
        for (int i = 0; i < boundaries.size(); i++) {
            counts[i] =
                    endIndexOf(boundaries, i, segmentCount) - boundaries.get(i).segmentIndex();
        }
        return counts;
    }

    private static int endIndexOf(List<Boundary> boundaries, int i, int segmentCount) {
        final int level = boundaries.get(i).level();
        for (int j = i + 1; j < boundaries.size(); j++) {
            if (boundaries.get(j).level() <= level) {
                return boundaries.get(j).segmentIndex();
            }
        }
        return segmentCount;
    }

    /** Nests {@code boundaries[start:end)} by level — the first entry in the range is always its shallowest. */
    private static List<StructureNode> nest(List<Boundary> boundaries, int[] counts, int start, int end) {
        final List<StructureNode> nodes = new ArrayList<>();
        int i = start;
        while (i < end) {
            final int childEnd = nextSiblingIndex(boundaries, i, end);
            final List<StructureNode> children = nest(boundaries, counts, i + 1, childEnd);
            nodes.add(new StructureNode(boundaries.get(i).title(), null, counts[i], children));
            i = childEnd;
        }
        return nodes;
    }

    private static int nextSiblingIndex(List<Boundary> boundaries, int i, int end) {
        final int level = boundaries.get(i).level();
        int j = i + 1;
        while (j < end && boundaries.get(j).level() > level) {
            j++;
        }
        return j;
    }

    private record Boundary(int segmentIndex, int level, String title) {}
}
