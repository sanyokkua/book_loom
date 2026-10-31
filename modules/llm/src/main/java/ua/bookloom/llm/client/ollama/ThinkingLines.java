package ua.bookloom.llm.client.ollama;

import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;

/** Bounds a streamed reply: how many lines it may run, and which lines count against that bound. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ThinkingLines {

    /** How many times its own cap a streamed reply may run before it is cut as a runaway. */
    private static final int RUNAWAY_FACTOR = 2;

    /** Lines a streamed reply may add past that, for the final part and the envelope around a short cap. */
    private static final int RUNAWAY_MARGIN_LINES = 64;

    private static final String THINKING_FIELD = "\"thinking\":\"";
    private static final String EMPTY_THINKING_FIELD = "\"thinking\":\"\"";
    private static final String EMPTY_CONTENT_FIELD = "\"content\":\"\"";

    /**
     * A thinking call streams its reasoning as lines with an empty {@code content}; they are the room the server was
     * given, not the answer running away, so only the other lines count against the bound.
     */
    static Predicate<String> counted(final ChatRequest request) {
        return request.isThinking() ? line -> !isThinkingLine(line) : line -> true;
    }

    /**
     * The most streamed lines read before a reply is cut as a runaway: Ollama sends about one token per line, so a
     * reply far past twice its own cap (or, with no cap, twice the context) is a model looping that the server did not
     * stop — a reflect call once streamed 9,664 lines until the three-minute timeout. Cut there, it is read as a
     * reply that finished for length.
     */
    static long maxLines(String modelId, ChatRequest request) {
        final @Nullable Integer cap = request.maxOutputTokens();
        final @Nullable Integer bound = cap != null ? cap : request.contextWindow();
        final long lines = bound == null ? Long.MAX_VALUE : (long) bound * RUNAWAY_FACTOR + RUNAWAY_MARGIN_LINES;
        log.debug(
                "Runaway guard maxOutputTokens={} contextWindow={} reasoning={} maxLines={}",
                request.maxOutputTokens(),
                request.contextWindow(),
                request.reasoningEnabled(),
                lines);
        return lines;
    }

    private static boolean isThinkingLine(final String line) {
        return line.contains(THINKING_FIELD)
                && !line.contains(EMPTY_THINKING_FIELD)
                && line.contains(EMPTY_CONTENT_FIELD);
    }
}
