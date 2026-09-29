package ua.bookloom.pipeline.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.prompt.JsonReplies;

/**
 * Reads a judge reply the way the catalogue promises: absent {@code findings}/{@code deferrals} read as empty,
 * unknown fields are ignored, a label outside {@code s1}..{@code sk} or an unrecognised severity is dropped, and a
 * reply that cannot be parsed or whose {@code score} is missing or outside 0.0-1.0 reads as unreadable.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class JudgeReplyParser {

    private final ObjectMapper mapper;

    /** Parses one judge reply against the chunk's pairs, whose order maps {@code s1}..{@code sk} to segment ids. */
    public JudgeVerdict parse(final String replyText, final List<JudgedPair> pairs) {
        Objects.requireNonNull(replyText, "replyText");
        Objects.requireNonNull(pairs, "pairs");
        final Map<String, String> labelToSegmentId = labelToSegmentId(pairs);
        final JsonNode root = JsonReplies.tolerant(mapper, replyText).orElse(null);
        if (root == null || !hasValidScore(root)) {
            return JudgeVerdict.unreadable();
        }
        return new JudgeVerdict(
                root.path("score").asDouble(),
                textOrNull(root.path("verdict")),
                findings(root.path("findings"), labelToSegmentId),
                deferrals(root.path("deferrals"), labelToSegmentId),
                true);
    }

    private static boolean hasValidScore(final JsonNode root) {
        final JsonNode score = root.path("score");
        return score.isNumber() && score.asDouble() >= 0.0 && score.asDouble() <= 1.0;
    }

    private static @Nullable String textOrNull(final JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private static List<JudgeFinding> findings(final JsonNode array, final Map<String, String> labelToSegmentId) {
        if (!array.isArray()) {
            return List.of();
        }
        final List<JudgeFinding> findings = new ArrayList<>();
        for (final JsonNode node : array) {
            final String segmentId = labelToSegmentId.get(node.path("segmentId").asText(""));
            final Severity severity = severityOf(node.path("severity").asText(""));
            if (segmentId == null || severity == null) {
                log.debug("Dropped judge finding knownLabel={} knownSeverity={}", segmentId != null, severity != null);
                logTraceDropped("finding", node);
                continue;
            }
            findings.add(new JudgeFinding(
                    segmentId,
                    node.path("type").asText(""),
                    severity,
                    node.path("note").asText("")));
        }
        return findings;
    }

    private static List<JudgeDeferral> deferrals(final JsonNode array, final Map<String, String> labelToSegmentId) {
        if (!array.isArray()) {
            return List.of();
        }
        final List<JudgeDeferral> deferrals = new ArrayList<>();
        for (final JsonNode node : array) {
            final String segmentId = labelToSegmentId.get(node.path("segmentId").asText(""));
            if (segmentId == null) {
                log.debug("Dropped judge deferral knownLabel=false");
                logTraceDropped("deferral", node);
                continue;
            }
            deferrals.add(new JudgeDeferral(segmentId, node.path("reason").asText("")));
        }
        return deferrals;
    }

    private static void logTraceDropped(final String kind, final JsonNode node) {
        if (log.isTraceEnabled()) {
            log.trace("Dropped judge {} node={}", kind, node);
        }
    }

    private static @Nullable Severity severityOf(final String raw) {
        try {
            return Severity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Map<String, String> labelToSegmentId(final List<JudgedPair> pairs) {
        final Map<String, String> labels = new LinkedHashMap<>();
        for (int index = 0; index < pairs.size(); index++) {
            labels.put("s" + (index + 1), pairs.get(index).segmentId());
        }
        return labels;
    }
}
