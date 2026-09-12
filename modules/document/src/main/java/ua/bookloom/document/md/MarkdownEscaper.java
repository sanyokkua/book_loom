package ua.bookloom.document.md;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Emphasis;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.document.mask.FragmentRange;

/**
 * Backslash-escapes Markdown punctuation the model introduced while restoring a translated segment — the
 * requirement <em>Escape model-introduced Markdown punctuation when restoring</em> (ADR-0031 D6). Run only on the
 * restore path, by {@link ua.bookloom.document.DocumentService#unmask}; the zero-edit write produces no target
 * text at all, so nothing here ever runs against it.
 *
 * <p><strong>Why a per-construct group, not a per-character scan.</strong> A stray {@code *} is only a problem
 * when it forms a construct the segment's own placeholder fragments did not — {@code 5 * 3} is ordinary prose and
 * must stay unescaped, or a translated book fills with backslashes. So each round parses the candidate, finds the
 * first construct (in document order) that (a) has at least one delimiter position outside every restored-fragment
 * range and (b) whose construct type occurs more often in the candidate than in the source, and escapes every such
 * position of that one construct in a single edit — escaping only one of a pair's two delimiters would stop the
 * pair from parsing as a construct at all one character early, silently changing which characters need protecting
 * (measured: escaping only the opening {@code *} of {@code звичайні *слова*} leaves the closing {@code *} no longer
 * a delimiter of anything, and the result is {@code звичайні \*слова*} — one backslash short of the requirement's
 * own scenario).
 *
 * <p><strong>Why the source's own construct multiset, not fragment ranges alone.</strong> A construct outside
 * every fragment range is not automatically model-introduced — a segment can hold a construct the source itself
 * spelled out with no masking at all (a literal {@code 1. What BMAD is} heading restored unchanged parses as an
 * {@code OrderedList}/{@code ListItem} that no fragment range covers, because nothing was masked). Escaping it
 * anyway turns an identity restore into a structure mismatch, which {@link MarkdownStructureCheck} then reports as
 * a corruption that never happened. So a round stops immediately once the candidate's construct multiset already
 * equals the source's, and even when it does not, only a construct type genuinely in excess — one the candidate
 * has more of than the source — is ever escaped; a type the source and candidate hold in equal number is left
 * alone regardless of where its delimiters fall.
 *
 * <p><strong>Why every insertion is funnelled through {@link #appendEscapableGroup}.</strong> A backslash before a
 * non-punctuation character is not a CommonMark escape at all — it renders as a literal backslash, which is a
 * visible corruption the requirement's own "SHALL NOT fail" promise cannot excuse. That check used to live on the
 * generic catch-all branch alone, and each branch added without it repeated the same defect: a setext heading's
 * marker is the underline on the <em>next</em> line, so the marker-led branch derived a position pointing at a
 * letter, wrote a backslash before it, and — because the text still parsed as a setext heading — did it again
 * every round until the budget ran out (measured: twenty backslashes, in 2,330 of 35,290 accepted restores over
 * the real corpus). So the check now sits at the one point where a group becomes an insertion, and every branch
 * inherits it. A construct with no escapable position produces no group and survives untouched into
 * {@link MarkdownStructureCheck}, which reports the mismatch honestly — the recorded residual for an indented code
 * block, whose marker position is an indent space, and now for a setext heading too.
 *
 * <p><strong>Why a hard line break is neutralised by deletion.</strong> There is no backslash spelling that
 * removes a hard break: a trailing backslash <em>is</em> the other hard-break spelling, so inserting one would
 * create a break, not remove one. Its trailing spaces (or its single backslash character) are deleted instead —
 * see {@link HardLineBreakDeletion}.
 *
 * <p>The loop is bounded at {@link #MAX_ESCAPE_ROUNDS}, and that bound is a cap on how many model-introduced
 * constructs one segment may carry, not a convergence safety net: {@link #firstActionableGroup} returns the
 * <em>first</em> actionable group and each round applies only that one, so a round neutralises exactly one
 * construct. Measured, a target carrying twenty-five independent emphasis pairs exhausts the budget with five
 * still unescaped, and {@link MarkdownStructureCheck} then reports the mismatch — the correct outcome, not a
 * failure of the bound.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MarkdownEscaper {

    /** Escape rounds attempted before giving up and leaving the structure check to report a residual mismatch. */
    private static final int MAX_ESCAPE_ROUNDS = 20;

    /**
     * One actionable construct: either the delimiter position(s) {@link #applyGroup} would insert a backslash
     * before ({@code deletion == false}), or the character position(s) it would delete instead ({@code deletion ==
     * true} — the one neutralisation a hard line break needs, since no backslash spelling removes one). The
     * construct's simple class name — the same name {@link MarkdownStructureCheck#constructMultiset} counts — is
     * carried alongside so a group can be checked against the source's own count of that construct type before it
     * is acted on.
     *
     * @param constructName the node's simple class name (e.g. {@code "Emphasis"}, {@code "ListItem"}); never null
     * @param positions the position(s) within the candidate text this group would act on; never null
     * @param deletion {@code true} if {@code positions} are deleted, {@code false} if a backslash is inserted
     *     before each
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: positions are only ever read via the accessor below,
    // never compared for equality — no different from every other int[] this class already passes around.
    private record DelimiterGroup(String constructName, int[] positions, boolean deletion) {}

    /**
     * Escapes every model-introduced construct in {@code candidate}, leaving untouched both a construct formed
     * entirely by a restored placeholder fragment and a construct type the source itself already contains as many
     * of.
     *
     * @param candidate the restored text to escape, before the structure check; never null
     * @param fragmentRanges each restored fragment's range within {@code candidate}
     *     ({@link ua.bookloom.document.mask.RestoredContent#fragmentRanges()}); never null
     * @param sourceText the segment's own source text ({@code sourceInner}), parsed the same way, so a construct
     *     the source legitimately owns is never escaped just because none of its delimiters fall inside a
     *     restored fragment; never null
     * @param kind the segment's kind, so a construct type {@link MarkdownStructureCheck} is about to disregard for
     *     this kind is not escaped at all — spending an edit on it writes a backslash the book did not need;
     *     never null
     * @return the escaped text; never null. It equals {@code candidate} when the construct multiset already
     *     matched {@code sourceText}'s or no construct left is both in excess and escapable; when the
     *     {@link #MAX_ESCAPE_ROUNDS} budget runs out first it carries the backslashes inserted so far and still
     *     fails {@link MarkdownStructureCheck}, which is where that outcome is reported.
     */
    public static String escapeModelIntroduced(
            String candidate, List<FragmentRange> fragmentRanges, String sourceText, SegmentKind kind) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(fragmentRanges, "fragmentRanges");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(kind, "kind");
        final List<String> sourceMultiset = MarkdownStructureCheck.constructMultiset(sourceText);
        String text = candidate;
        final List<int[]> ranges = copyOf(fragmentRanges);
        for (int round = 0; round < MAX_ESCAPE_ROUNDS; round++) {
            final List<String> candidateMultiset = MarkdownStructureCheck.constructMultiset(text);
            if (candidateMultiset.equals(sourceMultiset)) {
                return text;
            }
            final Node root = MarkdownReader.parser().parse(text);
            final List<DelimiterGroup> groups = new ArrayList<>();
            collectGroups(root, text, groups);
            final DelimiterGroup group = firstActionableGroup(groups, ranges, candidateMultiset, sourceMultiset, kind);
            if (group == null) {
                return text;
            }
            text = applyGroup(text, group, ranges);
        }
        return text;
    }

    /** A mutable working copy: the escape rounds shift these ranges in place as backslashes are inserted or deleted. */
    private static List<int[]> copyOf(List<FragmentRange> ranges) {
        final List<int[]> copy = new ArrayList<>(ranges.size());
        for (final FragmentRange range : ranges) {
            copy.add(new int[] {range.start(), range.end()});
        }
        return copy;
    }

    /**
     * The first group, in document order, worth acting on: one whose construct type is neither disregarded for
     * this segment kind nor already as numerous in the source, and at least one of whose positions lies outside
     * every restored fragment.
     */
    private static @Nullable DelimiterGroup firstActionableGroup(
            List<DelimiterGroup> groups,
            List<int[]> ranges,
            List<String> candidateMultiset,
            List<String> sourceMultiset,
            SegmentKind kind) {
        for (final DelimiterGroup group : groups) {
            if (MarkdownStructureCheck.isDisregarded(group.constructName(), kind)) {
                continue;
            }
            final boolean excess = isInExcess(group.constructName(), candidateMultiset, sourceMultiset);
            if (excess && hasActionablePosition(group.positions(), ranges)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Whether {@code constructName} occurs more often in {@code candidateMultiset} than in {@code sourceMultiset}
     * — the source's own instances of a construct type are never escaped just because a document-order scan
     * reaches them before a genuinely model-introduced instance of the same type.
     */
    private static boolean isInExcess(
            String constructName, List<String> candidateMultiset, List<String> sourceMultiset) {
        return Collections.frequency(candidateMultiset, constructName)
                > Collections.frequency(sourceMultiset, constructName);
    }

    private static boolean hasActionablePosition(int[] group, List<int[]> ranges) {
        for (final int position : group) {
            if (!insideAnyFragment(position, ranges)) {
                return true;
            }
        }
        return false;
    }

    private static boolean insideAnyFragment(int position, List<int[]> ranges) {
        for (final int[] range : ranges) {
            if (position >= range[0] && position < range[1]) {
                return true;
            }
        }
        return false;
    }

    /** Walks the tree in document order, appending one group per non-container construct it finds. */
    private static void collectGroups(Node node, String text, List<DelimiterGroup> groups) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            appendGroup(child, text, groups);
            collectGroups(child, text, groups);
        }
    }

    /**
     * Classifies one node by type and structure — never by name beyond what commonmark's own node types already
     * are — per the table in design.md D6/tasks.md 8.1: paired constructs contribute their two delimiter
     * positions, a marker-led block contributes its marker's position, a pure container contributes nothing and is
     * only descended into, and every other spanned construct contributes its own start. Each group carries the
     * node's simple class name alongside its positions — the same name {@link MarkdownStructureCheck}'s multiset
     * uses — so {@link #isInExcess} can compare like against like.
     *
     * <p>A node carrying no source span at all contributes nothing: there is no position to escape, and asking one
     * for a span index throws. Only {@code TableCell} reaches this guard in practice — of the four span-less node
     * types {@link MarkdownSpans} records, {@code Paragraph} is a container, and the two break types are handled
     * above it.
     */
    private static void appendGroup(Node node, String text, List<DelimiterGroup> groups) {
        if (node instanceof Text || node instanceof SoftLineBreak) {
            return;
        }
        if (node instanceof HardLineBreak hardLineBreak) {
            appendHardLineBreakGroup(hardLineBreak, text, groups);
            return;
        }
        if (isContainerOnly(node) || node.getSourceSpans().isEmpty()) {
            return;
        }
        final String constructName = node.getClass().getSimpleName();
        if (node instanceof Emphasis || node instanceof StrongEmphasis || node instanceof Link) {
            appendEscapableGroup(groups, constructName, pairedGroup(node), text);
        } else if (node instanceof Heading || node instanceof BlockQuote || node instanceof ThematicBreak) {
            appendEscapableGroup(
                    groups, constructName, new int[] {MarkdownMarkers.firstNonWhitespace(node, text)}, text);
        } else if (node instanceof ListItem listItem) {
            appendEscapableGroup(
                    groups, constructName, new int[] {MarkdownMarkers.listItemMarkerPosition(listItem, text)}, text);
        } else {
            appendEscapableGroup(groups, constructName, new int[] {MarkdownSpans.firstSpanStart(node)}, text);
        }
    }

    /**
     * The single point at which a derived position becomes an insertion, and therefore the single place the
     * escapability rule is enforced — every classification branch goes through here, so none of them can
     * reintroduce the stray-backslash defect described in this class's Javadoc by forgetting its own copy of the
     * check. A group is added only when <em>every</em> one of its positions is in bounds and is
     * {@link AsciiPunctuation#isPunctuation ASCII punctuation}: a partly-escapable group would leave a pair
     * half-escaped, which is the outcome the per-construct-group design exists to avoid.
     */
    private static void appendEscapableGroup(
            List<DelimiterGroup> groups, String constructName, int[] positions, String text) {
        for (final int position : positions) {
            if (position >= text.length() || !AsciiPunctuation.isPunctuation(text.charAt(position))) {
                return;
            }
        }
        groups.add(new DelimiterGroup(constructName, positions, false));
    }

    /**
     * A hard line break is neutralised by deletion, never by insertion — see {@link HardLineBreakDeletion} for why
     * and how the positions are derived. No group is added when it returns {@code null}: the construct then
     * survives to be honestly reported by {@link MarkdownStructureCheck}, the same fallback
     * {@link #appendEscapableGroup} takes for a position that is not escapable.
     */
    private static void appendHardLineBreakGroup(
            HardLineBreak hardLineBreak, String text, List<DelimiterGroup> groups) {
        final int @Nullable [] positions = HardLineBreakDeletion.positionsToDelete(hardLineBreak, text);
        if (positions != null) {
            groups.add(new DelimiterGroup("HardLineBreak", positions, true));
        }
    }

    /**
     * A pure container contributes no delimiter of its own — a list's marker belongs to its item, and a paragraph
     * has none at all. Commonmark's {@code Document} is deliberately absent from this list rather than defensively
     * present: it is only ever the parse root, and this method is reached only from the child walk, so testing for
     * it would be a guard over a state the caller has already made impossible.
     */
    private static boolean isContainerOnly(Node node) {
        return node instanceof Paragraph || node instanceof BulletList || node instanceof OrderedList;
    }

    /**
     * A paired construct's two delimiter positions — its own start, and its last spanned child's end. The opening
     * position is the node's own start, which is a different derivation from {@code MarkdownMasker}'s opening
     * fragment {@code [node start, first spanned child's start)}; only the closing side needs
     * {@link MarkdownSpans#lastSpannedChild(Node)}, and it needs it for the reason recorded there — a span-less
     * {@code SoftLineBreak} or space-spelled {@code HardLineBreak} can sit as the last child of a link label
     * wrapped across a line, and asking it for a span index threw {@code ArrayIndexOutOfBoundsException}, which the
     * port reported as {@code ErrorCode.internal} instead of the {@code ErrorCode.validation} odd model output
     * deserves. A childless node, or one whose every child is span-less, has only its own start.
     */
    private static int[] pairedGroup(Node node) {
        final Node lastSpanned = MarkdownSpans.lastSpannedChild(node);
        if (lastSpanned == null) {
            return new int[] {MarkdownSpans.firstSpanStart(node)};
        }
        return new int[] {MarkdownSpans.firstSpanStart(node), MarkdownSpans.lastSpanEnd(lastSpanned)};
    }

    /**
     * Applies one group's action to {@code text} — a backslash inserted before each of its positions, or (when
     * {@link DelimiterGroup#deletion()}) each of its positions deleted outright. Every position inside a restored
     * fragment is skipped either way, so a position genuinely belonging to the source's own content is never
     * touched. Positions are processed in descending order so an earlier edit never invalidates a later one's
     * index.
     */
    private static String applyGroup(String text, DelimiterGroup group, List<int[]> ranges) {
        final List<Integer> positions = new ArrayList<>();
        for (final int position : group.positions()) {
            if (!insideAnyFragment(position, ranges)) {
                positions.add(position);
            }
        }
        positions.sort(Comparator.reverseOrder());
        final StringBuilder builder = new StringBuilder(text);
        for (final int position : positions) {
            if (group.deletion()) {
                builder.deleteCharAt(position);
                shiftRangesAfterDeletion(ranges, position);
            } else {
                builder.insert(position, '\\');
                shiftRangesAfterInsertion(ranges, position);
            }
        }
        return builder.toString();
    }

    /**
     * Widens every fragment boundary after inserting one character at {@code insertionPoint}. A fragment range is
     * half-open, {@code [start, end)}: a character inserted <em>at</em> {@code start} pushes the whole fragment
     * along, so the start moves for an insertion point less than or equal to it, while a character inserted
     * <em>at</em> {@code end} lands outside the fragment, so the end moves only for a strictly smaller one.
     * Getting that one comparison wrong silently grew the fragment by a character it does not own — measured,
     * restoring {@code KEEP*a* b} as {@code KEEP\*a\* b} left the fragment {@code KEEP} recorded as {@code KEEP\},
     * which no later round could tell from real fragment content.
     */
    private static void shiftRangesAfterInsertion(List<int[]> ranges, int insertionPoint) {
        for (final int[] range : ranges) {
            if (range[0] >= insertionPoint) {
                range[0]++;
            }
            if (range[1] > insertionPoint) {
                range[1]++;
            }
        }
    }

    /**
     * Narrows every fragment boundary after deleting the character at {@code deletionPoint}. By the same half-open
     * arithmetic, a character deleted at or after {@code start} is not one the start counts, and a character
     * deleted at or after {@code end} is not one the end counts, so both move only for a strictly smaller deletion
     * point. The earlier rationale for {@code >} — that {@link #insideAnyFragment} has already filtered
     * {@code deletionPoint} out of every range, so it can never equal a range's own start — does not hold: an
     * empty range reports nothing as inside it, and {@code MaskWriter#appendAtomic} accepts an empty fragment, so
     * {@code deletionPoint == start} is reachable. The half-open arithmetic is what makes {@code >} right here.
     */
    private static void shiftRangesAfterDeletion(List<int[]> ranges, int deletionPoint) {
        for (final int[] range : ranges) {
            if (range[0] > deletionPoint) {
                range[0]--;
            }
            if (range[1] > deletionPoint) {
                range[1]--;
            }
        }
    }
}
