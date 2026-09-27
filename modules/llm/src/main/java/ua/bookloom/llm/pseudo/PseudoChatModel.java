package ua.bookloom.llm.pseudo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Deterministic offline chat model that upper-cases either a delimited draft source or the last user message.
 */
@Slf4j
public final class PseudoChatModel implements ChatModel {

    private static final Pattern PROTECTED_REFERENCE =
            Pattern.compile("⟦g\\d+⟧|&(?:#(?:x|X)[0-9A-Fa-f]+|#\\d+|[A-Za-z][A-Za-z0-9]+);");
    private static final Pattern CAPITALIZED_WORD = Pattern.compile("\\p{Lu}\\p{L}*");

    private static final String FORMAT_JUDGE = "judge";
    private static final String FORMAT_REFLECT = "reflect";
    private static final String FORMAT_PRESCAN = "prescan";
    private static final String FORMAT_SUMMARY = "summary";

    private static final String JUDGE_REPLY = "{\"score\":1.0,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}";
    private static final String REFLECT_REPLY = "{\"issues\":[]}";
    private static final String SUMMARY_REPLY = "{\"summary\":{\"source\":\"\",\"target\":\"\"},\"facts\":[]}";

    private final ObjectMapper mapper;

    /**
     * Creates the built-in offline model.
     */
    public PseudoChatModel(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public Result<ChatResponse> chat(ChatRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            final String userMessage = lastUserMessage(request.messages());
            final Optional<String> draftSource = delimitedDraftSource(userMessage);
            final String source = draftSource.orElse(userMessage);
            log.debug(
                    "Pseudo chat messageCount={} lastUserMessageLength={} draftSourcePresent={} responseFormat={}",
                    request.messages().size(),
                    userMessage.length(),
                    draftSource.isPresent(),
                    request.responseFormat() == null
                            ? null
                            : request.responseFormat().name());
            final String translation = uppercasePreservingReferences(source);
            final String reply = structuredReply(request, source, translation);
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

    private Optional<String> delimitedDraftSource(String userMessage) {
        final String opening = "<Text>\n";
        final int start = userMessage.indexOf(opening);
        final int end = userMessage.indexOf("\n</Text>", start + opening.length());
        return start < 0 || end < 0
                ? Optional.empty()
                : Optional.of(userMessage.substring(start + opening.length(), end));
    }

    private String structuredReply(ChatRequest request, String source, String translation) {
        if (request.responseFormat() == null) {
            return translation;
        }
        final String formatName = request.responseFormat().name();
        return switch (formatName) {
            case FORMAT_JUDGE -> JUDGE_REPLY;
            case FORMAT_REFLECT -> REFLECT_REPLY;
            case FORMAT_PRESCAN -> prescanReply(source);
            case FORMAT_SUMMARY -> SUMMARY_REPLY;
            default -> targetReply(translation);
        };
    }

    private String targetReply(String translation) {
        return writeValue(Map.of("target", translation));
    }

    private String prescanReply(String source) {
        final List<Map<String, String>> terms = capitalizedWords(source).stream()
                .map(PseudoChatModel::termEntry)
                .toList();
        return writeValue(Map.of("terms", terms));
    }

    private static Map<String, String> termEntry(String term) {
        final Map<String, String> entry = new LinkedHashMap<>();
        entry.put("term", term);
        entry.put("type", "other");
        entry.put("gender", "unknown");
        return entry;
    }

    private static Set<String> capitalizedWords(String source) {
        final Matcher matcher = CAPITALIZED_WORD.matcher(source);
        final Set<String> words = new LinkedHashSet<>();
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private String writeValue(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception cause) {
            throw new IllegalStateException("Could not encode pseudo reply", cause);
        }
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
