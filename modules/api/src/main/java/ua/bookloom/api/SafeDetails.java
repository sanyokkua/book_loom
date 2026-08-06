package ua.bookloom.api;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The only supported way to produce {@link AppError#details()}.
 *
 * <p>This is a privacy control expressed as a type rather than as a review rule. {@code details} is rendered into an
 * expandable panel in the UI and written to the log file, so anything reaching it has left the process's control.
 * The allowlist — HTTP status, endpoint host, model name, timeout, attempt count, QA finding names — is therefore
 * enforced twice over: it is exactly this record's <strong>component set</strong>, and it is exactly its
 * <strong>method set</strong>. There is deliberately no {@code withMessage(String)}, no {@code withException(...)},
 * and no {@code append(...)}, so a stack trace, a response body, or an {@code Authorization} header has no syntax to
 * arrive by.
 *
 * <p>Two further defences apply to the components that hold text at all. {@link #withEndpoint(URI)} keeps only the
 * URI's host, so credentials embedded as user-info and tokens carried in a query string are dropped structurally
 * rather than by pattern-matching. {@link #withModelName(String)} and {@link #withQaFindings(List)} flatten control
 * characters and cap length, so even a value passed to the wrong field cannot inject a newline into a log line or
 * dump a multi-kilobyte payload into a dialog.
 *
 * <p>Immutable and fluent: each {@code with…} returns a new instance, so a shared partially-built value cannot be
 * mutated by one caller under another. Usage: {@snippet lang = "java":
 * final String details = SafeDetails.empty()
 *         .withHttpStatus(429)
 *         .withEndpoint(URI.create("http://localhost:11434/api/chat"))
 *         .withAttempt(2, 3)
 *         .render();
 * }
 *
 * @param httpStatus the HTTP status of the failed call, or {@code null}
 * @param endpointHost the endpoint's host with every other URI part discarded, or {@code null}
 * @param modelName the model identifier, flattened and capped, or {@code null}
 * @param timeoutMillis the configured or elapsed timeout in milliseconds, or {@code null}
 * @param attempt the rendered {@code attempt/max} pair, or {@code null}
 * @param qaFindings the rendered QA finding names, or {@code null}
 */
public record SafeDetails(
        @Nullable Integer httpStatus,
        @Nullable String endpointHost,
        @Nullable String modelName,
        @Nullable Long timeoutMillis,
        @Nullable String attempt,
        @Nullable String qaFindings) {

    /** Longest text a single allowlisted field may contribute, in characters. */
    private static final int MAX_FIELD_LENGTH = 120;

    /** Most QA finding names rendered before the remainder is summarised as a count. */
    private static final int MAX_FINDINGS = 10;

    /** Matches any run of control or whitespace characters, which collapse to a single space. */
    private static final String CONTROL_OR_SPACE = "[\\p{Cntrl}\\s]+";

    /**
     * Starts from no details at all.
     *
     * @return an instance with every allowlisted field absent
     */
    public static SafeDetails empty() {
        return new SafeDetails(null, null, null, null, null, null);
    }

    /**
     * Records the HTTP status of the failed call.
     *
     * @param status the status code
     * @return a copy carrying the status
     */
    public SafeDetails withHttpStatus(int status) {
        return new SafeDetails(status, endpointHost, modelName, timeoutMillis, attempt, qaFindings);
    }

    /**
     * Records the endpoint's host, discarding every other part of the URI.
     *
     * <p>Only {@link URI#getHost()} is kept. User-info, path, query and fragment are dropped without being
     * inspected, because those are exactly where an API key ends up when someone pastes a full endpoint URL.
     *
     * @param endpoint the configured endpoint
     * @return a copy carrying the host, or this instance when the URI has none
     */
    public SafeDetails withEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        final String host = endpoint.getHost();
        return host == null
                ? this
                : new SafeDetails(httpStatus, sanitize(host), modelName, timeoutMillis, attempt, qaFindings);
    }

    /**
     * Records the model involved in the failure.
     *
     * @param model the model identifier
     * @return a copy carrying the model name
     */
    public SafeDetails withModelName(String model) {
        Objects.requireNonNull(model, "model");
        return new SafeDetails(httpStatus, endpointHost, sanitize(model), timeoutMillis, attempt, qaFindings);
    }

    /**
     * Records the timeout that elapsed or was configured.
     *
     * @param timeout the timeout
     * @return a copy carrying the timeout in milliseconds
     */
    public SafeDetails withTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        return new SafeDetails(httpStatus, endpointHost, modelName, timeout.toMillis(), attempt, qaFindings);
    }

    /**
     * Records which attempt failed, out of how many were permitted.
     *
     * @param attemptNumber the one-based attempt number
     * @param maxAttempts the attempt budget
     * @return a copy carrying the attempt pair
     */
    public SafeDetails withAttempt(int attemptNumber, int maxAttempts) {
        return new SafeDetails(
                httpStatus, endpointHost, modelName, timeoutMillis, attemptNumber + "/" + maxAttempts, qaFindings);
    }

    /**
     * Records the names of the QA findings that failed a gate.
     *
     * <p>Finding <em>names</em> only — never the text they were found in, which is book content.
     *
     * @param findingNames the finding names
     * @return a copy carrying the rendered names, or this instance when the list is empty
     */
    public SafeDetails withQaFindings(List<String> findingNames) {
        Objects.requireNonNull(findingNames, "findingNames");
        if (findingNames.isEmpty()) {
            return this;
        }
        final String rendered = findingNames.stream()
                .limit(MAX_FINDINGS)
                .map(SafeDetails::sanitize)
                .collect(Collectors.joining(","));
        final int overflow = findingNames.size() - MAX_FINDINGS;
        final String summarised = overflow > 0 ? rendered + ",+" + overflow + " more" : rendered;
        return new SafeDetails(httpStatus, endpointHost, modelName, timeoutMillis, attempt, summarised);
    }

    /**
     * Renders the recorded fields in component order.
     *
     * <p>Returns {@code null} rather than an empty string when nothing was recorded, so the result can be handed
     * straight to {@link AppError}'s nullable {@code details} component without a caller-side emptiness check.
     *
     * @return the rendered detail string, or {@code null} when no field is set
     */
    public @Nullable String render() {
        final List<String> parts = new ArrayList<>(6);
        append(parts, "httpStatus", httpStatus);
        append(parts, "endpointHost", endpointHost);
        append(parts, "model", modelName);
        append(parts, "timeoutMs", timeoutMillis);
        append(parts, "attempt", attempt);
        append(parts, "qaFindings", qaFindings);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void append(List<String> parts, String key, @Nullable Object value) {
        if (value != null) {
            parts.add(key + "=" + value);
        }
    }

    /**
     * Flattens a value to one bounded, single-line token.
     *
     * <p>Not a filter for secrets — the component set is what keeps secrets out. This bounds the blast radius of a
     * value passed to the wrong field: a newline would otherwise break a log line into two, and an unbounded value
     * would turn an error dialog into a data dump.
     */
    private static String sanitize(String value) {
        final String flattened = value.replaceAll(CONTROL_OR_SPACE, " ").trim();
        return flattened.length() <= MAX_FIELD_LENGTH ? flattened : flattened.substring(0, MAX_FIELD_LENGTH) + "…";
    }
}
