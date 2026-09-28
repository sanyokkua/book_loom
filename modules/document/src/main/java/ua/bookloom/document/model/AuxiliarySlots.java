package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.AttributeAnchor;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.mask.MaskedContent;
import ua.bookloom.document.mask.PlainTextMasker;
import ua.bookloom.util.hash.HashUtil;

/**
 * The auxiliary slot table of one open book: which text an auxiliary segment id stands for, and where in the parsed
 * trees it lives.
 *
 * <p>A slot cannot be addressed by the body walk's anchors alone, because its root — the package document, a content
 * document's {@code <head>}, the navigation document, the NCX — lies outside every body. The table therefore maps
 * each segment id, which names its slot and never a running count, to the live tree root and the path inside it, so a
 * slot added later never renumbers another and a stored decision stays on the right text. A slot with no target is
 * left alone.
 */
@Slf4j
public final class AuxiliarySlots {

    private static final double INITIAL_CONFIDENCE = 0.0;

    private final Map<String, Slot> slots;
    private final List<Slot.Alias> aliases;

    private AuxiliarySlots(Map<String, Slot> slots, List<Slot.Alias> aliases) {
        this.slots = Map.copyOf(slots);
        this.aliases = List.copyOf(aliases);
    }

    /** One slot's location: the resource it lives in, the live tree node its path starts from, and how it is written. */
    public sealed interface Slot {

        /** The archive path of the resource the slot lives in, so a writer knows which resources it changed. */
        String resource();

        /** The live tree node the slot's path starts from. */
        TreeNode root();

        /** The element-sibling path from {@link #root()} to the slot's element. */
        List<Integer> path();

        /** An element whose character data is the text, written back as escaped plain text. */
        record Text(String resource, TreeNode root, List<Integer> path) implements Slot {}

        /** An element whose children are the text's markup, re-parsed from the restored target as body runs are. */
        record Markup(String resource, TreeNode root, List<Integer> path) implements Slot {}

        /**
         * A text element with no segment of its own, written from the target of the segment {@code sourceSegmentId}
         * so two places that read alike are written alike.
         */
        record Alias(String resource, TreeNode root, List<Integer> path, String sourceSegmentId) implements Slot {}

        /** An image's {@code alt} attribute; see {@link AltImages} for how it is written. */
        record Alt(
                String resource,
                TreeNode root,
                List<Integer> path,
                @Nullable String runSegmentId,
                String markup,
                int occurrence)
                implements Slot {}
    }

    /**
     * What a write changed.
     *
     * @param resources the archive paths of every resource a slot was written into
     * @param bodyTargets the target markup to write in place of a body segment's own, by segment id, for each body run
     *     whose image descriptions were substituted into it
     */
    public record Outcome(Set<String> resources, Map<String, String> bodyTargets) {

