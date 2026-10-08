package ua.bookloom.pipeline.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The one tolerant reader of a model's JSON reply. Jackson reads the first JSON value and ignores any prose after
 * it, which is what models add; the draft reply stays strict in {@link DraftReplyParser} because it must tell a
 * malformed reply from a plain-text one.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonReplies {

    /**
     * Reads the first JSON value of a reply.
     *
     * @param mapper the mapper to read with
     * @param replyText the model's reply; never null
     * @return the value, or empty when the text is blank, is not JSON or holds no value
     */
    public static Optional<JsonNode> tolerant(final ObjectMapper mapper, final String replyText) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(replyText, "replyText");
        try {
            final JsonNode root = mapper.readTree(replyText);
            return root == null || root.isMissingNode() ? Optional.empty() : Optional.of(root);
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Finds where the first JSON object of a text closes, reading braces outside strings.
     *
     * @param text the text to scan; never null
     * @return the index just past the first object's closing brace, or empty when the text holds no object or it never
     *     closes
     */
    public static OptionalInt firstObjectEnd(final String text) {
        Objects.requireNonNull(text, "text");
        int depth = 0;
        boolean inString = false;
        for (int i = text.indexOf('{'); i >= 0 && i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                inString = c != '"';
                i += c == '\\' ? 1 : 0;
                continue;
            }
            inString = c == '"';
            depth += c == '{' ? 1 : c == '}' ? -1 : 0;
            if (depth == 0 && c == '}') {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }
}
