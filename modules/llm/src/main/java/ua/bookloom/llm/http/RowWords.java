package ua.bookloom.llm.http;

import java.util.Objects;
import java.util.regex.Matcher;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * Fills the {@code {name}} placeholders of a row's message. Only the row pattern's named groups (digit-only by
 * construction) and the model name the caller chose are inserted, so no sentence of the server reaches a message.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RowWords {

    private static final String MODEL = "model";
    private static final String MODEL_FALLBACK = "<model>";

    /**
     * Words a row's message for one reply.
     *
     * @param row the matched row
     * @param body the text the row matched; searched, never logged
     * @param modelName the model the request named, if known
     * @return the row's message with each {@code {group}} and {@code {model}} placeholder filled; a placeholder with
     *     no value stays as written
     */
    static String fill(final ErrorRow row, final String body, final @Nullable String modelName) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(body, "body");
        String message = row.message().replace("{" + MODEL + "}", modelName == null ? MODEL_FALLBACK : modelName);
        if (row.body().namedGroups().isEmpty()) {
            return message;
        }
        final Matcher matcher = row.body().matcher(body);
        if (matcher.find()) {
            for (final String group : row.body().namedGroups().keySet()) {
                final String value = matcher.group(group);
                message = value == null ? message : message.replace("{" + group + "}", value);
            }
        }
        return message;
    }
}
