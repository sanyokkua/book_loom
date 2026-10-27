package ua.bookloom.llm.client.ollama;

import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ReasoningLevel;

/** Tells which streamed lines count against the runaway bound. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ThinkingLines {

    private static final String THINKING_FIELD = "\"thinking\":\"";
    private static final String EMPTY_THINKING_FIELD = "\"thinking\":\"\"";
    private static final String EMPTY_CONTENT_FIELD = "\"content\":\"\"";

    /**
     * A thinking call streams its reasoning as lines with an empty {@code content}; they are the room the server was
     * given, not the answer running away, so only the other lines count against the bound.
     */
    static Predicate<String> counted(final ChatRequest request) {
        final ReasoningLevel level = request.reasoningEnabled();
        return level != null && level.isThinking() ? line -> !isThinkingLine(line) : line -> true;
    }

    private static boolean isThinkingLine(final String line) {
        return line.contains(THINKING_FIELD)
                && !line.contains(EMPTY_THINKING_FIELD)
                && line.contains(EMPTY_CONTENT_FIELD);
    }
}
