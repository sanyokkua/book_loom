package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;

/** Makes one model call and decides one segment. */
@Slf4j
final class SegmentTranslator {

    private static final Pattern PLACEHOLDER = Pattern.compile("⟦g\\d+⟧");
    private final DocumentPort documents;
    private final ChatModel model;
    private final BookFormat format;
    private final String targetLanguage;
    private final @Nullable String sourceLanguage;

    SegmentTranslator(
            final DocumentPort documents,
            final ChatModel model,
            final BookFormat format,
            final String targetLanguage,
            @Nullable final String sourceLanguage) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.model = Objects.requireNonNull(model, "model");
        this.format = Objects.requireNonNull(format, "format");
        this.targetLanguage = Objects.requireNonNull(targetLanguage, "targetLanguage");
        this.sourceLanguage = sourceLanguage;
    }

    Result<Decision> translate(final Segment segment) {
        Objects.requireNonNull(segment, "segment");
        log.debug(
                "Translating segment id={} format={} targetLanguage={} sourceLanguage={}",
                segment.id(),
                format,
                targetLanguage,
                sourceLanguage);
        final ChatRequest request = requestFor(segment);
        final Result<ChatResponse> reply = callModel(segment, request);
        if (reply.isErr()) {
            return decideModelError(segment, Objects.requireNonNull(reply.error()));
        }
        return decideResponse(segment, Objects.requireNonNull(reply.data()));
    }

    private ChatRequest requestFor(final Segment segment) {
        log.debug(
                "Building chat request segmentId={} maskedLength={}",
                segment.id(),
                segment.masked().length());
        final String system = systemPrompt();
        logTracePrompt(system, segment.masked());
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, segment.masked())));
        log.debug(
                "Built chat request segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        return request;
    }

    private String systemPrompt() {
        log.debug(
                "Building system prompt targetLanguage={} sourceLanguagePresent={}",
                targetLanguage,
                sourceLanguage != null);
        if (sourceLanguage == null) {
            log.debug("System prompt sourceBranch=unknown");
            return "Translate the following text into " + targetLanguage + ". Preserve every ⟦gN⟧ placeholder "
                    + "exactly as written. Return only the translated text.";
        }
        log.debug("System prompt sourceBranch=known sourceLanguage={}", sourceLanguage);
        return "Translate the following text from " + sourceLanguage + " into " + targetLanguage
                + ". Preserve every ⟦gN⟧ placeholder exactly as written. Return only the translated text.";
    }

    private Result<ChatResponse> callModel(final Segment segment, final ChatRequest request) {
        log.debug(
                "Calling chat model segmentId={} messageCount={}",
                segment.id(),
                request.messages().size());
        try {
            final Result<ChatResponse> result = Objects.requireNonNull(model.chat(request), "model result");
            log.debug("Chat model completed segmentId={} result={}", segment.id(), result.isOk() ? "success" : "error");
            return result;
        } catch (Throwable cause) {
            final AppError error = AppError.of(
                    ErrorCode.internal,
                    "Translation failed",
                    "The model could not translate this segment.",
                    null,
                    cause);
            log.debug("Chat model completed segmentId={} result=thrown errorCode={}", segment.id(), error.code());
            log.error("Unexpected model failure segment={} code={}", segment.id(), error.code(), cause);
            return Result.err(error);
        }
    }

    private Result<Decision> decideModelError(final Segment segment, final AppError error) {
        log.debug("Model reply segment={} kind=error code={}", segment.id(), error.code());
        return switch (error.code()) {
            case validation, emptyCompletion, contextWindow -> flag(segment, error, "model-error", "[]");
            default -> terminal(segment, error, "model-error");
        };
    }

    private Result<Decision> decideResponse(final Segment segment, final ChatResponse response) {
        final String trimmed = response.content().strip();
        logTraceReply(response.content(), trimmed);
        log.debug(
                "Model reply segment={} kind=text finish={} empty={}",
                segment.id(),
                response.finishReason(),
                trimmed.isEmpty());
        if (trimmed.isEmpty()) {
            return flag(segment, emptyCompletion(), response.finishReason().name(), observedTokens(response.content()));
        }
        if (response.finishReason() != FinishReason.STOP) {
            return flag(segment, invalidFinish(), response.finishReason().name(), observedTokens(response.content()));
        }
        return restore(segment, trimmed);
    }

    private Result<Decision> restore(final Segment segment, final String trimmed) {
        log.debug("Restoring segment id={} format={} trimmedLength={}", segment.id(), format, trimmed.length());
        final String restoredWhitespace = restoreWhitespace(segment.masked(), trimmed);
        logTraceRestoration(restoredWhitespace);
        final Result<String> unmasked = documents.unmask(format, segment, restoredWhitespace);
        if (unmasked.isErr()) {
            final AppError error = Objects.requireNonNull(unmasked.error());
            log.debug("Unmask completed segmentId={} result=error code={}", segment.id(), error.code());
            return error.code() == ErrorCode.validation
                    ? flag(segment, error, FinishReason.STOP.name(), observedTokens(restoredWhitespace))
                    : terminal(segment, error, "unmask");
        }
        final String target = Objects.requireNonNull(unmasked.data());
        log.debug("Unmask completed segmentId={} result=success targetLength={}", segment.id(), target.length());
        logTraceUnmask(restoredWhitespace, target);
        final Decision decision = new Decision(segment.withDecision(SegmentStatus.ACCEPTED, target), null);
        log.debug("Segment decision id={} decision={} errorCode={}", segment.id(), SegmentStatus.ACCEPTED, null);
        return Result.ok(decision);
    }

    private Result<Decision> flag(
            final Segment segment, final AppError error, final String finish, final String observedTokens) {
        final String expectedTokens = expectedTokens(segment);
        log.warn(
                "Flagged segment id={} code={} expectedTokens={} observedTokens={}",
                segment.id(),
                error.code(),
                expectedTokens,
                observedTokens);
        log.debug(
                "Segment decision id={} replyFinish={} decision={} errorCode={}",
                segment.id(),
                finish,
                SegmentStatus.FLAGGED,
                error.code());
        return Result.ok(new Decision(segment.withDecision(SegmentStatus.FLAGGED, null), error));
    }

    private static Result<Decision> terminal(final Segment segment, final AppError error, final String replyKind) {
        log.debug(
                "Segment decision id={} replyKind={} decision=terminal errorCode={}",
                segment.id(),
                replyKind,
                error.code());
        return Result.err(error);
    }

    private static String restoreWhitespace(final String source, final String trimmed) {
        log.debug("Restoring whitespace sourceLength={} trimmedLength={}", source.length(), trimmed.length());
        int leadingEnd = 0;
        while (leadingEnd < source.length() && Character.isWhitespace(source.charAt(leadingEnd))) {
            leadingEnd++;
        }
        int trailingStart = source.length();
        while (trailingStart > leadingEnd && Character.isWhitespace(source.charAt(trailingStart - 1))) {
            trailingStart--;
        }
        final String restored = source.substring(0, leadingEnd) + trimmed + source.substring(trailingStart);
        log.debug(
                "Restored whitespace leadingLength={} trailingLength={} restoredLength={}",
                leadingEnd,
                source.length() - trailingStart,
                restored.length());
        return restored;
    }

    private static String expectedTokens(final Segment segment) {
        log.debug(
                "Collecting expected tokens segmentId={} placeholderCount={}",
                segment.id(),
                segment.placeholders().size());
        final String tokens = segment.placeholders().keySet().stream()
                .map(key -> "⟦" + key + "⟧")
                .toList()
                .toString();
        log.debug(
                "Collected expected tokens segmentId={} tokenCount={}",
                segment.id(),
                segment.placeholders().size());
        return tokens;
    }

    private static String observedTokens(final String text) {
        log.debug("Collecting observed tokens textLength={}", text.length());
        final Matcher matcher = PLACEHOLDER.matcher(text);
        final List<String> tokens = new java.util.ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        log.debug("Collected observed tokens textLength={} tokenCount={}", text.length(), tokens.size());
        return tokens.toString();
    }

    private static AppError emptyCompletion() {
        log.debug("Creating segment error code={} reason=empty-reply", ErrorCode.emptyCompletion);
        return AppError.of(ErrorCode.emptyCompletion, "Empty model response", "The model returned no translated text.");
    }

    private static AppError invalidFinish() {
        log.debug("Creating segment error code={} reason=non-stop-finish", ErrorCode.validation);
        return AppError.of(
                ErrorCode.validation, "Incomplete model response", "The model response did not finish normally.");
    }

    private static void logTracePrompt(final String system, final String user) {
        if (log.isTraceEnabled()) {
            log.trace("Segment prompt system={} user={}", system, user);
        }
    }

    private static void logTraceReply(final String raw, final String trimmed) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply raw={} trimmed={}", raw, trimmed);
        }
    }

    private static void logTraceRestoration(final String restored) {
        if (log.isTraceEnabled()) {
            log.trace("Segment reply restored={}", restored);
        }
    }

    private static void logTraceUnmask(final String input, final String output) {
        if (log.isTraceEnabled()) {
            log.trace("Segment unmask input={} output={}", input, output);
        }
    }
}
