package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.mask.MaskedContent;
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
 * block, so an exclusion checked second would turn every {@code <pre>} into a segment. In XHTML only, a block whose
 * sole child is a {@code <code>} span is excluded the same way (D11): the block owns no direct text of its own, so
 * the structural rule would otherwise descend into the span and hand the model a bare identifier. FictionBook uses
 * {@code code} for an ordinary prose style, so the exclusion does not apply there — a code span sitting inside a
 * text-owning block is still in the run and still masked, in both dialects.
 *
 * <p>Every segment this change produces is {@link SegmentStatus#PENDING} and never translated. Its {@code masked}
 * and {@code placeholders} are real, though: each run is masked by {@link TreeMasker} in the same pass that
 * computes {@code sourceInner}, so a segment ships with every protected span already replaced by a {@code ⟦gN⟧}
 * token (ADR-0031).
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockSegmentWalker {

    private static final double INITIAL_CONFIDENCE = 0.0;

    /** Blocks whose text is never translatable, whatever they contain, in every dialect (DD-49). */
    private static final Set<String> EXCLUDED_TAGS = Set.of("pre", "math");

    /**
     * Blocks excluded only in XHTML (D11). FictionBook uses {@code code} as an ordinary prose style — a paragraph
     * written entirely in it is real text a reader reads — so the exclusion cannot be format-agnostic the way
     * {@link #EXCLUDED_TAGS} is.
     */
    private static final Set<String> EXCLUDED_TAGS_XHTML = Set.of("code");

    /**
     * Walks {@code root} in document order and emits one segment per translatable run.
     *
     * @param root the unit's walk root — an EPUB spine document's {@code <body>} or an FB2 {@code <body>}
     * @param unitId the owning unit's id, used as each segment's id prefix ({@code {unitId}:{ordinal}})
     * @param dialect which tree-shaped format {@code root} was parsed from — passed explicitly because the
     *     named-atomic element set and the nested-{@code <pre>} line-feed rule (D2, D11) both differ by format,
     *     and every call site already knows which one it is
     * @return the unit's segments in document order; empty if it has no translatable block
     */
    public static List<Segment> walk(TreeNode root, String unitId, TreeDialect dialect) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(dialect, "dialect");
        final List<Draft> drafts = new ArrayList<>();
        collect(root, new ArrayList<>(), new ArrayList<>(), drafts, dialect);
        return finalizeSegments(drafts, unitId);
    }

    /**
     * Descends {@code parent}'s element children, maintaining the root-to-block element-sibling path and the
     * enclosing tag names. Non-element children are stepped over rather than descended into: they carry no
     * position in the path and cannot contain a block.
     */
    private static void collect(
            TreeNode parent, List<Integer> path, List<String> ancestors, List<Draft> drafts, TreeDialect dialect) {
        int elementIndex = 0;
        for (final TreeNode child : parent.childNodes()) {
            final String tag = child.tagName();
            if (tag == null) {
                continue;
            }
            final int index = elementIndex;
            elementIndex++;
            // Matched on the local name, because a prefixed <m:math> is still block-level MathML: measured, jsoup
            // reports tagName() as `m:math` for the form real EPUB2 and DAISY-derived books write, and matching the
            // qualified name would descend into it and make a mathematical identifier a segment.
            if (isExcluded(localNameOf(tag), dialect)) {
                continue;
            }
            path.add(index);
            descendOrEmit(child, tag, path, ancestors, drafts, dialect);
            path.removeLast();
        }
    }

    private static void descendOrEmit(
            TreeNode block,
            String tag,
            List<Integer> path,
            List<String> ancestors,
            List<Draft> drafts,
            TreeDialect dialect) {
        if (ownsDirectText(block)) {
            emitRuns(block, SegmentKinds.of(tag, ancestors), path, drafts, dialect);
            return;
        }
        ancestors.add(tag);
        collect(block, path, ancestors, drafts, dialect);
        ancestors.removeLast();
    }

    /** Whether {@code localName} is excluded from segmentation for {@code dialect} (D11: {@code code} is XHTML-only). */
    private static boolean isExcluded(String localName, TreeDialect dialect) {
        return EXCLUDED_TAGS.contains(localName)
                || (dialect == TreeDialect.XHTML && EXCLUDED_TAGS_XHTML.contains(localName));
    }

    /** The element name without its namespace prefix — see the exclusion check above for why it is needed. */
    private static String localNameOf(String tagName) {
        final int colon = tagName.lastIndexOf(':');
        return colon < 0 ? tagName : tagName.substring(colon + 1);
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
     *
     * <p>"Translatable" is {@link TreeMasker#translatableText}, not all the character data under the run: a
     * named-atomic span's interior is text a reader sees but the model never does, because masking replaces the
     * whole span with one token. Measured before this was narrowed, {@code <p>Hi<br/><code>x</code></p>} emitted a
     * second segment whose entire masked form was {@code ⟦g0⟧} — a model call that can only hand the token back,
     * and one more chance for the placeholder gate to reject a chunk. D11's code-only exclusion covers the same
     * shape one level up, at the block, and cannot see a run.
     */
    private static void emitRuns(
            TreeNode block, SegmentKind kind, List<Integer> path, List<Draft> drafts, TreeDialect dialect) {
        final List<TreeNode> children = block.childNodes();
        for (final BlockRuns.Run run : BlockRuns.split(block)) {
            final List<TreeNode> runNodes = children.subList(run.fromInclusive(), run.toExclusive());
            if (TreeMasker.translatableText(runNodes, dialect).isBlank()) {
                continue;
            }
            drafts.add(new Draft(
                    kind, List.copyOf(path), run.index(), markupOf(runNodes), TreeMasker.mask(runNodes, dialect)));
        }
    }

    private static String markupOf(List<TreeNode> nodes) {
        final StringBuilder markup = new StringBuilder();
        for (final TreeNode node : nodes) {
            markup.append(node.markup());
        }
        return markup.toString();
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
        final MaskedContent masked = draft.masked();
        return new Segment(
                id,
                unitId,
                order,
                draft.kind(),
                sourceInner,
                masked.masked(),
                masked.placeholders(),
                HashUtil.sha256OfNfcText(sourceInner),
                prevKey,
                nextKey,
                new NodeAnchor(draft.nodePath(), draft.runIndex()),
                null,
                SegmentStatus.PENDING,
                INITIAL_CONFIDENCE);
    }

    private record Draft(
            SegmentKind kind, List<Integer> nodePath, int runIndex, String sourceInner, MaskedContent masked) {}
}
