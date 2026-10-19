package ua.bookloom.document.md;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SkeletonAnchor;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.model.BufferTextWriter.TextReplacement;

/**
 * Turns a document's targeted segments into the span replacements a Markdown file is spliced with, and keeps them
 * from overlapping: an image description lies inside its paragraph's span, so when that paragraph is itself
 * replaced the description is written inside the restored image instead of as a replacement of its own.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownReplacements {

    private record Img(String id, MarkdownSlots.ImageSlot slot) {}

    private record Edit(int start, int end, String text) {}

    static List<TextReplacement> of(Document document, ParsedMarkdown parsed, String targetLanguage) {
        final MarkdownSlots slots = parsed.slots();
        final Map<String, Segment> aux = new LinkedHashMap<>();
        final List<TextReplacement> replacements = new ArrayList<>();
        for (final Unit unit : document.units()) {
            if (unit.isAuxiliary()) {
                unit.segments().forEach(segment -> aux.put(segment.id(), segment));
            }
        }
        final Map<String, List<Img>> imagesByBody = imagesByBody(slots);
        final Map<String, Boolean> bodyTargeted = new HashMap<>();
        for (final Unit unit : document.units()) {
            if (!unit.isAuxiliary()) {
                addBody(unit, parsed, aux, imagesByBody, bodyTargeted, replacements);
            }
        }
        addFrontmatter(aux, slots, replacements);
        addImages(aux, slots, bodyTargeted, replacements);
        addLang(slots, targetLanguage, replacements);
        return replacements;
    }

    private static Map<String, List<Img>> imagesByBody(MarkdownSlots slots) {
        final Map<String, List<Img>> grouped = new HashMap<>();
        slots.images().forEach((id, image) -> {
            if (image.enclosingId() != null) {
                grouped.computeIfAbsent(image.enclosingId(), key -> new ArrayList<>())
                        .add(new Img(id, image));
            }
        });
        return grouped;
    }

    private static void addBody(
            Unit unit,
            ParsedMarkdown parsed,
            Map<String, Segment> aux,
            Map<String, List<Img>> imagesByBody,
            Map<String, Boolean> bodyTargeted,
            List<TextReplacement> replacements) {
        for (final Segment segment : unit.segments()) {
            final String target = segment.targetInner();
            if (target == null) {
                continue;
            }
            bodyTargeted.put(segment.id(), Boolean.TRUE);
            final List<Img> images = imagesByBody.getOrDefault(segment.id(), List.of());
            final String written = images.isEmpty() ? target : withAlts(target, images, aux, parsed);
            replacements.add(new TextReplacement(spanOf(segment.anchor()), written));
        }
    }

    /**
     * Puts each image's translated description inside the restored image in {@code target}. The k-th image of a
     * given source spelling is matched to the k-th occurrence of that spelling, so two identical images each get
     * their own description in document order.
     */
    private static String withAlts(String target, List<Img> images, Map<String, Segment> aux, ParsedMarkdown parsed) {
        final Map<String, Integer> cursors = new HashMap<>();
        final List<Edit> edits = new ArrayList<>();
        for (final Img img : images) {
            final String markup = text(parsed, img.slot().image());
            final int at = target.indexOf(markup, cursors.getOrDefault(markup, 0));
            if (at < 0) {
                log.debug("image {} not found in its paragraph's target; description not written", img.id());
                continue;
            }
            cursors.put(markup, at + markup.length());
            final Segment segment = aux.get(img.id());
            if (segment != null && segment.targetInner() != null) {
                edits.add(altEdit(at, img.slot(), segment.targetInner(), parsed));
                log.debug("image {} description written inside its paragraph's target", img.id());
            }
        }
        return substitute(target, edits);
    }

    private static Edit altEdit(int imageAt, MarkdownSlots.ImageSlot slot, String alt, ParsedMarkdown parsed) {
        final int before = text(
                        parsed,
                        new ByteSpanAnchor(
                                slot.image().startInclusive(), slot.alt().startInclusive()))
                .length();
        final int start = imageAt + before;
        return new Edit(start, start + text(parsed, slot.alt()).length(), escapeAlt(alt));
    }

    private static String substitute(String target, List<Edit> edits) {
        final StringBuilder out = new StringBuilder(target);
        for (int i = edits.size() - 1; i >= 0; i--) {
            out.replace(edits.get(i).start(), edits.get(i).end(), edits.get(i).text());
        }
        return out.toString();
    }

    private static void addFrontmatter(
            Map<String, Segment> aux, MarkdownSlots slots, List<TextReplacement> replacements) {
        slots.quotes().forEach((id, quote) -> {
            final Segment segment = aux.get(id);
            if (segment != null && segment.targetInner() != null) {
                final String written = quote.encode(segment.targetInner());
                log.debug("frontmatter value {} written, quote style {}", id, quote);
                replacements.add(new TextReplacement(spanOf(segment.anchor()), written));
            }
        });
    }

    private static void addImages(
            Map<String, Segment> aux,
            MarkdownSlots slots,
            Map<String, Boolean> bodyTargeted,
            List<TextReplacement> replacements) {
        slots.images().forEach((id, image) -> {
            final Segment segment = aux.get(id);
            final boolean isInsideTarget = image.enclosingId() != null && bodyTargeted.containsKey(image.enclosingId());
            if (segment != null && segment.targetInner() != null && !isInsideTarget) {
                log.debug("image {} description written as its own replacement", id);
                replacements.add(new TextReplacement(image.alt(), escapeAlt(segment.targetInner())));
            }
        });
    }

    private static void addLang(MarkdownSlots slots, String targetLanguage, List<TextReplacement> replacements) {
        if (slots.lang() != null) {
            log.debug("frontmatter lang replaced with {}", targetLanguage);
            replacements.add(new TextReplacement(slots.lang(), targetLanguage));
        } else {
            log.debug("no frontmatter lang value; none added");
        }
    }

    private static String escapeAlt(String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static String text(ParsedMarkdown parsed, ByteSpanAnchor span) {
        final Charset charset = parsed.charset();
        final byte[] bytes = parsed.originalBytes();
        return new String(bytes, span.startInclusive(), span.length(), charset);
    }

    /** A buffer skeleton can only be addressed by a byte span; any other anchor is a programming error. */
    static ByteSpanAnchor spanOf(SkeletonAnchor anchor) {
        return switch (anchor) {
            case ByteSpanAnchor span -> span;
            case ua.bookloom.api.document.NodeAnchor ignored ->
                throw new IllegalArgumentException("A buffer skeleton cannot be addressed by a node path");
            case ua.bookloom.api.document.AttributeAnchor ignored ->
                throw new IllegalArgumentException("A buffer skeleton cannot be addressed by an attribute");
        };
    }
}
