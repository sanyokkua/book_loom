package ua.bookloom.llm.capability;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Reads OpenRouter's model catalog and a model's endpoint list into facts. A catalog {@code reasoning} object marks a
 * model that can reason (its efforts and whether reasoning is mandatory decide the off value sent); a model without one
 * is declared to have no reasoning control. One model may be served by several endpoints: the window and the answer
 * limit are the smallest any of them states, so a call fits whichever answers, and the parameters are every one an
 * endpoint honours, since {@code require_parameters} routes a call only to an endpoint that honours all it carries.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class OpenRouterCatalog {

    private static final String COMPLETION = "completion";
    private static final String THINKING = "thinking";
    private static final String CONTEXT_LENGTH = "context_length";
    private static final String MAX_COMPLETION_TOKENS = "max_completion_tokens";
    private static final String SUPPORTED_PARAMETERS = "supported_parameters";

    static Optional<JsonNode> row(final JsonNode list, final String modelId) {
        for (final JsonNode row : list.path("data")) {
            if (modelId.equals(row.path("id").asText())) {
                return Optional.of(row);
            }
        }
        log.debug("OpenRouter catalog has no row model={}", modelId);
        return Optional.empty();
    }

    static void readModel(final JsonNode row, final FactsDraft draft) {
        final JsonNode reasoning = row.path("reasoning");
        final boolean reasons = reasoning.isObject();
        draft.capabilities = reasons ? List.of(COMPLETION, THINKING) : List.of(COMPLETION);
        draft.thinkingOptions = ModelFactsReader.strings(reasoning.path("supported_efforts"));
        draft.thinkingDefault = ModelFactsReader.text(reasoning.path("default_effort"));
        draft.reasoningMandatory = reasoning.path("mandatory").asBoolean(false);
        draft.supportedParameters = ModelFactsReader.strings(row.path(SUPPORTED_PARAMETERS));
        final JsonNode top = row.path("top_provider");
        final Integer topWindow = ModelFactsReader.positiveOrNull(top.path(CONTEXT_LENGTH));
        draft.contextLength = topWindow != null ? topWindow : ModelFactsReader.positiveOrNull(row.path(CONTEXT_LENGTH));
        draft.maxCompletionTokens = ModelFactsReader.positiveOrNull(top.path(MAX_COMPLETION_TOKENS));
        log.debug(
                "OpenRouter catalog row read reasons={} efforts={} mandatory={} parameters={} window={} maxCompletion={}",
                reasons,
                draft.thinkingOptions,
                draft.reasoningMandatory,
                draft.supportedParameters.size(),
                draft.contextLength,
                draft.maxCompletionTokens);
    }

    static void readEndpoints(final JsonNode body, final FactsDraft draft) {
        final JsonNode endpoints = body.path("data").path("endpoints");
        final Integer window = smallest(endpoints, CONTEXT_LENGTH);
        final Integer answer = smallest(endpoints, MAX_COMPLETION_TOKENS);
        final Set<String> parameters = new LinkedHashSet<>();
        endpoints.forEach(endpoint -> parameters.addAll(ModelFactsReader.strings(endpoint.path(SUPPORTED_PARAMETERS))));
        log.debug(
                "OpenRouter endpoints read count={} window={} maxCompletion={} parameters={}",
                endpoints.size(),
                window,
                answer,
                parameters);
        if (window != null) {
            // A hosted endpoint's window is fixed, like a model loaded with its window.
            draft.contextLength = window;
            draft.loadedContext = window;
        }
        if (answer != null) {
            draft.maxCompletionTokens = answer;
        }
        if (!parameters.isEmpty()) {
            draft.supportedParameters = new ArrayList<>(parameters);
        }
    }

    private static @Nullable Integer smallest(final JsonNode endpoints, final String field) {
        Integer least = null;
        for (final JsonNode endpoint : endpoints) {
            final Integer value = ModelFactsReader.positiveOrNull(endpoint.path(field));
            if (value != null && (least == null || value < least)) {
                least = value;
            }
        }
        return least;
    }
}
