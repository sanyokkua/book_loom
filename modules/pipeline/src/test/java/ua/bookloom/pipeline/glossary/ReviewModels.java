package ua.bookloom.pipeline.glossary;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.ScriptedChatModel;

/**
 * A scripted model for a review test whose script is about the verdicts and the suggestions: the name-knowledge stage
 * (design D5) asks its bare names and is answered with no item, the first time and the once-more, so the rest of the
 * script answers the calls the test is about.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReviewModels {

    private static final Result<ChatResponse> NO_KNOWLEDGE =
            Result.ok(new ChatResponse("{\"items\":[]}", FinishReason.STOP));

    static ScriptedChatModel scripted() {
        return new ScriptedChatModel()
                .answerTo("name-knowledge", NO_KNOWLEDGE)
                .answerTo("name-knowledge", NO_KNOWLEDGE);
    }
}
