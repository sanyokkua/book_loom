package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import ua.bookloom.api.document.SegmentKind;

/**
 * Verifies that a restored Markdown segment keeps its source's structure — the requirement <em>Verify that a
 * restored Markdown segment keeps its structure</em> (ADR-0031 D6). Run only on the restore path, after
 * {@link MarkdownEscaper} and before the restored content is handed back; the zero-edit write produces no target
 * text at all, so nothing here ever runs against it.
 *
 * <p>Escaping a stray delimiter cannot help this failure mode, because nothing the model wrote is wrong — a
 * translation that legitimately moves or spaces out a restored fragment's delimiters (a translated {@code ⟦g0⟧
 * старі ⟦g1⟧} restoring to {@code * старі *}) changes what the <em>source's own</em> delimiters now mean. So both
 * the segment's source text and the restored text are parsed the same way, each standalone, and compared as a
 * multiset of construct types — a multiset, because translation legitimately reorders constructs within a
 * segment; standalone on both sides, because parsing the source in its surrounding document context (as it was
 * originally segmented) would resolve a reference link the restored side, parsed alone, cannot — failing the
 * identity restore of any segment holding one.
 *
 * <p><strong>Why a {@link SegmentKind#HEADING}/{@link SegmentKind#TABLE_CELL} segment disregards block
 * constructs.</strong> A segment's own text is only ever the content <em>inside</em> a block marker the skeleton
 * owns — a heading's text after its {@code #} marker, a table cell's text between its pipes. Parsed standalone,
 * that text can itself look like a different block: {@code ## 1. Alpha beta gamma} segments to
 * {@code 1. Alpha beta gamma}, which alone parses as an {@code OrderedList}/{@code ListItem}/{@code Paragraph} —
 * a block structure that is an artifact of parsing the fragment in isolation, not a property of the document,
 * since the heading owns no list. Counting it would make any translation that does not keep the numeral at
 * position zero an unrepairable failure (escaping only removes a construct the model <em>added</em>; it cannot
 * restore one the standalone parse invented), and measured against the 213-book corpus 54 of 2,065 Markdown
 * segments across four books are in exactly this position: 43 headings, 11 table cells. So for these two kinds
 * only, both sides of the comparison disregard block construct types, leaving the inline comparison every other
 * kind already gets. A paragraph keeps the full comparison — its content is not owned by an outer block marker,
 * so a restored paragraph that becomes a bullet list is still, correctly, a mismatch.
 *
 * <p><strong>The carve-out is one-directional, and {@link #escapesItsBlock} is what makes it so.</strong> Losing an
 * invented block construct is an artifact; <em>gaining</em> one is a corruption, and the multiset comparison alone
 * cannot tell them apart — {@link #collectConstructNames} already drops {@code Text} and {@code SoftLineBreak}, so
 * a heading whose translation adds a whole extra block reduces to {@code [Paragraph, Paragraph]} against
 * {@code [Paragraph]}, and stripping block types from both sides leaves {@code []} equal to {@code []}. Measured:
 * a HEADING segment restored to {@code Заголовок\n\nsecond paragraph} was accepted verbatim and written back as a
 * heading <em>plus a new paragraph</em>, and a TABLE_CELL restored to {@code Комірка | друга} gave its row an
 * extra column, so the table stopped parsing as a table and four translatable cells collapsed to one paragraph.
 * The honest rule is containment: content owned by an outer block marker may lose block constructs the standalone
 * parse invented, but must not gain a character that terminates the enclosing block.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkdownStructureCheck {

    /**
     * Simple class names of block-level constructs disregarded for a {@link SegmentKind#HEADING} or
     * {@link SegmentKind#TABLE_CELL} segment — every construct a standalone parse of inline-only content can
     * spuriously invent, plus the GFM table extension's block/head/body/row wrapper types.
     *
     * <p>{@code TableCell} is deliberately absent, but not because a cell's own content is special — a
     * {@code TableCell} <em>node</em> is the wrapper, never the content, exactly like the four wrapper types that
     * are stripped. Constructs nested inside a cell survive for a different reason entirely: they are not in this
     * set at all. The cell wrapper stays out because a table can only be invented whole, marker row and all;
     * measured, a restored 2x2 table leaves four {@code TableCell}s that reject the segment, and that rejection is
     * the accepted residual.
     *
     * <p>{@code Document} is absent for the same reason {@link MarkdownEscaper}'s container-only test omits it: the
     * walk in {@link #collectConstructNames} starts at the parse root and only ever adds children, so the root's
     * own name can never enter the multiset, and an entry for it would be a guard over a state the call chain has
     * already made impossible.
     */
    private static final Set<String> BLOCK_CONSTRUCTS = Set.of(
            "Paragraph",
            "Heading",
            "BulletList",
            "OrderedList",
            "ListItem",
            "BlockQuote",
            "ThematicBreak",
            "FencedCodeBlock",
            "IndentedCodeBlock",
            "HtmlBlock",
            "LinkReferenceDefinition",
            "TableBlock",
            "TableHead",
            "TableBody",
            "TableRow");

    /** The two line terminators CommonMark recognises, counted individually by {@link #lineTerminatorCount}. */
    private static final char LINE_FEED = '\n';

    private static final char CARRIAGE_RETURN = '\r';

    /** The GFM table extension's column separator — the character a restored cell must not gain unescaped. */
    private static final char PIPE = '|';

    private static final char BACKSLASH = '\\';

    /**
     * Compares {@code sourceText} and {@code restoredText}'s construct-type multisets, each parsed on its own.
     *
     * @param sourceText the segment's own source text ({@code sourceInner}), never the segment's masked form —
     *     which carries {@code ⟦gN⟧} tokens that would themselves parse as literal text and skew the comparison;
     *     never null
     * @param restoredText the escaped, restored candidate text; never null
     * @param kind the segment's kind, which selects whether block constructs are disregarded on both sides — see
     *     this class's Javadoc; never null
     * @return {@code true} if the two multisets are equal, {@code false} otherwise
     */
    public static boolean matches(String sourceText, String restoredText, SegmentKind kind) {
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(restoredText, "restoredText");
        Objects.requireNonNull(kind, "kind");
        if (escapesItsBlock(sourceText, restoredText, kind)) {
            return false;
        }
        return multisetFor(sourceText, kind).equals(multisetFor(restoredText, kind));
    }

    /**
     * Whether {@code restoredText} would break out of the block marker the skeleton owns — the containment half of
     * the {@link SegmentKind#HEADING}/{@link SegmentKind#TABLE_CELL} carve-out, run before the multiset comparison
     * because the comparison provably cannot see either case (see this class's Javadoc).
     *
     * <p>Both kinds are single-line by construction — a heading ends at its own line, and a table row ends at
     * hers — so a restored text carrying more line terminators than its source has gained a block terminator. A
     * cell additionally ends at every unescaped {@code |}: one more of those than the source had is one more
     * column than the row has, which stops the whole table parsing as a table. A {@code \|} the model wrote itself
     * is properly escaped and stays acceptable; a bare one is a validation failure, because a pipe forms no block
     * construct for the escaper to see and no construct multiset can ever notice it.
     */
    private static boolean escapesItsBlock(String sourceText, String restoredText, SegmentKind kind) {
        if (kind != SegmentKind.HEADING && kind != SegmentKind.TABLE_CELL) {
            return false;
        }
        if (lineTerminatorCount(restoredText) > lineTerminatorCount(sourceText)) {
            return true;
        }
        return kind == SegmentKind.TABLE_CELL && unescapedPipeCount(restoredText) > unescapedPipeCount(sourceText);
    }

    /**
     * Counts {@code \n} and {@code \r} individually rather than resolving {@code \r\n} to one break: a heading's
     * and a cell's content holds none of either, so any excess at all is the signal, and counting conservatively
     * can only reject a translation, never accept a corrupting one.
     */
    private static int lineTerminatorCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == LINE_FEED || c == CARRIAGE_RETURN) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts pipes that would actually end a table cell — a pipe preceded by an <em>odd</em> number of backslashes
     * is escaped, and one preceded by an even number (including none) is not, so {@code \\|} counts and {@code \|}
     * does not.
     */
    private static int unescapedPipeCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == PIPE && precedingBackslashes(text, i) % 2 == 0) {
                count++;
            }
        }
        return count;
    }

    private static int precedingBackslashes(String text, int index) {
        int backslashes = 0;
        while (index - backslashes - 1 >= 0 && text.charAt(index - backslashes - 1) == BACKSLASH) {
            backslashes++;
        }
        return backslashes;
    }

    private static List<String> multisetFor(String text, SegmentKind kind) {
        final List<String> names = constructMultiset(text);
        final List<String> kept = new ArrayList<>(names.size());
        for (final String name : names) {
            if (!isDisregarded(name, kind)) {
                kept.add(name);
            }
        }
        return kept;
    }

    /**
     * Whether a construct of type {@code constructName} is disregarded on both sides of the comparison for a
     * segment of {@code kind} — the block-construct carve-out this class's Javadoc explains.
     *
     * <p>Package-private, not private: {@link MarkdownEscaper} must not spend an escape on a construct this class
     * is about to ignore, or it writes a backslash the book did not need (measured: a HEADING translated to
     * {@code 1. Альфа бета} was written as {@code # 1\. Альфа бета}). It asks this method rather than keeping its
     * own copy of the rule, for the same reason {@link #constructMultiset} is shared — two copies can drift, and
     * one of them would be wrong.
     *
     * @param constructName a node's simple class name, as {@link #constructMultiset} produces it
     * @param kind the segment's kind; only {@link SegmentKind#HEADING} and {@link SegmentKind#TABLE_CELL}
     *     disregard anything
     * @return {@code true} if the construct is ignored for this kind, {@code false} if it is compared
     */
    static boolean isDisregarded(String constructName, SegmentKind kind) {
        return (kind == SegmentKind.HEADING || kind == SegmentKind.TABLE_CELL)
                && BLOCK_CONSTRUCTS.contains(constructName);
    }

    /**
     * The sorted list of every node's simple class name in {@code text}'s parse tree, except {@code Text} and
     * {@code SoftLineBreak} — disregarded because rendering two source lines as one is legal Markdown and
     * routine in translation. Commonmark's {@code Document} needs no exclusion: it is only ever the parse root,
     * and this walk only ever visits children. A hard line break is deliberately not excluded: its
     * two-trailing-spaces spelling carries no source span of its own, so the masker derives its range from the
     * preceding text run rather than from the node, and counting the construct here is what catches its loss.
     * (The backslash spelling does carry a span and is masked directly — the earlier wording, that a hard line
     * break "cannot be masked", was true of neither spelling.)
     *
     * <p>Package-private, not private: {@link MarkdownEscaper} needs the segment's own source text reduced to this
     * exact multiset to tell a construct the source legitimately owns from one only the model introduced — the same
     * computation this class already performs for the post-escape check, never a second, independently-maintained
     * copy that could drift from it.
     */
    static List<String> constructMultiset(String text) {
        final Node root = MarkdownReader.parser().parse(text);
        final List<String> names = new ArrayList<>();
        collectConstructNames(root, names);
        Collections.sort(names);
        return names;
    }

    private static void collectConstructNames(Node node, List<String> names) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (!(child instanceof Text) && !(child instanceof SoftLineBreak)) {
                names.add(child.getClass().getSimpleName());
            }
            collectConstructNames(child, names);
        }
    }
}
