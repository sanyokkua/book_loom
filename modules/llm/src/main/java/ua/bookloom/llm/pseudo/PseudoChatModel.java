package ua.bookloom.llm.pseudo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;

/**
 * Deterministic offline chat model that upper-cases the last user message.
 */
@Slf4j
public final class PseudoChatModel implements ChatModel {

    private static final Pattern PROTECTED_REFERENCE =
            Pattern.compile("⟦g\\d+⟧|&(?:#(?:x|X)[0-9A-Fa-f]+|#\\d+|[A-Za-z][A-Za-z0-9]+);");

    /**
     * Creates the built-in offline model.
     */
    public PseudoChatModel() {
        // The pseudo model has no external collaborators.
    }

    @Override
    public Result<ChatResponse> chat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            final String userMessage = lastUserMessage(request.messages());
            log.debug(
                    "Pseudo chat messageCount={} lastUserMessageLength={}",
                    request.messages().size(),
                    userMessage.length());
            final String reply = uppercasePreservingReferences(userMessage);
            logTrace(userMessage, reply);
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static String lastUserMessage(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            final ChatMessage message = messages.get(index);
            if (message.role() == ChatRole.USER) {
                return message.content();
            }
        }
        return "";
    }

    private static String uppercasePreservingReferences(String text) {
        final Matcher matcher = PROTECTED_REFERENCE.matcher(text);
        final StringBuilder uppercase = new StringBuilder(text.length());
        int copiedThrough = 0;
        while (matcher.find()) {
            appendUppercase(uppercase, text, copiedThrough, matcher.start());
            uppercase.append(matcher.group());
            copiedThrough = matcher.end();
        }
        appendUppercase(uppercase, text, copiedThrough, text.length());
        return uppercase.toString();
    }

    private static void appendUppercase(StringBuilder target, String source, int start, int end) {
        target.append(source.substring(start, end).toUpperCase(Locale.ROOT));
    }

    private static void logTrace(String userMessage, String reply) {
        if (log.isTraceEnabled()) {
            log.trace("Pseudo chat lastUserMessage={} reply={}", userMessage, reply);
        }
    }

    private static AppError internalError(Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal,
                "Pseudo model failure",
                "The offline pseudo model could not complete the chat request.",
                null,
                cause);
        log.error("Unexpected pseudo chat failure code={}", error.code(), cause);
        return error;
    }
}
