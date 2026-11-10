package ua.bookloom.api.llm;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The request and reply of one verification stage, for the Settings test dialog.
 *
 * <p>The bodies hold prompt and reply text and are shown to the person and logged at TRACE only. They never hold a
 * header or a credential: the key is masked before the exchange is built, and no header is part of it.
 *
 * @param method the HTTP method
 * @param url the full request URL with no credentials in it
 * @param requestBody the request body, or an empty string when there was none
 * @param responseBody the response body, or an empty string when there was none
 * @param httpStatus the response status, or zero when no response arrived
 * @param firstTokenMs milliseconds to the first streamed token, or null when not measured
 * @param tokensPerSecond the measured generation speed, or null when not measured
 * @param structured whether the reply was a well-formed structured answer
 * @param requestHeaders the request headers as masked "Name: value" lines, or an empty string
 * @param promptTokens the tokens of the prompt the server counted, or null when it did not say
 * @param completionTokens the tokens of the reply the server counted, or null when it did not say
 * @param shownRequestBody the request body laid out for reading (indented when it is JSON); Copy as curl takes
 *     {@code requestBody}, the body as sent
 * @param shownResponseBody the reply laid out for reading (indented when it is JSON)
 * @param loadMs milliseconds the server spent loading the model before it answered, or null when it did not say;
 *     only Ollama reports it
 * @param cachedPromptTokens how many prompt tokens the server says it served from its cache, or null when it did not
 *     say
 * @param calls the calls of a stage that made several (the Advanced features checks), in the order sent; empty for a
 *     stage of one request
 */
public record VerificationExchange(
        String method,
        String url,
        String requestBody,
        String responseBody,
        int httpStatus,
        @Nullable Long firstTokenMs,
        @Nullable Double tokensPerSecond,
        boolean structured,
        String requestHeaders,
        @Nullable Integer promptTokens,
        @Nullable Integer completionTokens,
        String shownRequestBody,
        String shownResponseBody,
        @Nullable Long loadMs,
        @Nullable Integer cachedPromptTokens,
        List<ProbeCall> calls) {

    /** The form with no cached prompt count. */
    public VerificationExchange(
            String method,
            String url,
            String requestBody,
            String responseBody,
            int httpStatus,
            @Nullable Long firstTokenMs,
            @Nullable Double tokensPerSecond,
            boolean structured,
            String requestHeaders,
            @Nullable Integer promptTokens,
            @Nullable Integer completionTokens,
            String shownRequestBody,
            String shownResponseBody,
            @Nullable Long loadMs,
            List<ProbeCall> calls) {
        this(
                method,
                url,
                requestBody,
                responseBody,
                httpStatus,
                firstTokenMs,
                tokensPerSecond,
                structured,
                requestHeaders,
                promptTokens,
                completionTokens,
                shownRequestBody,
                shownResponseBody,
                loadMs,
                null,
                calls);
    }

    /** The form whose bodies are shown as they are, with no load time and no separate calls. */
    public VerificationExchange(
            String method,
            String url,
            String requestBody,
            String responseBody,
            int httpStatus,
            @Nullable Long firstTokenMs,
            @Nullable Double tokensPerSecond,
            boolean structured,
            String requestHeaders,
            @Nullable Integer promptTokens,
            @Nullable Integer completionTokens) {
        this(
                method,
                url,
                requestBody,
                responseBody,
                httpStatus,
                firstTokenMs,
                tokensPerSecond,
                structured,
                requestHeaders,
                promptTokens,
                completionTokens,
                requestBody,
                responseBody,
                null,
                null,
                List.of());
    }

    /** The form without token counts. */
    public VerificationExchange(
            String method,
            String url,
            String requestBody,
            String responseBody,
            int httpStatus,
            @Nullable Long firstTokenMs,
            @Nullable Double tokensPerSecond,
            boolean structured,
            String requestHeaders) {
        this(
                method,
                url,
                requestBody,
                responseBody,
                httpStatus,
                firstTokenMs,
                tokensPerSecond,
                structured,
                requestHeaders,
                null,
                null);
    }

    /** The form without request headers. */
    public VerificationExchange(
            String method,
            String url,
            String requestBody,
            String responseBody,
            int httpStatus,
            @Nullable Long firstTokenMs,
            @Nullable Double tokensPerSecond,
            boolean structured) {
        this(
                method,
                url,
                requestBody,
                responseBody,
                httpStatus,
                firstTokenMs,
                tokensPerSecond,
                structured,
                "",
                null,
                null);
    }

    /** Rejects a missing or blank method and URL, and absent bodies; copies the calls. */
    public VerificationExchange {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(requestBody, "requestBody");
        Objects.requireNonNull(responseBody, "responseBody");
        Objects.requireNonNull(requestHeaders, "requestHeaders");
        Objects.requireNonNull(shownRequestBody, "shownRequestBody");
        Objects.requireNonNull(shownResponseBody, "shownResponseBody");
        calls = List.copyOf(calls);
        if (method.isBlank() || url.isBlank()) {
            throw new IllegalArgumentException("method and url must not be blank");
        }
    }
}
