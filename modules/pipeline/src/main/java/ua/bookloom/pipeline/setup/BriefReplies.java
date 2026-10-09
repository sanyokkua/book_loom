package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;

/**
 * Reads one sample's brief suggestion reply and keeps only the answers the sample supports. A neutral answer (a
 * neutral register, no narrator, an unknown gender, an empty phrase) needs no proof; any other needs a quote the code
 * finds in the sample it was given and a confidence of at least {@value #MIN_CONFIDENCE}, or it is no answer at all.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefReplies {

    /** An answer the model is less sure of than this is not kept. */
    static final double MIN_CONFIDENCE = 0.5;

    private static final double SURE = 1.0;
    private static final Set<String> NEUTRAL = Set.of("", "neutral", "unspecified", "unknown");

    /**
     * One supported answer of one sample.
     *
     * @param value the answer in the reply's vocabulary ({@code casual}, {@code first}, {@code female}) or the free text
     *     the model wrote, empty for none
     * @param quote the quote that proves it, empty for a neutral answer
     */
    record Answer(String value, String quote) {

        /** Rejects a missing part. */
        Answer {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(quote, "quote");
        }

        boolean isNeutral() {
            return NEUTRAL.contains(value);
        }
    }

    /**
     * The supported answers of a reply.
     *
     * @param reply the parsed reply
     * @param sample the sample the reply was given
     * @return the answer of each field the sample supports; never null, without a field whose answer it does not
     */
    static Map<BriefField, Answer> read(final JsonNode reply, final BookSample sample) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(sample, "sample");
        final Map<BriefField, Answer> answers = new EnumMap<>(BriefField.class);
        for (final BriefField field : BriefField.values()) {
            final JsonNode node = reply.get(wireName(field));
            if (node == null || node.isNull()) {
                log.debug("Brief reply has no {}", field);
                continue;
            }
            final Answer answer = new Answer(value(field, text(node.isObject() ? node.get("value") : node)), "");
            if (answer.isNeutral()) {
                log.debug("Brief reply {} is neutral", field);
                answers.put(field, answer);
                continue;
            }
            supported(field, answer, node, sample).ifPresent(kept -> answers.put(field, kept));
        }
        return answers;
    }

    private static Optional<Answer> supported(
            final BriefField field, final Answer answer, final JsonNode node, final BookSample sample) {
        final String quote = node.isObject() ? text(node.get("evidence")) : "";
        final double confidence = confidence(node);
        final boolean found = sample.holds(quote);
        log.debug("Brief reply {} confidence={} quoteFound={}", field, confidence, found);
        log.trace("Brief reply {} value '{}' quote '{}'", field, answer.value(), quote);
        if (!found || confidence < MIN_CONFIDENCE) {
            log.warn(
                    "Brief reply {} is not supported by its sample (quote found {}, confidence {})",
                    field,
                    found,
                    confidence);
            return Optional.empty();
        }
        return Optional.of(new Answer(answer.value(), quote));
    }

    // A model that leaves the confidence out is taken at its word; the quote check still holds it to the text.
    private static double confidence(final JsonNode node) {
        final JsonNode confidence = node.isObject() ? node.get("confidence") : null;
        if (confidence == null || confidence.isNull()) {
            return SURE;
        }
        return confidence.isNumber() ? confidence.asDouble() : confidence.asDouble(SURE);
    }

    private static String text(@Nullable final JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText().strip();
    }

    static String wireName(final BriefField field) {
        return switch (field) {
            case GENRE -> "genre";
            case REGISTER -> "register";
            case VOICE -> "voice";
            case AUDIENCE -> "audience";
            case NARRATOR -> "narrator";
            case NARRATOR_GENDER -> "narratorGender";
        };
    }

    // The fields with a closed vocabulary take only its words; anything else the model wrote is the neutral word.
    private static String value(final BriefField field, final String raw) {
        final String word = raw.toLowerCase(Locale.ROOT);
        return switch (field) {
            case GENRE, VOICE, AUDIENCE -> raw;
            case REGISTER ->
                switch (word) {
                    case "formal", "literary" -> "formal";
                    case "casual" -> "casual";
                    default -> "neutral";
                };
            case NARRATOR -> "first".equals(word) || "third".equals(word) ? word : "unspecified";
            case NARRATOR_GENDER -> "male".equals(word) || "female".equals(word) ? word : "unknown";
        };
    }
}
