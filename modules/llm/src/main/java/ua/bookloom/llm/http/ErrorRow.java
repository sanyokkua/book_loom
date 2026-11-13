package ua.bookloom.llm.http;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ServerEngine;

/**
 * One row of the error catalogue: what a provider's reply means, read from its words in any order, whatever the status.
 *
 * @param id the stable row name written to DEBUG diagnostics; unique across the catalogue
 * @param engines the server engines the row applies to; empty means every engine
 * @param statuses the HTTP statuses the row applies to; empty means any status
 * @param body the words that identify the meaning, searched anywhere in the body
 * @param condition what else the body must satisfy once {@code body} is found; for a meaning whose words must all be
 *     present in any order, or must not be accompanied by others
 * @param code the typed classification returned to callers
 * @param reason the token the failure carries in its details
 * @param retry what trying again can do for this failure
 * @param title a readable, body-independent error heading
 * @param message a readable, body-independent explanation
 */
record ErrorRow(
        String id,
        Set<ServerEngine> engines,
        Set<Integer> statuses,
        Pattern body,
        Predicate<String> condition,
        ErrorCode code,
        FailureReason reason,
        RetryRule retry,
        String title,
        String message) {

    /** A row whose words alone identify the meaning. */
    ErrorRow(
            String id,
            Set<ServerEngine> engines,
            Set<Integer> statuses,
            Pattern body,
            ErrorCode code,
            FailureReason reason,
            RetryRule retry,
            String title,
            String message) {
        this(id, engines, statuses, body, text -> true, code, reason, retry, title, message);
    }

    /** A row that applies to every engine. */
    ErrorRow(
            String id,
            Set<Integer> statuses,
            Pattern body,
            ErrorCode code,
            FailureReason reason,
            RetryRule retry,
            String title,
            String message) {
        this(id, Set.of(), statuses, body, code, reason, retry, title, message);
    }

    ErrorRow {
        Objects.requireNonNull(id, "id");
        engines = Set.copyOf(Objects.requireNonNull(engines, "engines"));
        statuses = Set.copyOf(Objects.requireNonNull(statuses, "statuses"));
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(condition, "condition");
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
     * @param engine the effective engine of the provider that answered
     * @return {@code true} if the engine and the status are ones the row applies to and the body holds its words,
     *     {@code false} otherwise
     */
    boolean matches(final int status, final String text, final ServerEngine engine) {
        return appliesTo(engine)
                && (statuses.isEmpty() || statuses.contains(status))
                && body.matcher(text).find()
                && condition.test(text);
    }

    boolean appliesTo(final ServerEngine engine) {
        return engines.isEmpty() || engines.contains(engine);
    }
}
