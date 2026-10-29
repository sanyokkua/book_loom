package ua.bookloom.api;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The only supported way to produce {@link AppError#details()}.
 *
 * <p>This is a privacy control expressed as a type rather than as a review rule. {@code details} is rendered into an
 * expandable panel in the UI and written to the log file, so anything reaching it has left the process's control.
 * The allowlist — a failure reason token, HTTP status, endpoint host, model name, timeout, attempt count, QA finding
 * names, the expected/observed placeholder multisets, a folder's own name and the prompt and window token counts of
 * a prompt that did not fit, the engine found at an address — is therefore enforced twice over: it is exactly this record's
 * <strong>component set</strong>, and it is exactly its <strong>method set</strong>. There is deliberately no
 * {@code withMessage(String)}, no {@code withException(...)}, and no {@code append(...)}, so a stack trace, a
 * response body, or an {@code Authorization} header has no syntax to arrive by.
 *
 * <p>Two further defences apply to the components that hold text at all. {@link #withEndpoint(URI)} keeps only the
 * URI's host and port, so credentials embedded as user-info and tokens carried in a query string are dropped structurally
 * rather than by pattern-matching. {@link #withModelName(String)} and {@link #withQaFindings(List)} flatten control
 * characters and cap length, so even a value passed to the wrong field cannot inject a newline into a log line or
 * dump a multi-kilobyte payload into a dialog. {@link #withPlaceholderMultiset(List, List)} is the one exception to
 * the length cap, and says why on its own Javadoc — its <em>observed</em> side comes from a provider response
 * rather than from this system, so that side is bounded by token count and per-token length instead.
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
 * @param reason the {@link FailureReason#token()} of why the failure happened, or {@code null}; rendered first, so
 *     {@link FailureReason#in(String)} can read it back
 * @param httpStatus the HTTP status of the failed call, or {@code null}
 * @param endpointHost the endpoint's host, with its port when one is given, and every other URI part discarded, or {@code null}
 * @param modelName the model identifier, flattened and capped, or {@code null}
 * @param timeoutMillis the configured or elapsed timeout in milliseconds, or {@code null}
 * @param attempt the rendered {@code attempt/max} pair, or {@code null}
 * @param qaFindings the rendered QA finding names, or {@code null}
 * @param expectedPlaceholders the space-joined expected placeholder tokens, or {@code null}
 * @param observedPlaceholders the space-joined observed placeholder tokens, or {@code null}
 * @param folderName the last name of the folder a file was to be written into, flattened and capped — never the
 *     folder's path, which can name the person — or {@code null}
 * @param promptTokens the tokens a request needed, as the provider stated them for a prompt that did not fit, or
 *     {@code null}
 * @param windowTokens the tokens the model's window holds, as the provider stated them, or {@code null}
 * @param engine the {@code ServerEngine} constant name of the server found at the address, or {@code null}; only
 *     an upper-case constant name is taken, so no reply text can arrive through it
 */
public record SafeDetails(
        @Nullable String reason,
        @Nullable Integer httpStatus,
        @Nullable String endpointHost,
        @Nullable String modelName,
        @Nullable Long timeoutMillis,
        @Nullable String attempt,
        @Nullable String qaFindings,
        @Nullable String expectedPlaceholders,
        @Nullable String observedPlaceholders,
        @Nullable String folderName,
        @Nullable Integer promptTokens,
        @Nullable Integer windowTokens,
        @Nullable String engine) {

    /** Most QA finding names rendered before the remainder is summarised as a count. */
    private static final int MAX_FINDINGS = 10;

    /** The shape of an enum constant's name, the only text the engine field takes. */
    private static final Pattern CONSTANT_NAME = Pattern.compile("[A-Z][A-Z_]{0,31}");

    /** The rendered model field: its text runs to the next rendered key or the end. */
    private static final Pattern MODEL_IN_DETAILS = Pattern.compile("(?:\\A|, )model=(.+?)(?=, [A-Za-z]+=|\\z)");

    /**
     * Reads back the model {@link #withModelName(String)} recorded, so the window can name the id in its own words.
     *
     * @param details the rendered details, or {@code null}
     * @return the model id if the details name one, or empty if they do not
     */
    public static Optional<String> modelIn(@Nullable String details) {
        if (details == null) {
            return Optional.empty();
        }
        final Matcher found = MODEL_IN_DETAILS.matcher(details);
        return found.find() ? Optional.of(found.group(1)) : Optional.empty();
    }

    /**
     * Starts from no details at all.
     *
     * @return an instance with every allowlisted field absent
     */
    public static SafeDetails empty() {
        return new SafeDetails(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Records the HTTP status of the failed call.
     *
     * @param status the status code
     * @return a copy carrying the status
     */
    public SafeDetails withHttpStatus(int status) {
        return new SafeDetails(
                reason,
                status,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
    }

    /**
     * Records the endpoint's host and port, discarding every other part of the URI.
     *
     * <p>Only {@link URI#getHost()} and {@link URI#getPort()} are kept. User-info, path, query and fragment are dropped without being
     * inspected, because those are exactly where an API key ends up when someone pastes a full endpoint URL.
     *
     * @param endpoint the configured endpoint
     * @return a copy carrying the host, or this instance when the URI has none
     */
    public SafeDetails withEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        final String host = endpoint.getHost();
        final int port = endpoint.getPort();
        return host == null
                ? this
                : new SafeDetails(
                        reason,
                        httpStatus,
                        SafeText.flatten(port < 0 ? host : host + ":" + port),
                        modelName,
                        timeoutMillis,
                        attempt,
                        qaFindings,
                        expectedPlaceholders,
                        observedPlaceholders,
                        folderName,
                        promptTokens,
                        windowTokens,
                        engine);
    }

    /**
     * Records the model involved in the failure.
     *
     * @param model the model identifier
     * @return a copy carrying the model name
     */
    public SafeDetails withModelName(String model) {
        Objects.requireNonNull(model, "model");
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                SafeText.flatten(model),
                timeoutMillis,
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
    }

    /**
     * Records the timeout that elapsed or was configured.
     *
     * @param timeout the timeout
     * @return a copy carrying the timeout in milliseconds
     */
    public SafeDetails withTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeout.toMillis(),
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
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
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attemptNumber + "/" + maxAttempts,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
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
        final String rendered =
                findingNames.stream().limit(MAX_FINDINGS).map(SafeText::flatten).collect(Collectors.joining(","));
        final int overflow = findingNames.size() - MAX_FINDINGS;
        final String summarised = overflow > 0 ? rendered + ",+" + overflow + " more" : rendered;
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                summarised,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
    }

    /**
     * Records the placeholder-multiset comparison's expected and observed tokens.
     *
     * <p>Two fields, not one blended string: the repair tier at change 16 recovers each list with the same
     * {@code ⟦g\d+⟧} regex the gate itself uses, so each field must be a pure run of tokens a caller can scan back
     * into a list, never prose that mixes "expected" and "observed" together.
     *
     * <p><strong>The two sides have different origins, so they get different treatment.</strong> {@code expected}
     * is scanned out of a segment's own masked form, which this system produced, so every token in it is
     * well-formed by construction and the list is as long as the book's own markup made it — it renders in full,
     * bypassing {@link SafeText#flatten(String)}, because truncating it is the exact defect this component exists to
     * remove: a forty-placeholder segment renders at roughly 240 characters, and the 120-character cap would
     * silently drop half the tokens the repair tier needs to ask for.
     *
     * <p>{@code observed} is scanned out of a <em>provider response</em>. That reasoning does not transfer: the
     * token grammar's digit run is unbounded, so one degenerate token measured 200,003 characters and rendered a
     * 200-KB error dialog and a 200-KB log line, and 50,000 tokens rendered 438,937 characters. It is therefore
     * bounded by count and by per-token length, with any excess stated rather than dropped in silence. The
     * character cap still does not apply — it is the volume that needed bounding, not the spelling.
     *
     * @param expected the expected placeholder tokens, in order; rendered in full
     * @param observed the observed placeholder tokens, in order — an empty list here (against a non-empty
     *     {@code expected}) is itself a meaningful failure shape (the model returned no tokens at all) and still
     *     renders, as an empty {@code observedPlaceholders} field
     * @return a copy carrying both token lists, or this instance when both lists are empty
     */
    public SafeDetails withPlaceholderMultiset(List<String> expected, List<String> observed) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(observed, "observed");
        if (expected.isEmpty() && observed.isEmpty()) {
            return this;
        }
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                qaFindings,
                String.join(" ", expected),
                SafeText.observed(observed),
                folderName,
                promptTokens,
                windowTokens,
                engine);
    }

    /**
     * Records why the failure happened, where its code alone cannot say what to fix.
     *
     * @param why the reason
     * @return a copy carrying the reason's token
     */
    public SafeDetails withReason(FailureReason why) {
        Objects.requireNonNull(why, "why");
        return new SafeDetails(
                why.token(),
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engine);
    }

    /**
     * Records the folder a file was to be written into, by its last name only.
     *
     * @param folder the folder; only {@link Path#getFileName()} is kept
     * @return a copy carrying the folder's name, or this instance when the path has no name (a file system root)
     */
    public SafeDetails withFolder(Path folder) {
        Objects.requireNonNull(folder, "folder");
        final Path name = folder.getFileName();
        return name == null
                ? this
                : new SafeDetails(
                        reason,
                        httpStatus,
                        endpointHost,
                        modelName,
                        timeoutMillis,
                        attempt,
                        qaFindings,
                        expectedPlaceholders,
                        observedPlaceholders,
                        SafeText.flatten(name.toString()),
                        promptTokens,
                        windowTokens,
                        engine);
    }

    /**
     * Records the two counts a provider stated for a prompt that did not fit the model's window, so the flag can say
     * "696 of 512 tokens". Typed integers only: the provider's sentence they were read from never enters the details.
     *
     * @param prompt the tokens the request needed; zero or more
     * @param window the tokens the model's window holds; zero or more
     * @return a copy carrying both counts
     * @throws IllegalArgumentException if either count is negative
     */
    public SafeDetails withTokenCounts(int prompt, int window) {
        if (prompt < 0 || window < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                prompt,
                window,
                engine);
    }

    /**
     * Records which engine answers at the provider's address, so a test can name it in any display language. The
     * root package takes the constant's name, not the {@code ua.bookloom.api.llm.ServerEngine} type, so it does not
     * depend on a sub-package that depends on it.
     *
     * @param engineName the engine constant's {@code name()}, such as {@code LM_STUDIO}
     * @return a copy carrying the engine
     * @throws IllegalArgumentException if the name is not an upper-case constant name
     */
    public SafeDetails withEngine(String engineName) {
        Objects.requireNonNull(engineName, "engineName");
        if (!CONSTANT_NAME.matcher(engineName).matches()) {
            throw new IllegalArgumentException("engine must be a constant name");
        }
        return new SafeDetails(
                reason,
                httpStatus,
                endpointHost,
                modelName,
                timeoutMillis,
                attempt,
                qaFindings,
                expectedPlaceholders,
                observedPlaceholders,
                folderName,
                promptTokens,
                windowTokens,
                engineName);
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
        final List<String> parts = new ArrayList<>(13);
        append(parts, "reason", reason);
        append(parts, "httpStatus", httpStatus);
        append(parts, "endpointHost", endpointHost);
        append(parts, "model", modelName);
        append(parts, "timeoutMs", timeoutMillis);
        append(parts, "attempt", attempt);
        append(parts, "qaFindings", qaFindings);
        append(parts, "expectedPlaceholders", expectedPlaceholders);
        append(parts, "observedPlaceholders", observedPlaceholders);
        append(parts, "folder", folderName);
        append(parts, "promptTokens", promptTokens);
        append(parts, "windowTokens", windowTokens);
        append(parts, "engine", engine);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void append(List<String> parts, String key, @Nullable Object value) {
        if (value != null) {
            parts.add(key + "=" + value);
        }
    }
}
