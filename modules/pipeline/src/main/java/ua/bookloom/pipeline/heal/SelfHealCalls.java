package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * What {@link DirectedFix}, {@link ReflectImprove#improve}, {@link Polish} and backward revision's re-render
 * otherwise each repeated: rendering a
 * call's system/user messages from the catalogue, computing its output limit, and reading a classified
 * {@link RepairReply} back into one consistent set of log lines. Each caller passes its own {@code @Slf4j} logger
 * so a log line still names the class that made the call, not this shared helper.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SelfHealCalls {

    /**
     * Renders {@code name}'s system and user messages from the catalogue.
     *
     * @param templates the catalogue's template renderer
     * @param name which call's templates to render
     * @param frame the run's language pair, style sheet and foreign-passage policy, for the system slots
     * @param userValues the user template's slot values
     * @return the two-message list every self-heal call sends
     */
    public static List<ChatMessage> messagesFor(
            final PromptTemplates templates,
            final PromptName name,
            final CallFrame frame,
            final Map<String, String> userValues) {
        final String system =
                templates.renderSystem(name, frame.systemSlotValues()).strip();
        final String user = templates.renderUser(name, userValues).strip();
        return List.of(new ChatMessage(ChatRole.SYSTEM, system), new ChatMessage(ChatRole.USER, user));
    }

    /**
     * The expected output and its cap for a call whose source is {@code maskedSource}.
     *
     * @param maskedSource the segment's masked source
     * @param frame the run's language pair
     * @return the limit, or {@code null} when the source has no display text (no expected-output constraint sent)
     */
    public static @Nullable OutputLimit outputLimit(final String maskedSource, final CallFrame frame) {
        return OutputLimit.forSource(maskedSource, frame.sourceLanguage(), frame.targetLanguage());
    }

    /** Logs {@code request}'s messages at TRACE, prefixed with {@code label}. */
    public static void logTraceMessages(final Logger log, final String label, final ChatRequest request) {
        if (log.isTraceEnabled()) {
            log.trace("{} messages {}", label, request.messages());
        }
    }

    /** Logs a successful reply's raw content at TRACE, prefixed with {@code label}. */
    public static void logTraceReply(final Logger log, final String label, final Result<ChatResponse> reply) {
        if (log.isTraceEnabled() && reply.isOk()) {
            log.trace(
                    "{} raw reply {}",
                    label,
                    Objects.requireNonNull(reply.data()).content());
        }
    }

    /** Logs a classified {@link RepairReply} outcome at DEBUG, prefixed with {@code label}. */
    public static void logOutcome(
            final Logger log, final String label, final String segmentId, final Result<RepairReply> outcome) {
        if (outcome.isErr()) {
            log.debug(
                    "{} reply segmentId={} outcome=error code={}",
                    label,
                    segmentId,
                    Objects.requireNonNull(outcome.error()).code());
            return;
        }
        switch (Objects.requireNonNull(outcome.data())) {
            case RepairReply.Rewritten rewritten ->
                log.debug(
                        "{} reply segmentId={} outcome=Rewritten targetLength={}",
                        label,
                        segmentId,
                        rewritten.maskedTarget().length());
            case RepairReply.Malformed malformed ->
                log.debug(
                        "{} reply segmentId={} outcome=Malformed diagnostic={}",
                        label,
                        segmentId,
                        malformed.diagnostic());
            case RepairReply.FlagNow flagNow ->
                log.debug(
                        "{} reply segmentId={} outcome=FlagNow code={}",
                        label,
                        segmentId,
                        flagNow.error().code());
        }
    }
}
