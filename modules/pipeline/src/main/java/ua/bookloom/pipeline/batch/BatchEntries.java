package ua.bookloom.pipeline.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.ControlCharacters;
import ua.bookloom.pipeline.prompt.JsonReplies;

/**
 * Pulls the {@code (id, text)} entries out of a reply as the model wrote them, repeats and wrong ids included: what to
 * make of them is the parser's decision. Tolerant of what small models wrap around the payload — prose, a code fence,
 * a bare array instead of the object, a number for an id — and of a reply cut off in its
 * last entry, which simply never closes and so is never read. A field it does not know, such as a {@code notes}
 * string a model wrote before its target or a {@code terms} object an older prompt asked for, is ignored.
 */
@Slf4j
@RequiredArgsConstructor
final class BatchEntries {

    private static final String TARGET = "target";

    /** One entry of a reply. */
    record Entry(String id, String text) {}

    private final ObjectMapper mapper;

    /** The entries of a reply in the order written; empty when the reply holds none. */
    List<Entry> read(final String reply) {
        Objects.requireNonNull(reply, "reply");
        final List<Entry> entries = json(reply);
        log.debug("Read batch reply replyLength={} entries={}", reply.length(), entries.size());
        return entries;
    }

    private List<Entry> json(final String reply) {
        final int start = firstOf(reply, '{', '[');
        if (start < 0) {
            return List.of();
        }
        final JsonNode root =
                JsonReplies.tolerant(mapper, reply.substring(start)).orElse(null);
        if (root == null) {
            return salvaged(reply);
        }
        final List<Entry> entries = new ArrayList<>();
        collect(root, entries);
        return entries;
    }

    // Every {"id", "target"} object counts wherever the model put it: a small model sometimes writes "items_2",
    // "items_3" beside "items", one object each, and each of them is a good draft.
    private static void collect(final JsonNode node, final List<Entry> entries) {
        if (isEntry(node)) {
            entry(node, entries);
        } else if (node.isContainerNode()) {
            node.forEach(child -> collect(child, entries));
        }
    }

    private static boolean isEntry(final JsonNode node) {
        final JsonNode id = node.path("id");
        return node.isObject()
                && (id.isTextual() || id.isNumber())
                && node.path(TARGET).isTextual();
    }

    /** The complete entries of a reply cut off before its JSON closed, as a model stopped by its cap leaves it. */
    private List<Entry> salvaged(final String reply) {
        final List<Entry> entries = new ArrayList<>();
        // Whatever follows the closing brace is not the reply (a runaway tail of markup): no entry is read from it.
        final OptionalInt end = JsonReplies.firstObjectEnd(reply);
        for (final RawEntries.Raw raw : RawEntries.find(end.isPresent() ? reply.substring(0, end.getAsInt()) : reply)) {
            try {
                final String text = mapper.readValue("\"" + raw.body() + "\"", String.class);
                entries.add(new Entry(raw.id(), ControlCharacters.stripSpacing(text)));
            } catch (JsonProcessingException _) {
                log.debug("Skipped a batch entry whose text is not a JSON string id={}", raw.id());
            }
        }
        return entries;
    }

    private static void entry(final JsonNode node, final List<Entry> entries) {
        entries.add(new Entry(
                node.path("id").asText().strip(),
                ControlCharacters.stripSpacing(node.path(TARGET).textValue())));
    }

    private static int firstOf(final String text, final char first, final char second) {
        final int a = text.indexOf(first);
        final int b = text.indexOf(second);
        if (a < 0 || b < 0) {
            return Math.max(a, b);
        }
        return Math.min(a, b);
    }
}
