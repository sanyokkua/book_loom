package ua.bookloom.document.md;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.node.Image;
import org.commonmark.node.Node;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.document.mask.MaskedContent;
import ua.bookloom.document.mask.PlainTextMasker;
import ua.bookloom.util.hash.HashUtil;
import ua.bookloom.util.text.VisibleText;

/**
 * Finds a Markdown file's auxiliary text (design.md D12): each frontmatter value that is text and each image's
 * description, all addressed by the byte span they occupy, so the writer splices them like body segments.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkdownAuxiliary {

    private static final String AUX_PREFIX = "aux:";
    private static final int IMAGE_OPENER_LENGTH = 2;
    private static final double INITIAL_CONFIDENCE = 0.0;

    /**
     * Where the file's two parts sit, so a character offset in either can be turned into a byte offset.
     *
     * @param split the frontmatter block and the body
     * @param charset the encoding the file was decoded with
     * @param bomLength the byte-order mark's length in bytes, before the block
     * @param bodyByteOffset the byte offset the body starts at
     * @param bodyByteOffsets the character-to-byte map over the body
     */
    @SuppressWarnings("ArrayRecordComponent") // deliberate: an internal, read-only offset map that is never compared.
    record Layout(Frontmatter.Split split, Charset charset, int bomLength, int bodyByteOffset, int[] bodyByteOffsets) {}

    /**
     * What the reader gets back.
     *
     * @param segments the auxiliary segments: frontmatter values first, then images, in document order
     * @param slots what the writer needs beyond the segments' anchors
     */
    record Collected(List<Segment> segments, MarkdownSlots slots) {}

    static Collected collect(String sourceName, Layout layout, Node bodyRoot, List<Segment> bodySegments) {
        final List<Draft> drafts = new ArrayList<>();
        final Map<String, YamlQuote> quotes = new LinkedHashMap<>();
        final ByteSpanAnchor lang = collectFrontmatter(layout, drafts, quotes);
        final Map<String, MarkdownSlots.ImageSlot> images = new LinkedHashMap<>();
        collectImages(sourceName, layout, bodyRoot, bodySegments, drafts, images);
        log.debug(
                "markdown auxiliary for {}: frontmatter values={} images={} lang replaceable={}",
                sourceName,
                quotes.size(),
                images.size(),
                lang != null);
        return new Collected(segmentsOf(drafts), new MarkdownSlots(quotes, images, lang));
    }

    private static @Nullable ByteSpanAnchor collectFrontmatter(
            Layout layout, List<Draft> drafts, Map<String, YamlQuote> quotes) {
        final String block = layout.split().block();
        if (block.isEmpty()) {
            return null;
        }
        final int[] offsets = MarkdownSpans.byteOffsets(block, layout.charset());
        ByteSpanAnchor lang = null;
        for (final FrontmatterScalars.Scalar scalar : FrontmatterScalars.scan(block)) {
            final ByteSpanAnchor span = scalar.start() < 0
                    ? null
                    : new ByteSpanAnchor(
                            layout.bomLength() + offsets[scalar.start()], layout.bomLength() + offsets[scalar.end()]);
            if ("lang".equals(scalar.key()) && span != null && lang == null) {
                lang = span;
            }
            addValue(scalar, span, drafts, quotes);
        }
        return lang;
    }

    private static void addValue(
            FrontmatterScalars.Scalar scalar,
            @Nullable ByteSpanAnchor span,
            List<Draft> drafts,
            Map<String, YamlQuote> quotes) {
        final String id = AUX_PREFIX + "fm:" + scalar.key();
        if (!scalar.isText() || span == null || quotes.containsKey(id)) {
            return;
        }
        quotes.put(id, scalar.quote());
        drafts.add(new Draft(id, SegmentKind.FRONTMATTER_VALUE, scalar.source(), span));
        log.trace("frontmatter value {} read: {}", id, scalar.source());
    }

    private static void collectImages(
            String sourceName,
            Layout layout,
            Node bodyRoot,
            List<Segment> bodySegments,
            List<Draft> drafts,
            Map<String, MarkdownSlots.ImageSlot> images) {
        final String body = layout.split().body();
        final List<Image> found = new ArrayList<>();
        findImages(bodyRoot, found);
        for (int k = 0; k < found.size(); k++) {
            final Image image = found.get(k);
            final int start = MarkdownSpans.firstSpanStart(image);
            final int end = MarkdownSpans.lastSpanEnd(image);
            final int altEnd = altEnd(body, start + IMAGE_OPENER_LENGTH, end);
            final String source = decodeEscapes(body.substring(start + IMAGE_OPENER_LENGTH, Math.max(altEnd, 0)));
            if (altEnd < 0 || VisibleText.isBlank(source)) {
                log.debug("image {} has no description; not produced", k);
                continue;
            }
            final ByteSpanAnchor alt = span(layout, start + IMAGE_OPENER_LENGTH, altEnd);
            final String id = AUX_PREFIX + "alt:" + sourceName + ":img" + k;
            drafts.add(new Draft(id, SegmentKind.ALT, source, alt));
            images.put(id, new MarkdownSlots.ImageSlot(span(layout, start, end), alt, enclosing(bodySegments, alt)));
            log.trace("image {} description read: {}", id, source);
        }
    }

    private static ByteSpanAnchor span(Layout layout, int charStart, int charEnd) {
        return new ByteSpanAnchor(
                layout.bodyByteOffset() + layout.bodyByteOffsets()[charStart],
                layout.bodyByteOffset() + layout.bodyByteOffsets()[charEnd]);
    }

    /** An image inside an image's description is part of that description, so the walk does not enter an image. */
    private static void findImages(Node parent, List<Image> found) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Image image) {
                if (!image.getSourceSpans().isEmpty()) {
                    found.add(image);
                }
            } else {
                findImages(child, found);
            }
        }
    }

    /** The index of the {@code ]} closing the description that starts at {@code from}, or {@code -1}. */
    private static int altEnd(String body, int from, int limit) {
        int depth = 1;
        for (int i = from; i < limit; i++) {
            final char c = body.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static String decodeEscapes(String raw) {
        final StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            final char c = raw.charAt(i);
            final boolean isEscape =
                    c == '\\' && i + 1 < raw.length() && AsciiPunctuation.isPunctuation(raw.charAt(i + 1));
            out.append(isEscape ? raw.charAt(++i) : c);
        }
        return out.toString();
    }

    private static @Nullable String enclosing(List<Segment> bodySegments, ByteSpanAnchor alt) {
        for (final Segment segment : bodySegments) {
            if (segment.anchor() instanceof ByteSpanAnchor span
                    && span.startInclusive() <= alt.startInclusive()
                    && alt.endExclusive() <= span.endExclusive()) {
                return segment.id();
            }
        }
        return null;
    }

    private static List<Segment> segmentsOf(List<Draft> drafts) {
        final List<Segment> segments = new ArrayList<>(drafts.size());
        for (int order = 0; order < drafts.size(); order++) {
            final Draft draft = drafts.get(order);
            final MaskedContent masked = PlainTextMasker.mask(draft.source());
            segments.add(new Segment(
                    draft.id(),
                    Unit.AUXILIARY_ID,
                    order,
                    draft.kind(),
                    draft.source(),
                    masked.masked(),
                    masked.placeholders(),
                    HashUtil.sha256OfNfcText(draft.source()),
                    order > 0 ? drafts.get(order - 1).id() : null,
                    order < drafts.size() - 1 ? drafts.get(order + 1).id() : null,
                    draft.anchor(),
                    null,
                    SegmentStatus.PENDING,
                    INITIAL_CONFIDENCE,
                    null,
                    masked.pairs(),
                    masked.lineBreakTokens()));
        }
        return segments;
    }

    private record Draft(String id, SegmentKind kind, String source, ByteSpanAnchor anchor) {}
}
