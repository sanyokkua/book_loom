package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.SkeletonAnchor;

/**
 * Resolves a {@link NodeAnchor} back to its block within a parsed tree and replaces that block's own run with
 * translated content — the write side of ADR-0025.
 *
 * <p><strong>What changed and why.</strong> The shipped implementation resolved an anchor to the block's first
 * non-blank text node and set that node's text, which escapes any restored inline markup into
 * escaped-entity text and leaves the original inline children in place beside it. 23.4% of blocks in a 194-book
 * corpus carry inline markup and eight books exceed 99%, so that was not an edge case. Replacement is now scoped
 * to a <em>run's child range</em>: the translated content is parsed as markup, the run's old children are
 * removed, and nothing else in the tree — no attribute, no comment, no sibling, and in particular no
 * {@code <br/>} bounding the run — is touched.
 *
 * <p>An anchor's node path is relative to the <strong>unit's walk root</strong>, the same element
 * {@link BlockSegmentWalker#walk} was given, and its steps are element-sibling indices. Indexing by element
 * position rather than by raw child-node position is what keeps a path stable across a comment or a bare text
 * node sitting between two block-level siblings.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SkeletonAnchors {

    /**
     * Writes {@code targetInner} into the run {@code anchor} addresses, and touches nothing else in the tree.
     *
     * <p>Because an anchor is a pure index path plus a run index, writing an earlier segment's target text first
     * — even one longer than its source, and even one that changes the number of inline elements — changes no
     * later segment's anchor: a run's content has no bearing on any element's position among its siblings.
     * Callers may therefore write every accepted segment's target in any order before serializing.
     *
     * @param root the unit's walk root, the same element the walker was given
     * @param anchor the segment's anchor; must be a {@link NodeAnchor}
     * @param targetInner the translated inner content, parsed as markup rather than inserted as literal text
     */
    public static void writeBack(TreeNode root, SkeletonAnchor anchor, String targetInner) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(targetInner, "targetInner");
        final NodeAnchor nodeAnchor = requireNodeAnchor(anchor);
        final TreeNode block = resolve(root, nodeAnchor.nodePath());
        final BlockRuns.Run run = runAt(block, nodeAnchor.runIndex());
        block.replaceChildren(run.fromInclusive(), run.toExclusive(), targetInner);
    }

    /**
     * A tree skeleton can only be addressed by a {@link NodeAnchor}; a {@link ByteSpanAnchor} indexes a byte
     * buffer with no tree at all, so reaching here with one is a programming error, not a data error.
     */
    private static NodeAnchor requireNodeAnchor(SkeletonAnchor anchor) {
        return switch (anchor) {
            case NodeAnchor nodeAnchor -> nodeAnchor;
            case ByteSpanAnchor ignored ->
                throw new IllegalArgumentException("A tree skeleton cannot be addressed by a byte span");
        };
    }

    /** Follows an element-sibling path from {@code root}, stepping over text nodes and comments. */
    private static TreeNode resolve(TreeNode root, List<Integer> nodePath) {
        TreeNode current = root;
        for (final int step : nodePath) {
            current = elementChild(current, step);
        }
        return current;
    }

    private static TreeNode elementChild(TreeNode parent, int elementIndex) {
        int seen = 0;
        for (final TreeNode child : parent.childNodes()) {
            if (child.tagName() == null) {
                continue;
            }
            if (seen == elementIndex) {
                return child;
            }
            seen++;
        }
        throw new IllegalStateException("Anchor path does not resolve: no element child at index " + elementIndex);
    }

    private static BlockRuns.Run runAt(TreeNode block, int runIndex) {
        final List<BlockRuns.Run> runs = new ArrayList<>(BlockRuns.split(block));
        if (runIndex >= runs.size()) {
            throw new IllegalStateException("Anchor addresses run " + runIndex + " of a block with " + runs.size());
        }
        return runs.get(runIndex);
    }
}
