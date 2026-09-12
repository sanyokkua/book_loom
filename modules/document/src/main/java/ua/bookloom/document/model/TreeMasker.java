package ua.bookloom.document.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.document.mask.MaskWriter;
import ua.bookloom.document.mask.MaskedContent;

/**
 * Masks a run of {@link TreeNode}s — the shared masker for both tree-shaped formats, EPUB (jsoup) and FB2
 * (JDOM2), exactly as {@link BlockSegmentWalker} shares their block recognition (ADR-0027) and {@link BlockRuns}
 * shares their run splitting.
 *
 * <p><strong>Why this lives here rather than in {@code ua.bookloom.document.mask} (a deliberate deviation from
 * design.md D10).</strong> A masker in {@code .mask} that walks {@link TreeNode} would make {@code .model} and
 * {@code .mask} mutually dependent: {@code .model} already depends on {@code .mask} for the accumulator, and
 * {@link BlockSegmentWalker} must call this masker to build a segment, so moving it would close the cycle. That
 * reason alone is sufficient. It is <em>not</em> because this class touches a parser — it imports none, which is
 * the whole point of the {@link TreeNode} adapter. This mirrors the split the module already has, where
 * {@link BlockSegmentWalker} lives in {@code .model} and Markdown's walker lives in {@code .md}.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TreeMasker {

    /**
     * Named-atomic in <strong>both</strong> dialects: their interior never reaches the model, whatever it contains
     * (DD-49, EC-IMG-1).
     *
     * <p>MathML and SVG are here rather than in the XHTML-only set below because FictionBook declaring neither is
     * a reason they are <em>foreign content that must stay atomic</em>, not a reason to descend into them.
     * Measured before this was widened: an FB2 paragraph carrying an inline
     * {@code <m:math><m:mi>alpha</m:mi></m:math>} masked to {@code Formula ⟦g0⟧⟦g1⟧alpha⟦g2⟧⟦g3⟧ here.}, handing
     * the mathematical identifier {@code alpha} — and an inline SVG's drawn caption — to the model as translatable
     * prose. {@link BlockSegmentWalker}'s own exclusion set already treats {@code math} format-agnostically, so
     * the narrower scoping also disagreed with itself: a <em>block-level</em> FB2 {@code <math>} was protected
     * while an <em>inline</em> one was not.
     *
     * <p>{@code pre} and {@code listing} are the two preformatted block elements
     * {@link #LINE_FEED_DISCARDING_ELEMENTS} names; masking them atomically is what keeps a nested listing from
     * reaching the model as raw markup.
     */
    private static final Set<String> ATOMIC_ELEMENTS = Set.of("math", "svg", "pre", "listing");

    /**
     * Named-atomic in XHTML only. FictionBook uses {@code code} as an ordinary prose style — a paragraph written
     * entirely in it is real text a reader reads — so masking it atomically there would silently drop real content
     * from translation (D11, the same carve-out {@link BlockSegmentWalker} makes at block level).
     */
    private static final Set<String> XHTML_ONLY_ATOMIC_ELEMENTS = Set.of("code");

    /**
     * The elements jsoup's HTML parser discards one leading line feed after, measured on jsoup 1.23.1 —
     * deliberately <em>not</em> {@code textarea}, which the HTML serialization spec lists alongside these two but
     * which jsoup does not in fact strip anything from (see {@code PreformattedLineFeedRestorer}'s own Javadoc for
     * the measurement).
     */
    private static final Set<String> LINE_FEED_DISCARDING_ELEMENTS = Set.of("pre", "listing");

    /**
     * Masks {@code runNodes} — one segment's run, in document order.
     *
     * @param runNodes the run's nodes, in document order; never null
     * @param dialect which tree-shaped format {@code runNodes} was parsed from; never null
     * @return the masked content; never null
     * @throws ua.bookloom.document.mask.MaskInvariantException if the mask-time invariant fails
     */
    public static MaskedContent mask(List<TreeNode> runNodes, TreeDialect dialect) {
        Objects.requireNonNull(runNodes, "runNodes");
        Objects.requireNonNull(dialect, "dialect");
        final MaskWriter writer = new MaskWriter();
        maskNodes(runNodes, dialect, writer);
        return writer.build();
    }

    private static void maskNodes(List<TreeNode> nodes, TreeDialect dialect, MaskWriter writer) {
        for (final TreeNode node : nodes) {
            maskNode(node, dialect, writer);
        }
    }

    /**
     * Classifies {@code node} by type first, then name, then child count (ADR-0031, design.md D2) — in that
     * order and no other, because both adapters return {@code List.of()} from {@link TreeNode#childNodes()} for
     * every non-element, so testing child count before type would route a comment, a CDATA section, or a text
     * node into the "element with no children" arm and emit an illegal closing tag for one of them.
     */
    private static void maskNode(TreeNode node, TreeDialect dialect, MaskWriter writer) {
        switch (node.type()) {
            case CDATA -> writer.appendAtomic(node.markup());
            case TEXT -> writer.appendCharacterData(node.ownText());
            case OTHER -> writer.appendAtomic(node.markup()); // comment, processing instruction, entity reference
            case ELEMENT -> maskElement(node, dialect, writer);
        }
    }

    private static void maskElement(TreeNode element, TreeDialect dialect, MaskWriter writer) {
        if (isNamedAtomic(element, dialect)) {
            writer.appendAtomic(atomicFragment(element, dialect));
            return;
        }
        if (element.childNodes().isEmpty()) {
            writer.appendAtomic(element.markup());
            return;
        }
        writer.appendAtomic(element.openMarkup());
        maskNodes(element.childNodes(), dialect, writer);
        writer.appendAtomic(element.closeMarkup());
    }

    /**
     * The character data {@code nodes} would actually hand a model: every text node and CDATA section they carry,
     * <strong>except</strong> what sits inside a named-atomic element, whose interior is replaced wholesale by one
     * token and so can never be translated.
     *
     * <p>Owned here rather than by {@link BlockSegmentWalker}, which is the only caller, because the answer depends
     * on the named-atomic sets above — a second walk that decided atomicity for itself would drift from the masker
     * silently, which is the failure mode ADR-0027 already recorded once for the block rule.
     *
     * @param nodes the run's nodes, in document order; never null
     * @param dialect which tree-shaped format {@code nodes} was parsed from; never null
     * @return the translatable character data, concatenated in document order; never null, empty if there is none
     */
    static String translatableText(List<TreeNode> nodes, TreeDialect dialect) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(dialect, "dialect");
        final StringBuilder text = new StringBuilder();
        appendTranslatableText(nodes, dialect, text);
        return text.toString();
    }

    private static void appendTranslatableText(List<TreeNode> nodes, TreeDialect dialect, StringBuilder text) {
        for (final TreeNode node : nodes) {
            text.append(node.ownText());
            if (!isNamedAtomic(node, dialect)) {
                appendTranslatableText(node.childNodes(), dialect, text);
            }
        }
    }

    private static boolean isNamedAtomic(TreeNode node, TreeDialect dialect) {
        if (node.type() != TreeNodeType.ELEMENT) {
            return false;
        }
        // Non-null by construction: every adapter guarantees tagName() is non-null exactly for an element.
        final String tag = localNameOf(Objects.requireNonNull(node.tagName(), "tag"));
        return ATOMIC_ELEMENTS.contains(tag)
                || (dialect == TreeDialect.XHTML && XHTML_ONLY_ATOMIC_ELEMENTS.contains(tag));
    }

    /**
     * A named-atomic element's captured fragment — its markup, except that in XHTML every preformatted element
     * <strong>anywhere in the subtree</strong> gets back the one line feed jsoup's HTML parser discarded after its
     * start tag (task 4.2).
     *
     * <p>The reinsertion has to be subtree-wide, not just about {@code element} itself. A captured fragment is
     * re-parsed a second time when it is restored, so each preformatted element inside it loses a second line feed
     * unless the capture puts one back. Measured before this was widened, a {@code <pre>} holding two leading line
     * feeds and nested inside an atomic {@code <code>} — or inside another {@code <pre>} — came back from an
     * identity round trip with <em>both</em> of them gone, because the outer element's arm captured raw markup and
     * the preformatted arm was never reached. That deletes a line from inside a listing or verse block on every
     * export.
     *
     * <p>JDOM2's XML parser discards nothing, so applying this to {@link TreeDialect#FICTION_BOOK} would add a line
     * on every cycle instead.
     */
    private static String atomicFragment(TreeNode element, TreeDialect dialect) {
        if (dialect != TreeDialect.XHTML || !hasDiscardedLineFeed(element)) {
            return element.markup();
        }
        return composeWithRestoredLineFeeds(element);
    }

    /** Whether {@code node}'s subtree — {@code node} itself included — holds an element that lost a line feed. */
    private static boolean hasDiscardedLineFeed(TreeNode node) {
        if (node.type() != TreeNodeType.ELEMENT) {
            return false;
        }
        if (discardedALineFeed(node)) {
            return true;
        }
        for (final TreeNode child : node.childNodes()) {
            if (hasDiscardedLineFeed(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean discardedALineFeed(TreeNode element) {
        final String tag = localNameOf(Objects.requireNonNull(element.tagName(), "tag"));
        return LINE_FEED_DISCARDING_ELEMENTS.contains(tag) && beginsWithLineFeed(element);
    }

    /**
     * Recomposes only the ancestor chain down to each affected element; every unaffected subtree is taken as its
     * own verbatim {@link TreeNode#markup()}, which is what keeps a void element such as {@code <img/>} serializing
     * as the parser writes it rather than as an {@code openMarkup() + closeMarkup()} pair it never had.
     *
     * <p>Composed from the element's own parts rather than by slicing markup() at openMarkup()'s length. Measured
     * on jsoup 1.23.1: outerHtml() serializes with the document's pinned settings (XML syntax) while
     * attributes().html() is hard-coded to a fresh default OutputSettings (HTML syntax), so the two disagree
     * whenever the difference is observable — {@code <pre hidden>} composes as {@code <pre hidden>} but serializes
     * as {@code <pre hidden="">}, and {@code <pre 2col="x">} composes with the attribute while XML-syntax
     * serialization drops it, leaving openMarkup() <em>longer</em> than markup() and the slice throwing rather than
     * merely mis-cutting.
     */
    private static String composeWithRestoredLineFeeds(TreeNode node) {
        if (!hasDiscardedLineFeed(node)) {
            return node.markup();
        }
        final StringBuilder inner = new StringBuilder();
        for (final TreeNode child : node.childNodes()) {
            inner.append(composeWithRestoredLineFeeds(child));
        }
        final String restored = discardedALineFeed(node) ? "\n" : "";
        return node.openMarkup() + restored + inner + node.closeMarkup();
    }

    /**
     * The element name without its namespace prefix.
     *
     * <p>Load-bearing for the named-atomic sets: measured, jsoup reports {@code tagName()} as {@code m:math} for
     * {@code <m:math>} and {@code svg:svg} for {@code <svg:svg>}, both of which real EPUB2 and DAISY-derived books
     * write. Matching the qualified name would let a prefixed MathML or SVG element fall through to the paired arm,
     * so the walk would descend into it and hand a mathematical identifier — or a figure's drawn caption — to the
     * model as translatable prose, which is exactly what naming those elements exists to prevent.
     *
     * @param tagName the element name as its parser records it; never null
     * @return the portion after the last colon, or {@code tagName} unchanged when it carries no prefix
     */
    private static String localNameOf(String tagName) {
        final int colon = tagName.lastIndexOf(':');
        return colon < 0 ? tagName : tagName.substring(colon + 1);
    }

    private static boolean beginsWithLineFeed(TreeNode element) {
        final List<TreeNode> children = element.childNodes();
        return !children.isEmpty()
                && children.get(0).type() == TreeNodeType.TEXT
                && children.get(0).ownText().startsWith("\n");
    }
}
