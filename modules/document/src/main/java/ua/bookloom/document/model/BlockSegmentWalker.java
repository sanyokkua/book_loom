package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.util.hash.HashUtil;

/**
 * Walks a parsed unit in document order and emits one {@link Segment} per line-break-delimited run of every
 * segment-bearing block — seam F1's segment producer for both tree-shaped formats, through {@link TreeNode}.
 *
 * <p><strong>What makes a block (ADR-0027).</strong> An element is segment-bearing when it owns direct
 * non-whitespace text; an element that owns none is descended into rather than segmented. The tag name decides
 * only the kind ({@link SegmentKinds}). This replaced a {@code p}/{@code h1}–{@code h6}/{@code li} whitelist that
 * reached 73.74% of a 194-book corpus's text and left 42 of those books importing essentially empty, because whole
 * publishing toolchains write every paragraph as {@code <div class="paragraph">}.
 *
 * <p><strong>Why "descend while an element owns no direct text" rather than literal innermost.</strong> Both
 * readings of the requirement agree on a wrapper — {@code <div class="wrap"><div>Prose.</div></div>} segments the
 * inner div either way. They disagree on {@code <p>Hello <em>world</em>.</p>}, where a literal innermost rule
 * would segment the {@code <em>}; FR-DOC-03 requires a translation of {@code Привіт <em>світ</em>.} to land on
 * the {@code <p>}, so the block is the outermost element in each descent that owns text of its own. A paragraph
 * wrapped {@code <p><span><i>…</i></span></p>} — the shape one corpus book uses for 7,159 of 7,160 paragraphs —
 * owns no direct text at any level above the {@code <i>}, so that is where its segment lands, exactly as the
 * wrapper rule says.
 *
 * <p>Exclusions are checked before the text test, never after: {@code <pre>} (with its subtree) and a block-level
 * {@code <math>} yield no segment however much text they own (DD-49). The structural rule widens what counts as a
 * block, so an exclusion checked second would turn every {@code <pre>} into a segment.
 *
 * <p>Every segment this change produces is {@link SegmentStatus#PENDING}, unmasked — {@code masked} equals
 * {@code sourceInner} and {@code placeholders} is empty — and never translated.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockSegmentWalker {

    private static final double INITIAL_CONFIDENCE = 0.0;

    /** Blocks whose text is never translatable, whatever they contain (DD-49). */
    private static final Set<String> EXCLUDED_TAGS = Set.of("pre", "math");

    /**
     * Walks {@code root} in document order and emits one segment per translatable run.
     *
     * @param root the unit's walk root — an EPUB spine document's {@code <body>} or an FB2 {@code <body>}
     * @param unitId the owning unit's id, used as each segment's id prefix ({@code {unitId}:{ordinal}})
     * @return the unit's segments in document order; empty if it has no translatable block
     */
    public static List<Segment> walk(TreeNode root, String unitId) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(unitId, "unitId");
        final List<Draft> drafts = new ArrayList<>();
        collect(root, new ArrayList<>(), new ArrayList<>(), drafts);
        return finalizeSegments(drafts, unitId);
    }

    /**
     * Descends {@code parent}'s element children, maintaining the root-to-block element-sibling path and the
     * enclosing tag names. Non-element children are stepped over rather than descended into: they carry no
     * position in the path and cannot contain a block.
     */
    private static void collect(TreeNode parent, List<Integer> path, List<String> ancestors, List<Draft> drafts) {
        int elementIndex = 0;
        for (final TreeNode child : parent.childNodes()) {
            final String tag = child.tagName();
            if (tag == null) {
                continue;
            }
            final int index = elementIndex;
            elementIndex++;
            if (EXCLUDED_TAGS.contains(tag)) {
                continue;
            }
            path.add(index);
            descendOrEmit(child, tag, path, ancestors, drafts);
            path.removeLast();
        }
    }

    private static void descendOrEmit(
            TreeNode block, String tag, List<Integer> path, List<String> ancestors, List<Draft> drafts) {
        if (ownsDirectText(block)) {
            emitRuns(block, SegmentKinds.of(tag, ancestors), path, drafts);
            return;
        }
        ancestors.add(tag);
        collect(block, path, ancestors, drafts);
        ancestors.removeLast();
    }

    /** Whether {@code element} carries non-whitespace character data as its own child, not a descendant's. */
    private static boolean ownsDirectText(TreeNode element) {
        for (final TreeNode child : element.childNodes()) {
            if (!child.ownText().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emits one draft per run of {@code block} that carries translatable text. A run holding only markup — the
     * empty range between two adjacent {@code <br/>}, or an image-only stretch — yields nothing, while still
     * consuming its run index so that a later run's recorded index is the one reassembly will resolve.
     */
    private static void emitRuns(TreeNode block, SegmentKind kind, List<Integer> path, List<Draft> drafts) {
        final List<TreeNode> children = block.childNodes();
        for (final BlockRuns.Run run : BlockRuns.split(block)) {
            final List<TreeNode> runNodes = children.subList(run.fromInclusive(), run.toExclusive());
            if (visibleText(runNodes).isBlank()) {
                continue;
            }
            drafts.add(new Draft(kind, List.copyOf(path), run.index(), markupOf(runNodes)));
        }
    }

    private static String markupOf(List<TreeNode> nodes) {
        final StringBuilder markup = new StringBuilder();
        for (final TreeNode node : nodes) {
            markup.append(node.markup());
        }
        return markup.toString();
    }

    /** All character data under {@code nodes}, at any depth — what "empty once markup is disregarded" means. */
    private static String visibleText(List<TreeNode> nodes) {
        final StringBuilder text = new StringBuilder();
        appendVisibleText(nodes, text);
        return text.toString();
    }

    private static void appendVisibleText(List<TreeNode> nodes, StringBuilder text) {
        for (final TreeNode node : nodes) {
            text.append(node.ownText());
            appendVisibleText(node.childNodes(), text);
        }
    }

    private static List<Segment> finalizeSegments(List<Draft> drafts, String unitId) {
        final List<Segment> segments = new ArrayList<>(drafts.size());
        for (int order = 0; order < drafts.size(); order++) {
            segments.add(toSegment(drafts, unitId, order));
        }
        return segments;
    }

    private static Segment toSegment(List<Draft> drafts, String unitId, int order) {
        final Draft draft = drafts.get(order);
        final String id = unitId + ":" + order;
        final String prevKey = order > 0 ? unitId + ":" + (order - 1) : null;
        final String nextKey = order < drafts.size() - 1 ? unitId + ":" + (order + 1) : null;
        final String sourceInner = draft.sourceInner();
        return new Segment(
                id,
                unitId,
                order,
                draft.kind(),
                sourceInner,
                sourceInner,
                Map.of(),
                HashUtil.sha256OfNfcText(sourceInner),
                prevKey,
                nextKey,
                new NodeAnchor(draft.nodePath(), draft.runIndex()),
                null,
                SegmentStatus.PENDING,
                INITIAL_CONFIDENCE);
    }

    private record Draft(SegmentKind kind, List<Integer> nodePath, int runIndex, String sourceInner) {}
}
