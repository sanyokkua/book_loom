package ua.bookloom.pipeline.reviewer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.JsonReplies;

/**
 * Reads a reviewer reply tolerantly, because a small model wraps and misspells: unknown fields are ignored, a label
 * outside {@code s1}..{@code sk} or a status that names nothing drops that answer, an edit with no quote or that
 * changes nothing is dropped, and a criterion that names nothing reads as a style note, which is never applied. A
 * reply that holds no {@code results} list at all is unreadable. This class decides nothing about whether an edit is
 * right: {@link EditVerifier} does.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ReviewReplyParser {

    private final ObjectMapper mapper;

    /**
     * Parses one reply against the batch it answers.
     *
     * @param replyText the reply as the model sent it; never null
     * @param pairs the batch's pairs, whose order maps {@code s1}..{@code sk} to segment ids; never null
     * @return the readable verdict, whose items are in the reply's order, or an unreadable one
     */
    public ReviewVerdict parse(final String replyText, final List<ReviewedPair> pairs) {
        Objects.requireNonNull(replyText, "replyText");
        Objects.requireNonNull(pairs, "pairs");
        final JsonNode root =
                JsonReplies.tolerant(mapper, fromFirstBracket(replyText)).orElse(null);
        final JsonNode results = root == null ? null : root.isArray() ? root : root.path("results");
        if (results == null || !results.isArray()) {
            log.debug("Reviewer reply holds no results list readable={}", false);
            return ReviewVerdict.unreadable();
        }
        return verdictOf(results, pairs);
    }

    /**
     * Reads a reply the model's output cap cut off: every complete entry of the {@code results} list is kept and the
     * entry the cut fell in, and anything after it, is left unread. Where {@link #parse} calls a reply with no closed
     * JSON unreadable, a cut reply is readable here, possibly with no entry.
     *
     * @param replyText the reply as the model sent it; never null
     * @param pairs the batch's pairs, whose order maps {@code s1}..{@code sk} to segment ids; never null
     * @return a readable verdict of the complete entries; the pairs it names no answer for are the unread ones
     */
    public ReviewVerdict parseSalvaging(final String replyText, final List<ReviewedPair> pairs) {
        Objects.requireNonNull(replyText, "replyText");
        Objects.requireNonNull(pairs, "pairs");
        final List<JsonNode> entries = completeEntries(fromFirstBracket(replyText));
        final ReviewVerdict verdict = verdictOf(entries, pairs);
        log.debug(
                "Salvaged a cut reviewer reply answers={} of={}",
                verdict.items().size(),
                pairs.size());
        return verdict;
    }

    private List<JsonNode> completeEntries(final String text) {
        final List<JsonNode> entries = new ArrayList<>();
        try (JsonParser reader = mapper.getFactory().createParser(text)) {
            if (enterResults(reader)) {
                while (reader.nextToken() == JsonToken.START_OBJECT) {
                    entries.add(mapper.readTree(reader));
                }
            }
        } catch (IOException ignored) {
            log.debug("The reviewer reply ends inside an entry; the entries before it are kept");
        }
        return entries;
    }

    // Positions the reader just before the first entry of the results array (or of a bare array).
    private static boolean enterResults(final JsonParser reader) throws IOException {
        JsonToken token = reader.nextToken();
        if (token == JsonToken.START_ARRAY) {
            return true;
        }
        if (token != JsonToken.START_OBJECT) {
            return false;
        }
        while ((token = reader.nextToken()) == JsonToken.FIELD_NAME) {
            final boolean results = "results".equals(reader.currentName());
            token = reader.nextToken();
            if (results && token == JsonToken.START_ARRAY) {
                return true;
            }
            reader.skipChildren();
        }
        return false;
    }

    private ReviewVerdict verdictOf(final Iterable<JsonNode> results, final List<ReviewedPair> pairs) {
        final Map<String, String> labels = labelToSegmentId(pairs);
        final Map<String, ReviewItem> bySegment = new LinkedHashMap<>();
        for (final JsonNode node : results) {
            final ReviewItem item = item(node, labels);
            if (item != null && bySegment.putIfAbsent(item.segmentId(), item) != null) {
                log.debug("Dropped a repeated reviewer answer segmentId={}", item.segmentId());
            }
        }
        log.debug("Read reviewer reply answers={} of={}", bySegment.size(), pairs.size());
        return ReviewVerdict.answered(List.copyOf(bySegment.values()));
    }

    // A model that was not held to a schema wraps its JSON in prose or a code fence; reading starts at the JSON.
    private static String fromFirstBracket(final String text) {
        final int object = text.indexOf('{');
        final int array = text.indexOf('[');
        final int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
        return start < 0 ? text : text.substring(start);
    }

    private @Nullable ReviewItem item(final JsonNode node, final Map<String, String> labels) {
        final String segmentId = labels.get(node.path("id").asText(""));
        final ReviewStatus status =
                ReviewStatus.of(node.path("status").asText("")).orElse(null);
        if (segmentId == null || status == null) {
            log.debug("Dropped a reviewer answer knownLabel={} knownStatus={}", segmentId != null, status != null);
            return null;
        }
        return switch (status) {
            case OK -> ReviewItem.ok(segmentId);
            case EDITS -> editsItem(segmentId, node.path("edits"));
            case REWRITE -> rewriteItem(segmentId, node.path("rewrite"));
        };
    }

    private static ReviewItem editsItem(final String segmentId, final JsonNode array) {
        final List<ReviewEdit> edits = new ArrayList<>();
        if (array.isArray()) {
            for (final JsonNode node : array) {
                final ReviewEdit edit = edit(node);
                if (edit != null) {
                    edits.add(edit);
                }
            }
        }
        if (edits.isEmpty()) {
            log.debug("A reviewer answer of edits holds no usable edit, read as ok segmentId={}", segmentId);
            return ReviewItem.ok(segmentId);
        }
        return new ReviewItem(segmentId, ReviewStatus.EDITS, edits, null);
    }

    private static ReviewItem rewriteItem(final String segmentId, final JsonNode text) {
        if (!text.isTextual() || text.asText().isBlank()) {
            log.debug("A reviewer answer of rewrite holds no text, read as ok segmentId={}", segmentId);
            return ReviewItem.ok(segmentId);
        }
        return new ReviewItem(segmentId, ReviewStatus.REWRITE, List.of(), text.asText());
    }

    private static @Nullable ReviewEdit edit(final JsonNode node) {
        final String quote = node.path("quote").asText("");
        final String replacement = node.path("replacement").asText("");
        if (quote.isBlank() || quote.equals(replacement)) {
            log.debug("Dropped a reviewer edit with no quote or no change");
            return null;
        }
        final ReviewCriterion criterion =
                ReviewCriterion.of(node.path("criterion").asText("")).orElse(ReviewCriterion.STYLE);
        return new ReviewEdit(criterion, quote, replacement);
    }

    private static Map<String, String> labelToSegmentId(final List<ReviewedPair> pairs) {
        final Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < pairs.size(); index++) {
            labels.put(ReviewerLabels.of(index), pairs.get(index).segmentId());
        }
        return labels;
    }
}
