package ua.bookloom.llm.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelProfile.Quirk;

/**
 * Lays a request's messages out the way the model's chat template reads them (design D20): BookLoom's own system
 * message is always there, so no persona baked into a download is used; a template with no system role gets the system
 * text as the start of the first user message; a template that writes the reasoning level itself, or reads a
 * {@code Reasoning:} line as an order, never sees one of BookLoom's; and an old template that switches thinking on the
 * words {@code /think} and {@code /no_think} sees them with a word joiner after the slash.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageShaper {

    /** The system text a call that brought none is sent, so the template's own default is never used. */
    public static final String DEFAULT_SYSTEM = "Follow the instructions in the user message exactly.";

    /** The invisible word joiner put after the slash of a thinking switch in book text. */
    public static final char WORD_JOINER = '⁠';

    private static final String BLANK_LINE = "\n\n";
    private static final Pattern THINK_SWITCH = Pattern.compile("/(?=(?:no_)?think\\b)");
    private static final String JOINED_SLASH = "/" + WORD_JOINER;

    // A line naming a reasoning level, strength or effort, which muse-glimmer's template rewrites or replaces.
    private static final Pattern REASONING_WORDS =
            Pattern.compile("(?im)^[^\\n]*\\breasoning\\s+(?:level|strength|effort)\\b[^\\n]*(?:\\n|\\z)");

    // A line gpt-oss's harmony template reads as the reasoning order.
    private static final Pattern REASONING_LINE = Pattern.compile("(?im)^\\s*Reasoning:[^\\n]*(?:\\n|\\z)");

    /**
     * The request laid out for the model's profile.
     *
     * @param profile the model's resolved profile; non-null
     * @param request the call; non-null
     * @return the request with its messages shaped; the same request when nothing changes
     */
    public static ChatRequest shaped(final ModelProfile profile, final ChatRequest request) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(request, "request");
        List<ChatMessage> messages = withSystem(request.messages());
        messages = withoutReasoningLines(profile, messages);
        if (profile.quirks().contains(Quirk.ESCAPE_THINK_TOGGLES)) {
            messages = escaped(messages);
        }
        final ChatRequest shaped = withMessages(request, messages);
        final boolean fold = profile.systemRole() == ModelProfile.SystemRole.FOLD;
        log.debug(
                "Messages shaped kind={} count={} systemRole={} quirks={}",
                request.callKind(),
                messages.size(),
                profile.systemRole(),
                profile.quirks());
        return fold ? folded(shaped) : shaped;
    }

    /**
     * The request with its system text put at the start of the first user message, followed by a blank line, and no two
     * messages of one role in a row.
     *
     * @param request the call; non-null
     * @return the folded request; the same request when it has no system message
     */
    public static ChatRequest folded(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final List<ChatMessage> messages = request.messages();
        final String system = messages.stream()
                .filter(message -> message.role() == ChatRole.SYSTEM)
                .map(ChatMessage::content)
                .collect(Collectors.joining(BLANK_LINE));
        if (system.isEmpty()) {
            return request;
        }
        final List<ChatMessage> rest = messages.stream()
                .filter(message -> message.role() != ChatRole.SYSTEM)
                .toList();
        final List<ChatMessage> folded = new ArrayList<>();
        folded.add(new ChatMessage(ChatRole.USER, system));
        folded.addAll(rest);
        log.debug(
                "System text folded into the first user message kind={} systemChars={}",
                request.callKind(),
                system.length());
        return withMessages(request, joinedRuns(folded));
    }

    /**
     * The request with every run of messages of one role merged into one message, for a template that wants the roles
     * to alternate.
     *
     * @param request the call; non-null
     * @return the merged request; the same request when no two neighbouring messages share a role
     */
    public static ChatRequest merged(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final List<ChatMessage> joined = joinedRuns(request.messages());
        log.debug(
                "Message runs merged kind={} before={} after={}",
                request.callKind(),
                request.messages().size(),
                joined.size());
        return joined.size() == request.messages().size() ? request : withMessages(request, joined);
    }

    /**
     * Whether two neighbouring messages share a role.
     *
     * @param request the call; non-null
     * @return {@code true} if a merge would change the request, {@code false} otherwise
     */
    public static boolean hasRuns(final ChatRequest request) {
        return joinedRuns(Objects.requireNonNull(request, "request").messages()).size()
                != request.messages().size();
    }

    /**
     * Whether the request has a system message of its own.
     *
     * @param request the call; non-null
     * @return {@code true} if a message has the system role, {@code false} otherwise
     */
    public static boolean hasSystem(final ChatRequest request) {
        return Objects.requireNonNull(request, "request").messages().stream()
                .anyMatch(message -> message.role() == ChatRole.SYSTEM);
    }

    /**
     * The reply with the word joiners a thinking switch was sent with taken out again.
     *
     * @param reply the reply text; non-null
     * @return the text with every slash-and-joiner turned back into a slash
     */
    public static String unescaped(final String reply) {
        return Objects.requireNonNull(reply, "reply").replace(JOINED_SLASH, "/");
    }

    // ChatRequest keeps one copy method per field it changes for callers outside :llm; only this shaper changes
    // messages.
    private static ChatRequest withMessages(final ChatRequest r, final List<ChatMessage> messages) {
        return new ChatRequest(
                messages,
                r.temperature(),
                r.responseFormat(),
                r.reasoningEnabled(),
                r.contextWindow(),
                r.expectedOutputTokens(),
                r.maxOutputTokens(),
                r.seed(),
                r.callKind(),
                r.sampling(),
                r.outputField(),
                r.source(),
                r.reasoningReserve(),
                r.keepLoaded());
    }

    private static List<ChatMessage> withSystem(final List<ChatMessage> messages) {
        if (messages.stream().anyMatch(message -> message.role() == ChatRole.SYSTEM)) {
            return messages;
        }
        log.debug("No system message given; BookLoom's own default is sent");
        final List<ChatMessage> withSystem = new ArrayList<>();
        withSystem.add(new ChatMessage(ChatRole.SYSTEM, DEFAULT_SYSTEM));
        withSystem.addAll(messages);
        return withSystem;
    }

    private static List<ChatMessage> withoutReasoningLines(
            final ModelProfile profile, final List<ChatMessage> messages) {
        final boolean levelWords = profile.quirks().contains(Quirk.TEMPLATE_WRITES_REASONING);
        final boolean harmony = profile.quirks().contains(Quirk.HARMONY_FORMAT);
        if (!levelWords && !harmony) {
            return messages;
        }
        return messages.stream()
                .map(message -> {
                    String text = message.content();
                    if (levelWords && message.role() == ChatRole.SYSTEM) {
                        text = removed(REASONING_WORDS, text, "reasoning-words");
                    }
                    text = harmony ? removed(REASONING_LINE, text, "reasoning-line") : text;
                    return new ChatMessage(message.role(), text);
                })
                .toList();
    }

    private static String removed(final Pattern pattern, final String text, final String what) {
        final long found = pattern.matcher(text).results().count();
        if (found == 0) {
            return text;
        }
        log.debug("Lines left out of a message what={} count={}", what, found);
        return pattern.matcher(text).replaceAll("");
    }

    private static List<ChatMessage> escaped(final List<ChatMessage> messages) {
        final long switches = messages.stream()
                .filter(message -> message.role() != ChatRole.SYSTEM)
                .mapToLong(message ->
                        THINK_SWITCH.matcher(message.content()).results().count())
                .sum();
        log.debug("Thinking switches escaped in book text count={}", switches);
        return messages.stream()
                .map(message -> message.role() == ChatRole.SYSTEM
                        ? message
                        : new ChatMessage(
                                message.role(),
                                THINK_SWITCH.matcher(message.content()).replaceAll(JOINED_SLASH)))
                .toList();
    }

    private static List<ChatMessage> joinedRuns(final List<ChatMessage> messages) {
        final List<ChatMessage> joined = new ArrayList<>();
        for (final ChatMessage message : messages) {
            final int last = joined.size() - 1;
            if (last >= 0 && joined.get(last).role() == message.role()) {
                final ChatMessage previous = joined.get(last);
                joined.set(last, new ChatMessage(message.role(), previous.content() + BLANK_LINE + message.content()));
            } else {
                joined.add(message);
            }
        }
        return joined;
    }
}
