package ua.bookloom.llm.http;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The error catalogue: one ordered list of rows read by the meaning of a reply's words. The engines' own rows come
 * first because they name a cause more closely; the broad context rows come last.
 */
// Checkstyle reads source before Lombok generates the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ErrorRows {

    static final List<ErrorRow> ORDERED = Stream.of(
                    OllamaRows.ROWS, LmStudioRows.ROWS, LlamaServerRows.ROWS, ContextRows.ROWS)
            .flatMap(List::stream)
            .toList();

    /**
     * The first row that explains a reply.
     *
     * @param status the reply's HTTP status
     * @param body the reply's body; searched, never logged here
     * @return the first matching row if one explains the reply, or empty if none does
     */
    static Optional<ErrorRow> match(final int status, final String body) {
        Objects.requireNonNull(body, "body");
        final Optional<ErrorRow> found =
                ORDERED.stream().filter(row -> row.matches(status, body)).findFirst();
        found.ifPresentOrElse(
                row -> log.debug(
                        "error row matched id={} reason={} code={} status={}",
                        row.id(),
                        row.reason().token(),
                        row.code(),
                        status),
                () -> log.debug("no error row matched status={} bodyLength={}", status, body.length()));
        return found;
    }

    /**
     * The first row that explains the text of a stream's error line, which has no status of its own; a row tied to
     * particular statuses is not considered.
     *
     * @param text the error line's text; searched, never logged here
     * @return the first matching row if one explains the text, or empty if none does
     */
    static Optional<ErrorRow> matchText(final String text) {
        Objects.requireNonNull(text, "text");
        final Optional<ErrorRow> found = ORDERED.stream()
                .filter(row ->
                        row.statuses().isEmpty() && row.body().matcher(text).find())
                .findFirst();
        found.ifPresentOrElse(
                row -> log.debug(
                        "error row matched id={} reason={} code={} streamed=true",
                        row.id(),
                        row.reason().token(),
                        row.code()),
                () -> log.debug("no error row matched streamed=true textLength={}", text.length()));
        return found;
    }
}
