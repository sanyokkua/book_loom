package ua.bookloom.pipeline.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.ControlCharacters;

/** Reads only the strict one-segment response object so wrapper prose never becomes translated book text. */
@Slf4j
public final class DraftReplyParser {

    private static final String INVALID_OBJECT = "was not one JSON object with exactly a nonblank target field";
    private static final String CONTROL_CHARACTERS =
            "held control characters; write quote marks and dashes as « » “ ” — themselves";
    private final ObjectMapper mapper;

    /** Creates a parser with the application's tolerant JSON mapper; Guice builds it for the self-heal calls. */
    @Inject
    public DraftReplyParser(final ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Parses the complete reply as the sole documented target object.
     *
     * @param replyText the model's whole reply
     * @param source the masked source text the reply translates; a control character it holds may come back as often
     *     as it holds it (an old TXT's form feed), any other is refused
     * @return the parsed target, or the diagnostic of a reply outside the contract
     */
    public ParsedReply parse(final String replyText, final String source) {
        Objects.requireNonNull(replyText, "replyText");
        Objects.requireNonNull(source, "source");
        log.debug("Parsing draft reply replyLength={}", replyText.length());
        try {
            final JsonNode root = mapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(replyText);
            if (validTarget(root)
                    && ControlCharacters.addsControl(source, root.path("target").textValue())) {
                return logged(invalid(CONTROL_CHARACTERS));
            }
            return logged(validTarget(root) ? structured(root.path("target").textValue()) : invalid(INVALID_OBJECT));
        } catch (JsonProcessingException ignored) {
            return logged(invalid("was not valid JSON"));
        }
    }

    private static boolean validTarget(final JsonNode root) {
        return root != null
                && root.isObject()
                && root.size() == 1
                && root.path("target").isTextual()
                && !root.path("target").textValue().isBlank();
    }

    private static ParsedReply structured(final String translation) {
        return new ParsedReply(ReplyKind.STRUCTURED, translation, "");
    }

    private static ParsedReply invalid(final String diagnostic) {
        return new ParsedReply(ReplyKind.INVALID_STRUCTURED, "", diagnostic);
    }

    private ParsedReply logged(final ParsedReply parsed) {
        log.debug(
                "Parsed draft reply kind={} translationLength={}",
                parsed.kind(),
                parsed.translation().length());
        if (log.isTraceEnabled() && parsed.kind() == ReplyKind.STRUCTURED) {
            log.trace("Parsed draft translation={}", parsed.translation());
        }
        return parsed;
    }

    /** The outcome determines whether a segment can be accepted directly or needs one structural repair call. */
    public enum ReplyKind {
        /** A valid target object was parsed. */
        STRUCTURED,

        /** Any reply outside the exact target-object contract. */
        INVALID_STRUCTURED
    }

    /** The parsed target, or a user-safe diagnostic for the structural repair instruction. */
    public record ParsedReply(ReplyKind kind, String translation, String diagnostic) {

        /** Rejects missing classification, target text, or diagnostic values. */
        public ParsedReply {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(translation, "translation");
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }
}
