package ua.bookloom.document.md;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.ByteSpanAnchor;

/**
 * What the writer needs about the auxiliary segments beyond their anchors: how each frontmatter value was quoted,
 * where each image sits, and where the {@code lang} value is. A Markdown auxiliary segment is addressed by byte
 * span like a body segment, so this is the whole "slot table" of the format.
 *
 * @param quotes the quote style of each frontmatter value, by segment id
 * @param images each image with a non-empty description, by segment id, in document order
 * @param lang the span of the top-level {@code lang} value inside its quotes, or {@code null} when there is none
 */
record MarkdownSlots(
        Map<String, YamlQuote> quotes,
        Map<String, ImageSlot> images,
        @Nullable ByteSpanAnchor lang) {

    static final MarkdownSlots NONE = new MarkdownSlots(Map.of(), Map.of(), null);

    MarkdownSlots {
        Objects.requireNonNull(quotes, "quotes");
        Objects.requireNonNull(images, "images");
        quotes = Collections.unmodifiableMap(new LinkedHashMap<>(quotes));
        images = Collections.unmodifiableMap(new LinkedHashMap<>(images));
    }

    /**
     * One image whose description is a segment.
     *
     * @param image the whole image, {@code ![alt](destination)}
     * @param alt the description between the brackets, escapes included
     * @param enclosingId the id of the body segment whose span holds the image, or {@code null} when none does
     */
    record ImageSlot(
            ByteSpanAnchor image,
            ByteSpanAnchor alt,
            @Nullable String enclosingId) {

        ImageSlot {
            Objects.requireNonNull(image, "image");
            Objects.requireNonNull(alt, "alt");
        }
    }
}
