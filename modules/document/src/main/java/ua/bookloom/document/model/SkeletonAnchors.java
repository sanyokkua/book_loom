package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p><strong>Why the whole unit's writes are planned before any of them lands.</strong> A run index is resolved
 * by splitting the block on its direct {@code <br/>} children, so it is a function of the block's <em>current</em>
 * content. The placeholder gate is order-insensitive on purpose — a translation legitimately reorders inline
 * markup — so it passes a target that moves a {@code <br/>}-mapped token out of nested inline markup, and that
 * target introduces a direct line break the source block did not have. Measured on
 * {@code <div>a<em>x<br/>y</em>b<br/>SECOND</div>}: writing the first run alone re-splits the block from two runs
 * into three, after which the second segment's recorded {@code runIndex} of 1 addresses the range the first
 * segment's translation was just written into — so the second write deleted the first's translation and left its
 * own source untranslated. {@link #writeBackAll} therefore resolves every block node and every run range from the
 * pre-write tree, then applies each block's writes back-to-front, since replacing a child range can only shift
 * positions at or after its own start.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SkeletonAnchors {

    /**
     * Writes one segment's target into the run its anchor addresses, and touches nothing else in the tree.
     *
     * <p>A single write is a whole plan of one, so this is safe on its own. A caller with several segments to
     * write MUST use {@link #writeBackAll} instead — writing them one call at a time is exactly the defect the
     * class Javadoc describes.
     *
     * @param root the unit's walk root, the same element the walker was given
     * @param anchor the segment's anchor; must be a {@link NodeAnchor}
     * @param targetInner the translated inner content, parsed as markup rather than inserted as literal text
     */
    public static void writeBack(TreeNode root, SkeletonAnchor anchor, String targetInner) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(targetInner, "targetInner");
        writeBackAll(root, List.of(new PendingWrite(anchor, targetInner)));
    }

    /**
     * Writes every supplied segment's target into the run its anchor addresses, resolving all of them against the
     * tree as it stands before the first write.
     *
     * @param root the unit's walk root, the same element the walker was given
     * @param writes one entry per segment whose target is to be written; order is irrelevant, because the order
     *     the writes are actually applied in is chosen here
     */
    public static void writeBackAll(TreeNode root, List<PendingWrite> writes) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(writes, "writes");
        for (final Map.Entry<List<Integer>, List<PendingWrite>> block :
                groupByBlock(writes).entrySet()) {
            writeBlock(resolve(root, block.getKey()), block.getValue());
        }
    }

    /**
     * One segment's pending write.
     *
     * @param anchor the segment's anchor; must be a {@link NodeAnchor}, since a tree has no byte spans
     * @param targetInner the translated inner content, parsed as markup rather than inserted as literal text
     */
    public record PendingWrite(SkeletonAnchor anchor, String targetInner) {

        /** Both components are dereferenced long after construction, so a null is caught at the call site. */
        public PendingWrite {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(targetInner, "targetInner");
        }
    }

    /** Groups by node path, so a block is resolved and split once however many of its runs are being written. */
    private static Map<List<Integer>, List<PendingWrite>> groupByBlock(List<PendingWrite> writes) {
        final Map<List<Integer>, List<PendingWrite>> byBlock = new LinkedHashMap<>();
        for (final PendingWrite write : writes) {
            byBlock.computeIfAbsent(requireNodeAnchor(write.anchor()).nodePath(), path -> new ArrayList<>())
                    .add(write);
        }
        return byBlock;
    }

    /**
     * Applies one block's writes back-to-front over run ranges taken from a single pre-write split.
     *
     * <p>Descending order is what makes those ranges stay true: {@link TreeNode#replaceChildren} can only change
     * child positions at or after {@code fromInclusive}, so every range still waiting its turn lies strictly
     * before the one just written and is untouched by it.
     */
    private static void writeBlock(TreeNode block, List<PendingWrite> writes) {
        final List<BlockRuns.Run> runs = BlockRuns.split(block);
        final List<PlannedWrite> planned = new ArrayList<>(writes.size());
        for (final PendingWrite write : writes) {
            planned.add(new PlannedWrite(
                    runAt(runs, requireNodeAnchor(write.anchor()).runIndex()), write.targetInner()));
        }
        planned.sort(Comparator.comparingInt((PlannedWrite write) -> write.run().fromInclusive())
                .reversed());
        for (final PlannedWrite write : planned) {
            block.replaceChildren(write.run().fromInclusive(), write.run().toExclusive(), write.targetInner());
        }
    }

    /** A write whose target child range is already fixed, awaiting its turn in back-to-front order. */
    private record PlannedWrite(BlockRuns.Run run, String targetInner) {}

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

    private static BlockRuns.Run runAt(List<BlockRuns.Run> runs, int runIndex) {
        if (runIndex >= runs.size()) {
            throw new IllegalStateException("Anchor addresses run " + runIndex + " of a block with " + runs.size());
        }
        return runs.get(runIndex);
    }
}
