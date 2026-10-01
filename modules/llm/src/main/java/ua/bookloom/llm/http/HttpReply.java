package ua.bookloom.llm.http;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable wire response; retaining headers lets callers make purpose-specific decisions without losing retry data.
 *
 * @param status the response status, preserved even when it is not successful
 * @param headers the response headers, copied deeply so retry metadata remains immutable
 * @param body the response body, retained for dialect parsing and classification
 * @param cut whether a streamed body was cut short as a runaway, so the body ends without the provider's last part
 */
public record HttpReply(int status, Map<String, List<String>> headers, String body, boolean cut) {

    /** A whole reply, not cut. */
    public HttpReply(int status, Map<String, List<String>> headers, String body) {
        this(status, headers, body, false);
    }

    /** Copies both map and value lists so a response cannot be changed after it crosses the exchange boundary. */
    public HttpReply {
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(body, "body");
        final Map<String, List<String>> copiedHeaders = new HashMap<>();
        headers.forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
        headers = Map.copyOf(copiedHeaders);
    }
}
