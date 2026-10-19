package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;

/**
 * One model call read from a trace log: the line that announced it, the request body sent over HTTP and, when the log
 * holds it, the response body. It holds book text; it lives in memory for one replay and is never written out.
 *
 * @param order the call's position among the calls of its log, from zero
 * @param kind what the call was for
 * @param segmentIds the segments the call was about, in order
 * @param requestBody the JSON body as it went out
 * @param responseBody the JSON body that came back, or null when the log lacks it
 */
record LoggedCall(
        int order,
        CallKind kind,
        List<String> segmentIds,
        String requestBody,
        @Nullable String responseBody) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern ITEM = Pattern.compile("<s id=\"[^\"]*\">(.*?)</s>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<Text>\\n(.*?)\\n</Text>", Pattern.DOTALL);
    private static final Pattern LANGUAGES =
            Pattern.compile("from [^(\\n]*\\(([A-Za-z-]+)\\) (?:to|into) [^(\\n]*\\(([A-Za-z-]+)\\)");
    private static final String DEFAULT_SOURCE = "en";
    private static final String DEFAULT_TARGET = "uk";
    private static final String NO_REASONING = "none";

    /** Copies the ids and rejects missing parts. */
    LoggedCall {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requestBody, "requestBody");
        segmentIds = List.copyOf(segmentIds);
    }

    /** The request as the llm module takes it, so the call can be sent again as it went out. */
    ChatRequest chatRequest() {
        final JsonNode body = json(requestBody);
        final JsonNode options = body.path("options");
        final List<ChatMessage> messages = new ArrayList<>();
        for (final JsonNode message : body.path("messages")) {
            messages.add(new ChatMessage(
                    ChatRole.valueOf(message.path("role").asText().toUpperCase(Locale.ROOT)),
                    message.path("content").asText()));
        }
        return new ChatRequest(
                messages,
                number(body.path("temperature"), options.path("temperature")),
                format(body),
                reasoning(body),
                null,
                null,
                integer(body.path("max_tokens"), options.path("num_predict")),
                integer(body.path("seed"), options.path("seed")),
                kind);
    }

    /** The text of the model's reply, or empty when the log holds no response or no content in it. */
    Optional<String> reply() {
        if (responseBody == null) {
            return Optional.empty();
        }
        final JsonNode body = json(responseBody);
        final JsonNode content = body.path("choices").path(0).path("message").path("content");
        final JsonNode text =
                content.isTextual() ? content : body.path("message").path("content");
        return text.isTextual() ? Optional.of(text.asText()) : Optional.empty();
    }

    /** The source texts the call asks to translate: one for a single draft, one per item of a batch; empty otherwise. */
    List<String> sourceTexts() {
        final String user = userMessage();
        final List<String> texts = new ArrayList<>();
        final int items = user.indexOf("<Items>");
        if (items >= 0) {
            final Matcher matcher = ITEM.matcher(user.substring(items));
            while (matcher.find()) {
                texts.add(matcher.group(1));
            }
            return texts;
        }
        final Matcher text = TEXT.matcher(user);
        return text.find() ? List.of(text.group(1)) : List.of();
    }

    /** Whether the call drafts several segments in one request. */
    boolean isBatch() {
        return userMessage().contains("<Items>");
    }

    /** The source language tag the prompt names; English when it names none. */
    String sourceLanguage() {
        final Matcher matcher = LANGUAGES.matcher(allMessages());
        return matcher.find() ? matcher.group(1) : DEFAULT_SOURCE;
    }

    /** The target language tag the prompt names; Ukrainian when it names none. */
    String targetLanguage() {
        final Matcher matcher = LANGUAGES.matcher(allMessages());
        return matcher.find() ? matcher.group(2) : DEFAULT_TARGET;
    }

    /** The number of characters of source the call carries, to tell a short call from a long one. */
    int sourceLength() {
        return sourceTexts().stream().mapToInt(String::length).sum();
    }

    private String userMessage() {
        String user = "";
        for (final JsonNode message : json(requestBody).path("messages")) {
            if ("user".equals(message.path("role").asText())) {
                user = message.path("content").asText();
            }
        }
        return user;
    }

    private String allMessages() {
        final StringBuilder all = new StringBuilder();
        for (final JsonNode message : json(requestBody).path("messages")) {
            all.append(message.path("content").asText()).append('\n');
        }
        return all.toString();
    }

    private static @Nullable ResponseFormat format(final JsonNode body) {
        final JsonNode schema = body.path("response_format").path("json_schema");
        if (schema.isObject()) {
            return new ResponseFormat(
                    schema.path("name").asText("reply"), schema.path("schema").toString());
        }
        return body.path("format").isObject()
                ? new ResponseFormat("reply", body.path("format").toString())
                : null;
    }

    private static @Nullable Boolean reasoning(final JsonNode body) {
        final boolean off = NO_REASONING.equals(body.path("reasoning_effort").asText())
                || (body.path("think").isBoolean() && !body.path("think").asBoolean());
        return off ? Boolean.FALSE : null;
    }

    private static @Nullable Double number(final JsonNode first, final JsonNode second) {
        final JsonNode node = first.isNumber() ? first : second;
        return node.isNumber() ? node.asDouble() : null;
    }

    private static @Nullable Integer integer(final JsonNode first, final JsonNode second) {
        final JsonNode node = first.isNumber() ? first : second;
        return node.isNumber() ? node.asInt() : null;
    }

    private static JsonNode json(final String text) {
        try {
            return MAPPER.readTree(text);
        } catch (IOException e) {
            return MAPPER.createObjectNode();
        }
    }
}
