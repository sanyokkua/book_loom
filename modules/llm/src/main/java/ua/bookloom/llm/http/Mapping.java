package ua.bookloom.llm.http;

import org.jspecify.annotations.Nullable;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * Captures one classification so the chosen code and explanation cannot drift apart.
 *
 * @param code the sole typed classification returned to callers
 * @param row the stable row name written to DEBUG diagnostics
 * @param title a readable, body-independent error heading
 * @param message a readable, body-independent explanation
 * @param reason the token the failure carries; {@link FailureReason#UNCLASSIFIED} when no catalogue row matched
 */
record Mapping(ErrorCode code, String row, String title, String message, FailureReason reason) {

    static Mapping of(ErrorRow row, String body, @Nullable String modelName) {
        return new Mapping(row.code(), row.id(), row.title(), RowWords.fill(row, body, modelName), row.reason());
    }

    static Mapping plain(ErrorCode code, String row, String title, String message) {
        return new Mapping(code, row, title, message, FailureReason.UNCLASSIFIED);
    }
}
