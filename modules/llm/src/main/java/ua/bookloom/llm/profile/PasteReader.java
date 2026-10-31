package ua.bookloom.llm.profile;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes the wrapping off a pasted name: quotes, a shell command, a link, an OpenRouter alias mark and variant suffix,
 * an Ollama digest. What is left is the id the name splitter reads. Pure text work; nothing is opened or run.
 */
@Slf4j
final class PasteReader {

    private static final Pattern DIGEST = Pattern.compile("@sha256:[0-9a-f]{6,}$");
    private static final Pattern ROUTER_SUFFIX =
            Pattern.compile("([^:]+(?:/[^:]+)?)((?::(?:" + String.join("|", routerWords()) + "))+)");

    private final CommandReader commands = new CommandReader();
    private final LinkReader links = new LinkReader();

    PasteContext read(final String raw) {
        Objects.requireNonNull(raw, "raw");
        final PasteContext ctx = new PasteContext(stripQuotes(raw.strip()));
        ctx.text = commands.read(ctx.text, ctx);
        if (isBareHostLink(ctx.text)) {
            ctx.text = "https://" + ctx.text;
        }
        if (ctx.text.contains("://")) {
            ctx.text = links.read(ctx.text, ctx);
        }
        if (ctx.text.startsWith("~")) {
            ctx.text = ctx.text.substring(1);
            ctx.alias = "latest";
        }
        ctx.text = DIGEST.matcher(ctx.text).replaceFirst("");
        final Matcher router = ROUTER_SUFFIX.matcher(ctx.text);
        if (router.matches() && router.group(1).contains("/")) {
            ctx.routerVariants.addAll(Arrays.asList(router.group(2).substring(1).split(":")));
            ctx.text = router.group(1);
            ctx.inputForm = ctx.inputForm == null ? "openrouter-id" : ctx.inputForm;
        }
        log.debug("Read paste form={} id={} repo={}", ctx.inputForm, ctx.text, ctx.repo);
        return ctx;
    }

    private static boolean isBareHostLink(final String text) {
        final String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("huggingface.co/") || lower.startsWith("ollama.com/");
    }

    private static String stripQuotes(final String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isQuote(text.charAt(start))) {
            start++;
        }
        while (end > start && isQuote(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isQuote(final char c) {
        return c == '`' || c == '\'' || c == '"';
    }

    private static String[] routerWords() {
        return NameVocabulary.ROUTER_VARIANTS.stream()
                .sorted()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .toArray(String[]::new);
    }
}