        /** Copies both so the table's caller cannot change them. */
        public Outcome {
            Objects.requireNonNull(resources, "resources");
            Objects.requireNonNull(bodyTargets, "bodyTargets");
            resources = Set.copyOf(resources);
            bodyTargets = Map.copyOf(bodyTargets);
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
     * Writes each targeted auxiliary segment of {@code unit} into its slot and leaves an untargeted or unknown slot as
     * source. The trees are written before any body run is, because an image's path is only valid in the pristine
     * tree.
     *
     * @param unit the auxiliary unit in the state its segments should be written back in
     * @param bodyUnits the body units, whose runs an image description may have to be substituted into
     * @return the resources written and the body targets that replace the runs' own
     */
    public Outcome writeBack(Unit unit, List<Unit> bodyUnits) {
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(bodyUnits, "bodyUnits");
        final Map<String, Segment> byId = new HashMap<>();
        unit.segments().forEach(segment -> byId.put(segment.id(), segment));
        final Set<String> resources = new LinkedHashSet<>();
        final List<AltImages.Write> altWrites = new ArrayList<>();
        final int skipped = writeSegments(unit, resources, altWrites);
        writeAliases(byId, resources);
        final Map<String, String> bodyTargets = AltImages.write(altWrites, bodyUnits);
        log.debug(
                "auxiliary write-back slots written={} skipped={} resources={}",
                unit.segments().size() - skipped,
                skipped,
                resources);
        return new Outcome(resources, bodyTargets);
    }

    /** Writes every targeted text slot at once and defers the images, which need the body runs; returns the skips. */
    private int writeSegments(Unit unit, Set<String> resources, List<AltImages.Write> altWrites) {
        int skipped = 0;
        for (final Segment segment : unit.segments()) {
            final Slot slot = slots.get(segment.id());
            final String target = segment.targetInner();
            if (slot == null || target == null) {
                log.trace("auxiliary slot {} skipped (target present: {})", segment.id(), target != null);
                skipped++;
            } else if (slot instanceof Slot.Alt alt) {
                altWrites.add(new AltImages.Write(alt, target));
                resources.add(alt.resource());
            } else {
                write(slot, segment.id(), target);
                resources.add(slot.resource());
            }
        }
        return skipped;
    }

    private void writeAliases(Map<String, Segment> byId, Set<String> resources) {
        for (final Slot.Alias alias : aliases) {
            final Segment source = byId.get(alias.sourceSegmentId());
            final String target = source == null ? null : source.targetInner();
            if (target == null) {
                log.trace("auxiliary alias of {} skipped: no target", alias.sourceSegmentId());
                continue;
            }
            log.debug("auxiliary alias in {} written from {}", alias.resource(), alias.sourceSegmentId());
            write(alias, alias.sourceSegmentId(), target);
            resources.add(alias.resource());
        }
    }

    private static void write(Slot slot, String segmentId, String targetInner) {
        final TreeNode element = SkeletonAnchors.nodeAt(slot.root(), slot.path());
        log.trace("auxiliary slot {} written: {}", segmentId, targetInner);
        if (slot instanceof Slot.Markup) {
            element.replaceChildren(0, element.childNodes().size(), targetInner);
        } else {
            element.setText(plainTextOf(targetInner));
        }
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
        private final List<Slot.Alias> aliases = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a text slot: the character data of the element at {@code path} below {@code root}, skipped when it
         * holds no translatable text.
         *
         * @param resource the archive path of the resource the element lives in
         * @param segmentId the segment id, which names the slot
         * @param kind the auxiliary kind of the segment
         * @param root the live tree node the path starts from
         * @param path the element-sibling path from {@code root} to the element
         * @param dialect the format {@code root} was parsed from
         * @return this builder
         */
        public Builder addText(
                String resource,
                String segmentId,
                SegmentKind kind,
                TreeNode root,
                List<Integer> path,
                TreeDialect dialect) {
            return addElement(new Slot.Text(resource, root, path), segmentId, kind, dialect);
        }

        /**
         * Adds a markup slot: the children of the element at {@code path} below {@code root}, written back as markup
         * so inline elements in the target stay elements; skipped when it holds no translatable text.
         *
         * @param resource the archive path of the resource the element lives in
         * @param segmentId the segment id, which names the slot
         * @param kind the auxiliary kind of the segment
         * @param root the live tree node the path starts from
         * @param path the element-sibling path from {@code root} to the element
         * @param dialect the format {@code root} was parsed from
         * @return this builder
         */
        public Builder addMarkup(
                String resource,
                String segmentId,
                SegmentKind kind,
                TreeNode root,
                List<Integer> path,
                TreeDialect dialect) {
            return addElement(new Slot.Markup(resource, root, path), segmentId, kind, dialect);
        }

        /**
         * Adds a text element that is written from another segment's target and is no segment itself.
         *
         * @param resource the archive path of the resource the element lives in
         * @param root the live tree node the path starts from
         * @param path the element-sibling path from {@code root} to the element
         * @param sourceSegmentId the id of the segment whose target the element is written from
         * @return this builder
         */
        public Builder addAlias(String resource, TreeNode root, List<Integer> path, String sourceSegmentId) {
            Objects.requireNonNull(sourceSegmentId, "sourceSegmentId");
            aliases.add(new Slot.Alias(resource, root, path, sourceSegmentId));
            return this;
        }

        /**
         * Adds an image's {@code alt} value as an {@link SegmentKind#ALT} segment named by the unit and the image's
         * path.
         *
         * @param resource the archive path of the content document holding the image
         * @param unitHref the content document's unit href, which names the slot
         * @param root the content document's walk root, which {@code image}'s path starts from
         * @param image the image found by {@link AltImages#scan}
         * @return this builder
         */
        public Builder addAlt(String resource, String unitHref, TreeNode root, AltImages.Image image) {
            Objects.requireNonNull(image, "image");
            final String id = "aux:alt:" + unitHref + ":" + dotted(image.path());
            slots.put(
                    id,
                    new Slot.Alt(
                            resource, root, image.path(), image.runSegmentId(), image.markup(), image.occurrence()));
            drafts.add(new Draft(
                    id,
                    SegmentKind.ALT,
                    image.alt(),
                    PlainTextMasker.mask(image.alt()),
                    new AttributeAnchor(image.path(), "alt")));
            return this;
        }

        /**
         * Whether a segment was produced for {@code segmentId}.
         *
         * @param segmentId a segment id
         * @return {@code true} if a slot was added under that id, {@code false} if its element held no text
         */
        public boolean isProduced(String segmentId) {
            return slots.containsKey(segmentId);
        }

        private Builder addElement(Slot slot, String segmentId, SegmentKind kind, TreeDialect dialect) {
            Objects.requireNonNull(segmentId, "segmentId");
            Objects.requireNonNull(kind, "kind");
            final List<TreeNode> children =
                    SkeletonAnchors.nodeAt(slot.root(), slot.path()).childNodes();
            if (TreeMasker.translatableText(children, dialect).isBlank()) {
                log.debug("auxiliary slot {} has no text; not produced", segmentId);
                return this;
            }
            final StringBuilder markup = new StringBuilder();
            children.forEach(child -> markup.append(child.markup()));
            drafts.add(new Draft(
                    segmentId,
                    kind,
                    markup.toString(),
                    TreeMasker.mask(children, dialect),
                    new NodeAnchor(slot.path(), 0)));
            slots.put(segmentId, slot);
            return this;
        }

        private static String dotted(List<Integer> path) {
            final StringBuilder joined = new StringBuilder();
            path.forEach(step -> joined.append(joined.isEmpty() ? "" : ".").append(step));
            return joined.toString();
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
            return new Collected(segments, new AuxiliarySlots(slots, aliases));
        }

        private Segment segmentOf(int order) {
            final Draft draft = drafts.get(order);
            final MaskedContent masked = draft.masked();
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
                    draft.anchor(),
                    null,
                    SegmentStatus.PENDING,
                    INITIAL_CONFIDENCE,
                    null,
                    masked.pairs(),
                    masked.lineBreakTokens());
        }

        private void logCounts(List<Segment> segments) {
            final Map<SegmentKind, Integer> counts = new EnumMap<>(SegmentKind.class);
            for (final Segment segment : segments) {
                counts.merge(segment.kind(), 1, Integer::sum);
                log.trace("auxiliary segment {} text: {}", segment.id(), segment.masked());
            }
            log.debug("auxiliary segments by kind {} aliases={}", counts, aliases.size());
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

    private record Draft(
            String id, SegmentKind kind, String sourceInner, MaskedContent masked, SkeletonAnchor anchor) {}
}
