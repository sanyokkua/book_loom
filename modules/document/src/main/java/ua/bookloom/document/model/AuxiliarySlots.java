package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.mask.MaskedContent;
import ua.bookloom.util.hash.HashUtil;

/**
 * The auxiliary slot table of one open book: which text an auxiliary segment id stands for, and where in the parsed
 * trees it lives.
 *
 * <p>A slot cannot be addressed by the body walk's anchors alone, because its root — the package document, a content
 * document's {@code <head>} — lies outside every body. The table therefore maps each segment id, which names its
 * slot and never a running count, to the live tree root and the anchor inside it, so a slot added later never
 * renumbers another and a stored decision stays on the right text. A slot with no target is left alone.
 */
@Slf4j
public final class AuxiliarySlots {

    private static final double INITIAL_CONFIDENCE = 0.0;

    private final Map<String, Slot> slots;

    private AuxiliarySlots(Map<String, Slot> slots) {
        this.slots = Map.copyOf(slots);
    }

    /**
     * One slot's location.
     *
     * @param root the live tree node the anchor's path starts from
     * @param anchor the element inside {@code root} whose character data is the slot's text
     */
    public record Slot(TreeNode root, NodeAnchor anchor) {

        /** Both parts are dereferenced at write time, so a null is caught where the slot is built. */
        public Slot {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(anchor, "anchor");
        }
    }

    /**
     * Starts an empty collection of slots.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Writes each targeted auxiliary segment of {@code unit} into its slot's element as character data — the tree
     * escapes it on output — and leaves an untargeted or unknown slot as source.
     *
     * @param unit the auxiliary unit in the state its segments should be written back in
     */
    public void writeBack(Unit unit) {
        Objects.requireNonNull(unit, "unit");
        int written = 0;
        int skipped = 0;
        for (final Segment segment : unit.segments()) {
            if (writeOne(segment)) {
                written++;
            } else {
                skipped++;
            }
        }
        log.debug("auxiliary write-back slots written={} skipped={}", written, skipped);
    }

    private boolean writeOne(Segment segment) {
        final String targetInner = segment.targetInner();
        final Slot slot = slots.get(segment.id());
        if (targetInner == null || slot == null) {
            log.trace("auxiliary slot {} skipped (target present: {})", segment.id(), targetInner != null);
            return false;
        }
        final String plain = plainTextOf(targetInner);
        log.trace("auxiliary slot {} written: {}", segment.id(), plain);
        SkeletonAnchors.nodeAt(slot.root(), slot.anchor().nodePath()).setText(plain);
        return true;
    }

    /**
     * The character data of restored markup: the restored target is markup, so a text slot decodes it and hands the
     * tree plain text to escape once on output — so an ampersand is never escaped twice.
     */
    private static String plainTextOf(String restoredMarkup) {
        return Jsoup.parseBodyFragment(restoredMarkup).body().wholeText();
    }

    /** Collects auxiliary segments and their slots, in the order they are added. */
    public static final class Builder {

        private final List<Draft> drafts = new ArrayList<>();
        private final Map<String, Slot> slots = new LinkedHashMap<>();

        private Builder() {}

        /**
         * Adds a text slot: the character data of the element at {@code path} below {@code root}, skipped when it
         * holds no translatable text.
         *
         * @param segmentId the segment id, which names the slot
         * @param kind the auxiliary kind of the segment
         * @param root the live tree node the path starts from
         * @param path the element-sibling path from {@code root} to the element
         * @param dialect the format {@code root} was parsed from
         * @return this builder
         */
        public Builder addText(
                String segmentId, SegmentKind kind, TreeNode root, List<Integer> path, TreeDialect dialect) {
            Objects.requireNonNull(segmentId, "segmentId");
            Objects.requireNonNull(kind, "kind");
            final List<TreeNode> children = SkeletonAnchors.nodeAt(root, path).childNodes();
            if (TreeMasker.translatableText(children, dialect).isBlank()) {
                log.debug("auxiliary slot {} has no text; not produced", segmentId);
                return this;
            }
            final StringBuilder markup = new StringBuilder();
            children.forEach(child -> markup.append(child.markup()));
            drafts.add(new Draft(segmentId, kind, markup.toString(), TreeMasker.mask(children, dialect)));
            slots.put(segmentId, new Slot(root, new NodeAnchor(path, 0)));
            return this;
        }

        /**
         * Finishes the collection.
         *
         * @return the auxiliary segments in the order added, and the table that writes them back
         */
        public Collected build() {
            final List<Segment> segments = new ArrayList<>(drafts.size());
            for (int order = 0; order < drafts.size(); order++) {
                segments.add(segmentOf(order));
            }
            logCounts(segments);
            return new Collected(segments, new AuxiliarySlots(slots));
        }

        private Segment segmentOf(int order) {
            final Draft draft = drafts.get(order);
            final MaskedContent masked = draft.masked();
            final Slot slot = Objects.requireNonNull(slots.get(draft.id()), "slot");
            return new Segment(
                    draft.id(),
                    Unit.AUXILIARY_ID,
                    order,
                    draft.kind(),
                    draft.sourceInner(),
                    masked.masked(),
                    masked.placeholders(),
                    HashUtil.sha256OfNfcText(draft.sourceInner()),
                    order > 0 ? drafts.get(order - 1).id() : null,
                    order < drafts.size() - 1 ? drafts.get(order + 1).id() : null,
                    slot.anchor(),
                    null,
                    SegmentStatus.PENDING,
                    INITIAL_CONFIDENCE,
                    null,
                    masked.pairs(),
                    masked.lineBreakTokens());
        }

        private static void logCounts(List<Segment> segments) {
            final Map<SegmentKind, Integer> counts = new EnumMap<>(SegmentKind.class);
            for (final Segment segment : segments) {
                counts.merge(segment.kind(), 1, Integer::sum);
                log.trace("auxiliary segment {} text: {}", segment.id(), segment.masked());
            }
            log.debug("auxiliary segments by kind {}", counts);
        }
    }

    /**
     * What a reader gets back from a finished collection.
     *
     * @param segments the auxiliary segments, in slot order
     * @param slots the table the writer resolves them through
     */
    public record Collected(List<Segment> segments, AuxiliarySlots slots) {

        /** Copies the segments so the caller's list cannot change the unit built from them. */
        public Collected {
            Objects.requireNonNull(segments, "segments");
            Objects.requireNonNull(slots, "slots");
            segments = List.copyOf(segments);
        }
    }

    private record Draft(String id, SegmentKind kind, String sourceInner, MaskedContent masked) {}
}
