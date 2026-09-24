package ua.bookloom.llm.response;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/** Removes reasoning tags and an outer code fence from provider reply text. */
@Slf4j
public final class ReplySanitizer {

    private static final Pattern COMPLETE_REASONING_BLOCK =
            Pattern.compile("(?is)<(think|thinking|reasoning)>.*?</\\1>");
    private static final Pattern UNTERMINATED_REASONING_BLOCK = Pattern.compile("(?is)<(think|thinking|reasoning)>.*$");
    private static final Pattern BARE_JSON_LABEL = Pattern.compile("(?is)^json\\s*(?=\\{)");

    private ReplySanitizer() {}

    /** Removes reasoning blocks, unwraps one outer backtick fence, and trims the remaining text. */
    public static String clean(String reply) {
        Objects.requireNonNull(reply, "reply");
        final Replacement complete = remove(COMPLETE_REASONING_BLOCK, reply);
        final Replacement unterminated = remove(UNTERMINATED_REASONING_BLOCK, complete.text());
        final FenceUnwrap fence = unwrapOuterFence(unterminated.text());
        final Replacement bareJsonLabel = remove(BARE_JSON_LABEL, fence.text());
        final String cleaned = bareJsonLabel.text().trim();
        log.debug(
                "Reply sanitized inputLength={} outputLength={} completeReasoningBlocksRemoved={} "
                        + "unterminatedReasoningBlocksRemoved={} codeFenceLinesRemoved={} bareJsonLabelsRemoved={}",
                reply.length(),
                cleaned.length(),
                complete.matches(),
                unterminated.matches(),
                fence.linesRemoved(),
                bareJsonLabel.matches());
        log.trace("Reply sanitizer cleaned text={}", cleaned);
        return cleaned;
    }

    private static Replacement remove(Pattern pattern, String text) {
        final Matcher matcher = pattern.matcher(text);
        final int matches = Math.toIntExact(matcher.results().count());
        return new Replacement(pattern.matcher(text).replaceAll(""), matches);
    }

    private static FenceUnwrap unwrapOuterFence(String text) {
        final String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return new FenceUnwrap(trimmed, 0);
        }
        final int headerEnd = trimmed.indexOf('\n');
        if (headerEnd < 0) {
            return new FenceUnwrap("", 1);
        }
        final String body = trimmed.substring(headerEnd + 1).trim();
        final int closingFenceStart = trailingFenceLineStart(body);
        if (closingFenceStart < 0) {
            return new FenceUnwrap(body, 1);
        }
        return new FenceUnwrap(body.substring(0, closingFenceStart).trim(), 2);
    }

    private static int trailingFenceLineStart(String text) {
        if (!text.endsWith("```")) {
            return -1;
        }
        final int fenceStart = text.length() - 3;
        if (fenceStart == 0) {
            return 0;
        }
        final char preceding = text.charAt(fenceStart - 1);
        return preceding == '\n' || preceding == '\r' ? fenceStart - 1 : -1;
    }

    private record Replacement(String text, int matches) {}

    private record FenceUnwrap(String text, int linesRemoved) {}
}
