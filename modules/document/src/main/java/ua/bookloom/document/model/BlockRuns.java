package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Splits a segment-bearing block's direct children into line-break-delimited runs — the unit ADR-0025 makes a
 * segment address, and the unit reassembly replaces.
 *
 * <p>One surveyed EPUB carries 465,500 characters inside four elements separated by 9,290 {@code <br/>}, and one
 * FB2 holds 1,850,973 characters in two paragraphs separated by 11,945 of them. Treating either block as a single
 * segment produces a segment no model can accept; splitting on the breaks recovers the paragraphs the reader
 * actually sees, while the break elements themselves stay in the skeleton and never move.
 *
 * <p>Only <em>direct</em> children are split on. A {@code <br/>} nested inside inline markup does not open a new
 * run, because a run is a range of the block's own child positions and a nested break has no such position —
 * splitting there would mean a segment's extent could not be expressed as one contiguous child range.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockRuns {

    private static final String LINE_BREAK_TAG = "br";

    /**
     * Splits {@code block}'s direct children into runs at every line-break element.
     *
     * @param block the segment-bearing block to split
     * @return one {@link Run} per inter-break range, in document order; always at least one run, and exactly one
     *     for a block containing no line break
     */
    public static List<Run> split(TreeNode block) {
        Objects.requireNonNull(block, "block");
        final List<TreeNode> children = block.childNodes();
        final List<Run> runs = new ArrayList<>();
        int runStart = 0;
        for (int i = 0; i < children.size(); i++) {
            if (isLineBreak(children.get(i))) {
                runs.add(new Run(runs.size(), runStart, i));
                runStart = i + 1;
            }
        }
        runs.add(new Run(runs.size(), runStart, children.size()));
        return runs;
    }

    private static boolean isLineBreak(TreeNode node) {
        return LINE_BREAK_TAG.equals(node.tagName());
    }

    /**
     * One line-break-delimited range of a block's direct children.
     *
     * @param index this run's position among its block's runs, counting empty runs — a segment's
     *     {@code NodeAnchor.runIndex}. Empty runs are counted rather than skipped so that the index a segment
     *     records at parse time is the same index reassembly resolves later, which is what keeps
     *     {@code <div>Один<br/><br/>Два</div>} writing "Два" into the third run rather than the second.
     * @param fromInclusive the first child index this run covers
     * @param toExclusive one past the last child index this run covers
     */
    public record Run(int index, int fromInclusive, int toExclusive) {

        /** Validates the range; an empty run ({@code from == to}) is legal and common between adjacent breaks. */
        public Run {
            if (index < 0 || fromInclusive < 0 || toExclusive < fromInclusive) {
                throw new IllegalArgumentException(
                        "Malformed run: index=" + index + " [" + fromInclusive + ", " + toExclusive + ")");
            }
        }
    }
}
