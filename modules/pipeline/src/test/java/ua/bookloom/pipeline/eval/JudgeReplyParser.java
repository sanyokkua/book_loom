package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.llm.response.ReasoningScrubber;
import ua.bookloom.pipeline.eval.JudgeReply.Flags;
import ua.bookloom.pipeline.eval.JudgeReply.JudgeError;
import ua.bookloom.pipeline.eval.JudgeReply.Preferred;
import ua.bookloom.pipeline.eval.JudgeReply.Scored;
import ua.bookloom.pipeline.eval.JudgeReply.Scores;
import ua.bookloom.pipeline.eval.JudgeReply.Severity;
import ua.bookloom.pipeline.eval.JudgeReply.Unparsed;
import ua.bookloom.pipeline.eval.JudgeReply.Verdict;
import ua.bookloom.pipeline.eval.JudgeReply.Winner;

/**
 * Reads a judge reply by code. It is tolerant of where the JSON object sits (after or inside a reasoning block, inside a
 * fence, with prose around it) and strict about what it holds: a missing or cut object, a score that is not a whole
 * number from 0 to 5, or an unknown verdict or winner makes the reply {@link Unparsed}; nothing is clamped or guessed.
 * An error whose span the translation does not hold is dropped, because the judge cannot point at words that are not
 * there.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JudgeReplyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_SCORE = 5;
    private static final String SCORES = "scores";
    private static final String WINNER = "winner";
    private static final List<String> SCORE_NAMES = List.of("meaning", "grammar", "terminology", "fluency");

    /** A single translation's judgement from {@code reply}, its error spans checked against {@code translation}. */
    static JudgeReply parsePointwise(final String reply, final String translation) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(translation, "translation");
        final JudgeReply parsed = object(reply, SCORES)
                .map(node -> pointwise(node, translation))
                .orElseGet(() -> new Unparsed("no complete JSON object with scores"));
        log.debug("Judge pointwise reply parsed length={} result={}", reply.length(), reasonOf(parsed));
        return parsed;
    }

    /** A comparison's answer from {@code reply}. */
    static JudgeReply parsePairwise(final String reply) {
        Objects.requireNonNull(reply, "reply");
        final JudgeReply parsed = object(reply, WINNER)
                .map(JudgeReplyParser::pairwise)
                .orElseGet(() -> new Unparsed("no complete JSON object with a winner"));
        log.debug("Judge pairwise reply parsed length={} result={}", reply.length(), reasonOf(parsed));
        return parsed;
    }

    private static String reasonOf(final JudgeReply reply) {
        return reply instanceof Unparsed unparsed ? "unparsed (" + unparsed.reason() + ")" : reply.describe();
    }

    private static JudgeReply pairwise(final JsonNode node) {
        final String word = node.path(WINNER).asText("");
        return Winner.of(word)
                .<JudgeReply>map(winner -> new Preferred(node.path("analysis").asText(""), winner))
                .orElseGet(() -> new Unparsed("unknown winner '" + word + "'"));
    }

    private static JudgeReply pointwise(final JsonNode node, final String translation) {
        final List<Integer> scores = new ArrayList<>();
        for (final String name : SCORE_NAMES) {
            final Optional<Integer> score = score(node.path(SCORES).path(name));
            if (score.isEmpty()) {
                return new Unparsed("score " + name + " is not a whole number from 0 to " + MAX_SCORE);
            }
            scores.add(score.get());
        }
        final String word = node.path("verdict").asText("");
        final Optional<Verdict> verdict = Verdict.of(word);
        if (verdict.isEmpty()) {
            return new Unparsed("unknown verdict '" + word + "'");
        }
        return new Scored(
                node.path("analysis").asText(""),
                errors(node.path("errors"), translation),
                new Scores(scores.get(0), scores.get(1), scores.get(2), scores.get(3)),
                flags(node.path("flags")),
                verdict.get());
    }

    private static Optional<Integer> score(final JsonNode value) {
        return value.isIntegralNumber() && value.asInt() >= 0 && value.asInt() <= MAX_SCORE
                ? Optional.of(value.asInt())
                : Optional.empty();
    }

    private static List<JudgeError> errors(final JsonNode errors, final String translation) {
        final List<JudgeError> kept = new ArrayList<>();
        errors.forEach(error -> kept(error, translation).ifPresent(kept::add));
        return kept;
    }

    private static Optional<JudgeError> kept(final JsonNode error, final String translation) {
        final String span = error.path("span").asText("");
        final Optional<Severity> severity = Severity.of(error.path("severity").asText(""));
        if (span.isEmpty() || !translation.contains(span)) {
            log.debug("Judge error dropped: span not in the translation spanLength={}", span.length());
            log.trace("Judge error dropped span={}", span);
            return Optional.empty();
        }
        if (severity.isEmpty()) {
            log.debug(
                    "Judge error dropped: unknown severity '{}'",
                    error.path("severity").asText(""));
            return Optional.empty();
        }
        return Optional.of(
                new JudgeError(span, severity.get(), error.path("class").asText("")));
    }

    private static Flags flags(final JsonNode flags) {
        return new Flags(
                flags.path("meaningError").asBoolean(false),
                flags.path("omission").asBoolean(false),
                flags.path("addition").asBoolean(false),
                flags.path("sourceLanguageResidue").asBoolean(false),
                flags.path("formattingBroken").asBoolean(false));
    }

    // The reply as the production scrubber leaves it first; when that holds no object (the model answered inside its
    // reasoning block) the raw reply is searched.
    private static Optional<JsonNode> object(final String reply, final String key) {
        return firstObject(ReasoningScrubber.scrub(reply), key).or(() -> firstObject(reply, key));
    }

    // The first complete JSON object holding `key`, from any `{` on; text after it is ignored. A cut object never
    // parses, and an inner object (an error item) lacks the key.
    private static Optional<JsonNode> firstObject(final String text, final String key) {
        for (int at = text.indexOf('{'); at >= 0; at = text.indexOf('{', at + 1)) {
            final Optional<JsonNode> node = read(text.substring(at));
            if (node.isPresent() && node.get().isObject() && node.get().has(key)) {
                return node;
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonNode> read(final String text) {
        try {
            return Optional.ofNullable(MAPPER.readTree(text));
        } catch (JsonProcessingException e) {
            log.trace("Judge reply candidate is not JSON: {}", e.getOriginalMessage());
            return Optional.empty();
        }
    }
}
