package ua.bookloom.document.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.AttributeAnchor;
import ua.bookloom.api.document.NodeAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.util.text.VisibleText;

/**
 * Finds the images whose {@code alt} value is auxiliary text, and writes a translated value back (design.md D12,
 * "ALT write-back").
 *
 * <p>An image inside a body run travels through translation as an atomic token that restores to its exact source
 * markup, so writing the translated value onto the tree would be undone by the run's re-parse. When the run has a
 * target the value is therefore substituted into that target's markup; when it has none, or the image sits in no run,
 * the attribute is set on the tree, where the serializer encodes it once.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AltImages {

    private static final String IMAGE_TAG = "img";
    private static final String ALT_ATTRIBUTE = "alt";

    /**
     * One image with a non-empty {@code alt}.
     *
     * @param path the element-sibling path from the body to the image
     * @param alt the image's {@code alt} value as plain text
     * @param markup the image's exact source markup, which is what a body run's restored text holds for it
     * @param runSegmentId the id of the body segment whose run holds the image, or {@code null} when it sits in no run
     * @param occurrence how many images with identical markup precede this one in the same run
     */
    public record Image(
            List<Integer> path,
            String alt,
            String markup,
            @Nullable String runSegmentId,
            int occurrence) {

        /** Copies the path so the record cannot be changed through the caller's list. */
        public Image {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(alt, "alt");
            Objects.requireNonNull(markup, "markup");
            path = List.copyOf(path);
        }
    }

    /** One image's translated value, waiting to be written. */
    record Write(AuxiliarySlots.Slot.Alt slot, String plainAlt) {}

    /**
     * Lists every image below {@code root} that has a non-empty {@code alt}, in document order, each with the run
     * that holds it.
     *
     * @param root the unit's walk root, the same node its segments' anchors are relative to
     * @param segments the unit's body segments
     * @return the images; empty when none has a non-empty {@code alt}
     */
    public static List<Image> scan(TreeNode root, List<Segment> segments) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(segments, "segments");
        final List<Found> found = new ArrayList<>();
        collect(root, new ArrayList<>(), found);
        final Map<List<Integer>, List<Segment>> byBlock = new HashMap<>();
        for (final Segment segment : segments) {
            if (segment.anchor() instanceof NodeAnchor anchor) {
                byBlock.computeIfAbsent(anchor.nodePath(), path -> new ArrayList<>())
                        .add(segment);
            }
        }
        final Map<String, Integer> seen = new HashMap<>();
        final List<Image> images = new ArrayList<>(found.size());
        for (final Found image : found) {
            final String runId = runOf(root, byBlock, image.path());
            final int occurrence = runId == null ? 0 : seen.merge(runId + '\0' + image.markup(), 1, Integer::sum) - 1;
            images.add(new Image(image.path(), image.alt(), image.markup(), runId, occurrence));
        }
        log.debug(
                "images with alt text found={} inRuns={}",
                images.size(),
                images.stream().filter(i -> i.runSegmentId() != null).count());
        return images;
    }

    private record Found(List<Integer> path, String alt, String markup) {}

    private static void collect(TreeNode parent, List<Integer> path, List<Found> found) {
        int elementIndex = 0;
        for (final TreeNode child : parent.childNodes()) {
            if (child.tagName() == null) {
                continue;
            }
            path.add(elementIndex);
            elementIndex++;
            final String alt = child.attribute(ALT_ATTRIBUTE);
            if (IMAGE_TAG.equals(child.tagName()) && alt != null && !VisibleText.isBlank(alt)) {
                found.add(new Found(List.copyOf(path), alt, child.markup()));
            }
            collect(child, path, found);
            path.removeLast();
        }
    }

    /**
     * The block that emits runs is the first ancestor with a segment: the walk stops descending there, so nothing
     * deeper has one. The image belongs to the run that covers its direct-child ancestor of that block.
     */
    private static @Nullable String runOf(
            TreeNode root, Map<List<Integer>, List<Segment>> byBlock, List<Integer> imagePath) {
        for (int length = 1; length < imagePath.size(); length++) {
            final List<Segment> blockSegments = byBlock.get(imagePath.subList(0, length));
            if (blockSegments != null) {
                final TreeNode block = SkeletonAnchors.nodeAt(root, imagePath.subList(0, length));
                return segmentOfRun(blockSegments, runIndexOf(block, imagePath.get(length)));
            }
        }
        return null;
    }

    private static int runIndexOf(TreeNode block, int elementIndex) {
        final List<TreeNode> children = block.childNodes();
        int childPosition = 0;
        int seen = 0;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).tagName() != null) {
                if (seen == elementIndex) {
                    childPosition = i;
                    break;
                }
                seen++;
            }
        }
        for (final BlockRuns.Run run : BlockRuns.split(block)) {
            if (childPosition >= run.fromInclusive() && childPosition < run.toExclusive()) {
                return run.index();
            }
        }
        throw new IllegalStateException("An image lies outside every run of its block");
    }

    private static @Nullable String segmentOfRun(List<Segment> blockSegments, int runIndex) {
        for (final Segment segment : blockSegments) {
            if (segment.anchor() instanceof NodeAnchor anchor && anchor.runIndex() == runIndex) {
                return segment.id();
            }
        }
        return null;
    }

    /**
     * Writes each translated value: substituted into its run's target where the run has one, set on the tree
     * otherwise.
     *
     * @return each substituted run's segment id mapped to the target markup to write in place of its own
     */
    static Map<String, String> write(List<Write> writes, List<Unit> bodyUnits) {
        final Map<String, Segment> bodyById = new HashMap<>();
        bodyUnits.forEach(unit -> unit.segments().forEach(segment -> bodyById.put(segment.id(), segment)));
        final Map<String, RunWrites> inRun = new LinkedHashMap<>();
        for (final Write write : writes) {
            final String runId = write.slot().runSegmentId();
            final Segment run = runId == null ? null : bodyById.get(runId);
            final String runTarget = run == null ? null : run.targetInner();
            if (runId != null && runTarget != null) {
                inRun.computeIfAbsent(runId, id -> new RunWrites(runTarget, new ArrayList<>()))
                        .writes()
                        .add(write);
            } else {
                setOnTree(write);
            }
        }
        final Map<String, String> substituted = new LinkedHashMap<>();
        inRun.forEach((runId, group) -> substituted.put(runId, substitute(group.target(), group.writes())));
        log.debug(
                "alt values set on the tree={} substituted in runs={}", writes.size() - countOf(inRun), countOf(inRun));
        return substituted;
    }

    /** The target markup of one body run and the image descriptions to substitute into it. */
    private record RunWrites(String target, List<Write> writes) {}

    private static int countOf(Map<String, RunWrites> inRun) {
        return inRun.values().stream().mapToInt(run -> run.writes().size()).sum();
    }

    private static void setOnTree(Write write) {
        log.debug(
                "alt {} set on the tree: run has no target or image is in no run",
                write.slot().path());
        SkeletonAnchors.writeBackAll(
                write.slot().root(),
                List.of(new SkeletonAnchors.PendingWrite(
                        new AttributeAnchor(write.slot().path(), ALT_ATTRIBUTE), write.plainAlt())));
    }

    private record Edit(int start, int end, String replacement) {}

    /** Every edit is located in the original text first and applied back to front, so no edit shifts another. */
    private static String substitute(String target, List<Write> writes) {
        final List<Edit> edits = new ArrayList<>(writes.size());
        for (final Write write : writes) {
            final String markup = write.slot().markup();
            final int start = indexOfOccurrence(target, markup, write.slot().occurrence());
            if (start < 0) {
                log.debug(
                        "alt {} not substituted: image {} not found in the run's target",
                        write.slot().path(),
                        write.slot().occurrence());
                continue;
            }
            log.trace("alt substituted in a run: {}", write.plainAlt());
            edits.add(new Edit(start, start + markup.length(), withAlt(markup, write.plainAlt())));
        }
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        final StringBuilder result = new StringBuilder(target);
        edits.forEach(edit -> result.replace(edit.start(), edit.end(), edit.replacement()));
        return result.toString();
    }

    private static int indexOfOccurrence(String text, String fragment, int occurrence) {
        int from = 0;
        int found = text.indexOf(fragment, from);
        for (int i = 0; i < occurrence && found >= 0; i++) {
            from = found + fragment.length();
            found = text.indexOf(fragment, from);
        }
        return found;
    }

    /** The image's markup with a new {@code alt}, attribute-encoded by the serializer that will write it. */
    private static String withAlt(String imageMarkup, String plainAlt) {
        final Document fragment = Jsoup.parseBodyFragment(imageMarkup);
        fragment.outputSettings().syntax(Document.OutputSettings.Syntax.xml).prettyPrint(false);
        final org.jsoup.nodes.Element image =
                Objects.requireNonNull(fragment.body().firstElementChild(), "image");
        image.attr(ALT_ATTRIBUTE, plainAlt);
        return image.outerHtml();
    }
}
