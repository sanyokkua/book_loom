package ua.bookloom.llm.response;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.llm.ModelProfile.MarkupFilter;

/**
 * Removes every known reasoning and turn marker from a reply, then the filters a model's profile switches on, then one
 * outer code fence and a leading {@code json} label (design D20). The markers never occur in book prose, so they are
 * removed from every reply; an opener never closed is cut to the end only for a think tag, or where it opens the reply,
 * so a book's literal "[think]" stays; the two filters ({@code to=self}, {@code \boxed{}}) could match real text and run only for
 * the models that write them.
 */
@Slf4j
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReasoningScrubber {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    // A think tag never occurs in prose, so one never closed is cut wherever it stands; the other openers can be words
    // a book holds ("[think]", "<reasoning>"), so they count only where they open the reply, as a reply cut off while
    // thinking does.
    private static final String ANYWHERE_OPENERS = "<(?:think|thinking)>";
    private static final String LEADING_OPENERS = "\\A\\s*(?:<reasoning>|\\[THINK\\]|<\\|channel>thought)";

    // gpt-oss's harmony format: the answer is what follows the last final channel header.
    private static final Pattern HARMONY_FINAL =
            Pattern.compile("<\\|channel\\|>\\s*final\\s*(?:<\\|constrain\\|>\\s*\\w+\\s*)?<\\|message\\|>", FLAGS);

    // Applied in order: whole blocks first, then a closer whose opener was cut off, then an opener never closed, then
    // the turn tokens a template leaked.
    private static final List<Rule> MARKERS = List.of(
            new Rule("think-block", "<(think|thinking|reasoning)>.*?</\\1>"),
            new Rule("think-bracket", "\\[THINK\\].*?\\[/THINK\\]"),
            new Rule("gemma-thought", "<\\|channel>thought.*?<channel\\|>"),
            new Rule(
                    "harmony-analysis",
                    "<\\|channel\\|>\\s*(?:analysis|commentary)\\s*<\\|message\\|>.*?(?:<\\|end\\|>|\\z)"),
            new Rule("closer-only", "\\A.*</(?:think|thinking|reasoning)>"),
            new Rule("bracket-closer-only", "\\A.*\\[/THINK\\]"),
            new Rule("unclosed-opener", ANYWHERE_OPENERS + ".*\\z"),
            new Rule("unclosed-leading-opener", LEADING_OPENERS + ".*\\z"),
            new Rule("bare-thought", "\\A\\s*thought\\s*<channel\\|>"),
            new Rule(
                    "turn-token",
                    "<\\|start\\|>assistant|<\\|(?:return|end|call|eot|im_end|im_start)\\|>"
                            + "|<\\|constrain\\|>\\s*json|<channel\\|>|<turn\\|>|</?end_of_turn>"
                            + "|<start_of_turn>(?:model)?"));

    private static final Pattern UNCLOSED = Pattern.compile(ANYWHERE_OPENERS + "|" + LEADING_OPENERS, FLAGS);

    // muse-glimmer's hidden note to itself, up to the header of the answer, and that header.
    private static final List<Rule> TO_SELF = List.of(
            new Rule("to-self", "to=self<\\|message\\|>.*?(?=to=user<\\|message\\|>|\\z)"),
            new Rule("to-user", "\\A\\s*to=user<\\|message\\|>"));

    // Magistral's whole answer, or a whole JSON string, wrapped in one \boxed{…}.
    private static final Pattern BOXED_WHOLE = Pattern.compile("\\A\\s*\\\\boxed\\{(.*)}\\s*\\z", Pattern.DOTALL);
    private static final Pattern BOXED_STRING = Pattern.compile("\"\\\\\\\\boxed\\{((?:[^\"\\\\]|\\\\.)*?)}\"");
    private static final Pattern BARE_JSON_LABEL = Pattern.compile("(?is)^json\\s*(?=\\{)");
    private static final String FENCE = "```";

    /**
     * Scrubs a reply with no profile filter, as the eval judge and a model with no row read it.
     *
     * @param reply the reply text as received; non-null
     * @return the answer, trimmed; empty when the reply was only reasoning
     */
    public static String scrub(final String reply) {
        return scrub(reply, Set.of());
    }

    /**
     * Scrubs a reply, running the filters the model's profile switches on.
     *
     * @param reply the reply text as received; non-null
     * @param filters the profile's markup filters; never null, may be empty
     * @return the answer, trimmed; empty when the reply was only reasoning
     */
    public static String scrub(final String reply, final Set<MarkupFilter> filters) {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(filters, "filters");
        final Counts counts = new Counts();
        String text = afterHarmonyFinal(reply, counts);
        text = applyAll(MARKERS, text, counts);
        if (filters.contains(MarkupFilter.TO_SELF)) {
            text = applyAll(TO_SELF, text, counts);
        }
        if (filters.contains(MarkupFilter.BOXED)) {
            text = unboxed(text, counts);
        }
        text = unwrapFence(text.trim(), counts);
        text = counted("json-label", BARE_JSON_LABEL, text, "", counts).trim();
        log.debug(
                "Reply scrubbed inputLength={} outputLength={} filters={} removed={}",
                reply.length(),
                text.length(),
                filters,
                counts.text());
        log.trace("Reply scrubbed text={}", text);
        return text;
    }

    /**
     * Whether a reply holds a reasoning opener that is never closed, which is a reply cut off while it was thinking.
     *
     * @param reply the reply text as received; non-null
     * @return {@code true} if an opener is never closed, {@code false} otherwise
     */
    public static boolean hasUnclosedOpener(final String reply) {
        Objects.requireNonNull(reply, "reply");
        final String closed = applyAll(MARKERS.subList(0, 3), reply, new Counts());
        return UNCLOSED.matcher(closed).find();
    }

    private static String afterHarmonyFinal(final String reply, final Counts counts) {
        final Matcher matcher = HARMONY_FINAL.matcher(reply);
        int end = -1;
        while (matcher.find()) {
            end = matcher.end();
        }
        if (end < 0) {
            return reply;
        }
        counts.add("harmony-final", 1);
        return reply.substring(end);
    }

    private static String applyAll(final List<Rule> rules, final String text, final Counts counts) {
        String result = text;
        for (final Rule rule : rules) {
            result = counted(rule.name(), rule.pattern(), result, "", counts);
        }
        return result;
    }

    private static String unboxed(final String text, final Counts counts) {
        final String whole = counted("boxed", BOXED_WHOLE, text, "$1", counts);
        return counted("boxed-string", BOXED_STRING, whole, "\"$1\"", counts);
    }

    private static String counted(
            final String name,
            final Pattern pattern,
            final String text,
            final String replacement,
            final Counts counts) {
        final Matcher matcher = pattern.matcher(text);
        final int found = Math.toIntExact(matcher.results().count());
        if (found == 0) {
            return text;
        }
        counts.add(name, found);
        return pattern.matcher(text).replaceAll(replacement);
    }

    private static String unwrapFence(final String text, final Counts counts) {
        if (!text.startsWith(FENCE)) {
            return text;
        }
        counts.add("fence", 1);
        final int headerEnd = text.indexOf('\n');
        if (headerEnd < 0) {
            return "";
        }
        final String body = text.substring(headerEnd + 1).trim();
        if (!body.endsWith(FENCE)) {
            return body;
        }
        final int closing = body.length() - FENCE.length();
        final boolean onItsLine = closing == 0 || body.charAt(closing - 1) == '\n' || body.charAt(closing - 1) == '\r';
        return onItsLine ? body.substring(0, closing).trim() : body;
    }

    private record Rule(String name, Pattern pattern) {
        Rule(final String name, final String regex) {
            this(name, Pattern.compile(regex, FLAGS));
        }
    }

    // How many of each marker were removed, for the one DEBUG line; never the text.
    private static final class Counts {
        private final StringBuilder text = new StringBuilder();

        void add(final String name, final int count) {
            text.append(text.isEmpty() ? "" : ",").append(name).append('=').append(count);
        }

        String text() {
            return text.isEmpty() ? "none" : text.toString();
        }
    }
}
