package ua.bookloom.pipeline.batch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.ControlCharacters;
import ua.bookloom.pipeline.batch.BatchEntries.Entry;

/**
 * Splits the plain-text reply of the {@link BatchFormat#MARKERS} arm at its {@code ⟦sN⟧} markers. Every marker is a
 * boundary, so no marker survives into a text; an id whose marker is written twice, or after the marker of a later
 * item, is dropped here and so reads as missing, which the missing-ids re-ask then picks up.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class MarkerReplies {

    /** A marker as written, and the text that follows it up to the next marker. */
    private record Part(String id, String text) {}

    /**
     * Reads a reply.
     *
     * @param reply the non-null reply text
     * @param items the non-null batch items in order, whose ids the markers name
     * @return the entries in the order written, without the dropped ids; empty when the reply holds no marker
     */
    static List<Entry> read(final String reply, final List<BatchItem> items) {
        final List<Part> parts = split(reply);
        if (parts.isEmpty()) {
            log.debug("Marked reply holds no marker replyLength={}", reply.length());
            return List.of();
        }
        final Map<String, Integer> counts = new HashMap<>();
        parts.forEach(part -> counts.merge(part.id(), 1, Integer::sum));
        final List<String> ids = items.stream().map(BatchItem::id).toList();
        final List<Entry> entries = new ArrayList<>();
        int latest = -1;
        for (final Part part : parts) {
            final int at = ids.indexOf(part.id());
            final boolean isDoubled = counts.getOrDefault(part.id(), 0) > 1;
            final boolean isReordered = at >= 0 && at < latest;
            latest = Math.max(latest, at);
            if (isDoubled || isReordered) {
                log.warn("Marker dropped id={} doubled={} reordered={}", part.id(), isDoubled, isReordered);
            } else {
                entries.add(new Entry(part.id(), part.text()));
            }
        }
        log.debug("Read marked reply markers={} entries={}", parts.size(), entries.size());
        return entries;
    }

    private static List<Part> split(final String reply) {
        final Matcher marker = BatchMarkers.MARKER.matcher(reply);
        final List<Part> parts = new ArrayList<>();
        String id = null;
        int textFrom = 0;
        while (marker.find()) {
            if (id == null) {
                dropPreamble(reply.substring(0, marker.start()));
            } else {
                parts.add(part(id, reply.substring(textFrom, marker.start())));
            }
            id = marker.group(1);
            textFrom = marker.end();
        }
        if (id != null) {
            parts.add(part(id, reply.substring(textFrom)));
        }
        return parts;
    }

    private static Part part(final String id, final String text) {
        return new Part(id, ControlCharacters.stripSpacing(text));
    }

    private static void dropPreamble(final String before) {
        if (!before.isBlank()) {
            log.warn("Text before the first marker dropped length={}", before.length());
            log.trace("Dropped text before the first marker: {}", before);
        }
    }
}
