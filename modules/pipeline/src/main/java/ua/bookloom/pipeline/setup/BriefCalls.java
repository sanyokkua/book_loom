package ua.bookloom.pipeline.setup;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.pipeline.prompt.PromptName;

/** The parts of a brief window's call and result that need no collaborators. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefCalls {

    /**
     * The request with the schema that also asks the passage's language, which only a book whose source language is
     * Auto needs.
     *
     * @param request the window's request built from the bare schema; never null
     * @return the same request with {@link BriefSuggestionSchema#WITH_LANGUAGE} as its response format
     */
    static ChatRequest withLanguageAsked(final ChatRequest request) {
        return new ChatRequest(
                request.messages(),
                request.temperature(),
                new ResponseFormat(
                        PromptName.BRIEF_SUGGESTION.responseFormatName(), BriefSuggestionSchema.WITH_LANGUAGE),
                request.reasoningEnabled(),
                request.contextWindow(),
                request.expectedOutputTokens(),
                request.maxOutputTokens(),
                request.seed(),
                request.callKind(),
                request.sampling());
    }

    /**
     * The fields a suggestion keeps, for the log.
     *
     * @param suggestion the combined suggestion; never null
     * @return the kept fields in order; never null, empty if none
     */
    static List<BriefField> kept(final BriefSuggestion suggestion) {
        return suggestion.evidence().entrySet().stream()
                .filter(entry -> entry.getValue().isKept())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    // 15i.B4 counts the windows' languages into a proposal; until then each is only recorded.
    static void logLanguage(final int window, final @Nullable JsonNode reply, final @Nullable String chosen) {
        if (reply != null) {
            BriefReplies.language(reply, chosen)
                    .ifPresent(language -> log.debug("Brief window {} is written in {}", window + 1, language));
        }
    }
}
