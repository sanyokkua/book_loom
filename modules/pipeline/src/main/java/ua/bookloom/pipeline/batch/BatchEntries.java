package ua.bookloom.pipeline.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.prompt.JsonReplies;

/**
 * Pulls the {@code (id, text)} entries out of a reply as the model wrote them, repeats and wrong ids included: what to
 * make of them is the parser's decision. Tolerant of what small models wrap around the payload — prose, a code fence,
 * a bare array instead of the object, a number for an id — and of a reply cut off in its
 * last entry, which simply never closes and so is never read.
 */
@Slf4j
@RequiredArgsConstructor
final class BatchEntries {

    /** One entry of a reply. */
    record Entry(String id, String text) {}

    private static final Pattern JSON_ENTRY = Pattern.compile(
            "\\{\\s*\"id\"\\s*:\\s*(\"?)([^\",}\\s]+)\\1\\s*,\\s*\"target\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*[,}]");

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
        final JsonNode items = root.isArray() ? root : root.path("items");
        final List<Entry> entries = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(node -> entry(node, entries));
        }
        return entries;
    }

    /** The complete entries of a reply cut off before its JSON closed, as a model stopped by its cap leaves it. */
    private List<Entry> salvaged(final String reply) {
        final List<Entry> entries = new ArrayList<>();
        final Matcher matcher = JSON_ENTRY.matcher(reply);
        while (matcher.find()) {
            try {
                final String text = mapper.readValue("\"" + matcher.group(3) + "\"", String.class);
                entries.add(new Entry(matcher.group(2), text.strip()));
            } catch (JsonProcessingException ignored) {
                log.debug("Skipped a batch entry whose text is not a JSON string id={}", matcher.group(2));
            }
        }
        return entries;
    }

    private static void entry(final JsonNode node, final List<Entry> entries) {
        final JsonNode id = node.path("id");
        final JsonNode target = node.path("target");
        if ((id.isTextual() || id.isNumber()) && target.isTextual()) {
            entries.add(new Entry(id.asText().strip(), target.textValue().strip()));
        }
    }

    private static int firstOf(final String text, final char first, final char second) {
        final int a = text.indexOf(first);
        final int b = text.indexOf(second);
        return a < 0 ? b : b < 0 ? a : Math.min(a, b);
    }
}
