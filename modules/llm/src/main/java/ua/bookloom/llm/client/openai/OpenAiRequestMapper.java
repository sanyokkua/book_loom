package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.dto.OpenAiChatRequest;
import ua.bookloom.llm.dto.OpenAiChatRequest.ResponseFormatDto;

/** Shapes a provider-neutral {@link ChatRequest} into the JSON body an OpenAI-compatible server expects. */
@Slf4j
final class OpenAiRequestMapper {
    private static final String FORMAT_TYPE = "json_schema";

    /** The {@code reasoning_effort} that turns reasoning off; a request that leaves reasoning alone omits the field. */
    static final String REASONING_OFF = "none";

    private final ProviderConfig config;
    private final ObjectMapper mapper;
    private final BiFunction<String, Throwable, AppError> serializationFailure;

    /**
     * Binds the mapper to one endpoint.
     *
     * @param serializationFailure builds the error for a body that could not be written, from the model id and the
     *     failure; the client owns it so every unexpected failure is logged and worded in one place
     */
    OpenAiRequestMapper(
            ProviderConfig config, ObjectMapper mapper, BiFunction<String, Throwable, AppError> serializationFailure) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.serializationFailure = Objects.requireNonNull(serializationFailure, "serializationFailure");
    }

    Result<ParsedFormat> parseFormat(@Nullable ResponseFormat format, String modelId) {
        if (format == null) {
            return Result.ok(new ParsedFormat(null));
        }
        try {
            final JsonNode schema = mapper.readTree(format.jsonSchema());
            if (schema == null || !schema.isObject()) {
                return invalidFormat(modelId, null);
            }
            return Result.ok(new ParsedFormat(new OpenAiChatRequest.ResponseFormatDto(
                    FORMAT_TYPE, new OpenAiChatRequest.JsonSchemaDto(format.name(), true, schema))));
        } catch (JsonProcessingException failure) {
            return invalidFormat(modelId, failure);
        }
    }

    private Result<ParsedFormat> invalidFormat(String modelId, @Nullable Throwable cause) {
        log.warn(
                "OpenAI-compatible request rejected locally provider={} model={} code={}",
                config.id(),
                modelId,
                ErrorCode.validation);
        return Result.err(AppError.of(
                ErrorCode.validation,
                "Invalid response schema",
                "The response format must contain a JSON schema object.",
                details(modelId),
                cause));
    }

    Result<String> serializeRequest(String modelId, ChatRequest request, @Nullable ResponseFormatDto responseFormat) {
        final List<OpenAiChatRequest.Message> messages =
                request.messages().stream().map(this::toOpenAiMessage).toList();
        try {
            final OpenAiChatRequest payload = new OpenAiChatRequest(
                    modelId,
                    messages,
                    false,
                    request.temperature(),
                    responseFormat,
                    request.maxOutputTokens(),
                    request.seed(),
                    Boolean.FALSE.equals(request.reasoningEnabled()) ? REASONING_OFF : null);
            return Result.ok(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException failure) {
            return Result.err(serializationFailure.apply(modelId, failure));
        }
    }

    private OpenAiChatRequest.Message toOpenAiMessage(ChatMessage message) {
        return new OpenAiChatRequest.Message(message.role().name().toLowerCase(Locale.ROOT), message.content());
    }

    private @Nullable String details(String modelId) {
        return SafeDetails.empty()
                .withEndpoint(config.baseUrl())
                .withModelName(modelId)
                .render();
    }

    record ParsedFormat(@Nullable ResponseFormatDto format) {}
}
