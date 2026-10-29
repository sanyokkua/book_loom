package ua.bookloom.llm.http;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/**
 * One row of the error catalogue: what a provider's reply means, read from its words in any order, whatever the status.
 *
 * @param id the stable row name written to DEBUG diagnostics; unique across the catalogue
 * @param statuses the HTTP statuses the row applies to; empty means any status
 * @param body the words that identify the meaning, searched anywhere in the body
 * @param code the typed classification returned to callers
 * @param reason the token the failure carries in its details
 * @param retry what trying again can do for this failure
 * @param title a readable, body-independent error heading
 * @param message a readable, body-independent explanation
 */
record ErrorRow(
        String id,
        Set<Integer> statuses,
        Pattern body,
        ErrorCode code,
        FailureReason reason,
        RetryRule retry,
        String title,
        String message) {

    ErrorRow {
        Objects.requireNonNull(id, "id");
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(retry, "retry");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(message, "message");
    }

    /**
     * Whether this row explains a reply.
     *
     * @param status the reply's HTTP status
     * @param text the reply's body
     * @return {@code true} if the status is one the row applies to and the body holds its words, {@code false} otherwise
     */
    boolean matches(final int status, final String text) {
        return (statuses.isEmpty() || statuses.contains(status))
                && body.matcher(text).find();
    }
}
