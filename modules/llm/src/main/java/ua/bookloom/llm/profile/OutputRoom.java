package ua.bookloom.llm.profile;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.util.lang.Script;

/**
 * The output a request can ask for inside its context window: the prompt and the reply share the window, so an answer
 * cap with a reasoning reserve on top, or a raised cap, never asks for more than the window leaves after the prompt.
 * The prompt is estimated from its characters at the cautious figure for an unknown script; the room never falls
 * below {@value #MIN_OUTPUT} tokens, so a prompt that fills the window still gets a reply the server can refuse.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OutputRoom {

    /** The least output any bounded request is given. */
    public static final int MIN_OUTPUT = 256;

    // The same margin the run's chunk estimates use, so a prompt is not under-counted.
    private static final double SAFETY_FACTOR = 1.15;

    /**
     * An answer cap and a reasoning reserve that together fit the room.
     *
     * @param cap the answer cap, or null when the request has none; positive when set
     * @param reserve the reasoning reserve; positive
     */
    public record Split(@Nullable Integer cap, int reserve) {}

    /**
     * The output tokens the request's window leaves after its prompt.
     *
     * @param request the request; non-null
     * @return the room, at least {@value #MIN_OUTPUT}, or null when the request carries no window
     */
    public static @Nullable Integer room(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        final Integer window = request.contextWindow();
        if (window == null) {
            return null;
        }
        final int prompt = promptTokens(request);
        final int room = Math.max(MIN_OUTPUT, window - prompt);
        log.debug("Output room window={} promptEstimate={} room={}", window, prompt, room);
        return room;
    }

    /**
     * The answer cap and the reasoning reserve, shrunk in proportion when together they exceed the room.
     *
     * @param request the request with its answer cap; non-null
     * @param reserve the reasoning reserve wanted; positive
     * @return the split; the request's cap and the reserve unchanged when they fit, the request has no cap or no window
     */
    public static Split split(final ChatRequest request, final int reserve) {
        final Integer cap = request.maxOutputTokens();
        final Integer room = room(request);
        if (cap == null || room == null || (long) cap + reserve <= room) {
            return new Split(cap, reserve);
        }
        final long total = (long) cap + reserve;
        final int boundedCap = (int) Math.max(1, (long) cap * room / total);
        final int boundedReserve = Math.max(1, room - boundedCap);
        log.debug(
                "Output clamped to the window kind={} cap={} reserve={} room={} boundedCap={} boundedReserve={}",
                request.callKind(),
                cap,
                reserve,
                room,
                boundedCap,
                boundedReserve);
        return new Split(boundedCap, boundedReserve);
    }

    private static int promptTokens(final ChatRequest request) {
        final long chars = request.messages().stream()
                .map(ChatMessage::content)
                .mapToLong(text -> text.codePointCount(0, text.length()))
                .sum();
        return (int)
                Math.min(Integer.MAX_VALUE, (long) Math.ceil(chars / Script.UNKNOWN.charsPerToken() * SAFETY_FACTOR));
    }
}
