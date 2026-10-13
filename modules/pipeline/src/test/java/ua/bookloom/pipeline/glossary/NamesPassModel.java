package ua.bookloom.pipeline.glossary;

import static ua.bookloom.pipeline.glossary.LexiconReplies.termOf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;

/**
 * A hand-written model for the names pass tests: it answers each call by the response format the request names, from
 * tables about the invented book, records every call, and runs a hook before answering (the test uses it to look at
 * the stored tables mid-pass and to move the clock). It can fail the first call of one format or the n-th call.
 */
final class NamesPassModel implements ChatModel {

    private static final Pattern REVIEW_LINE = Pattern.compile("(?m)^- (.+?) — \\d+×");
    private static final Pattern SUGGEST_LINE = Pattern.compile("(?m)^- (.+?) — ");
    private static final Pattern READING_WINDOW = Pattern.compile("(?m)^\\[(\\d{1,6})] ");

    /** What the model says of a term read in context. */
    record Reading(String kind, boolean speaking, boolean brand) {}

    private final List<String> formats = new CopyOnWriteArrayList<>();
    private final List<CallKind> kinds = new CopyOnWriteArrayList<>();
    private final Map<String, Reading> readings = new LinkedHashMap<>();
    private final Map<String, String> lexiconUsage = new LinkedHashMap<>();
    private final Map<String, String> targets = new LinkedHashMap<>();
    private final List<String> notNames = new ArrayList<>();
    private Runnable hook = () -> {};
    private @Nullable String failingFormat;
    private int failingCall;
    private @Nullable AppError failure;

    NamesPassModel reads(final String term, final String kind, final boolean speaking, final boolean brand) {
        readings.put(term, new Reading(kind, speaking, brand));
        return this;
    }

    /** How the recurring-term reading answers for a word: {@code term}, {@code word} or {@code name}; others are words. */
    NamesPassModel usesWord(final String word, final String usage) {
        lexiconUsage.put(word, usage);
        return this;
    }

    NamesPassModel suggests(final String term, final String target) {
        targets.put(term, target);
        return this;
    }

    /** The batched review calls this term not a name, quoting the term itself. */
    NamesPassModel callsNotAName(final String term) {
        notNames.add(term);
        return this;
    }

    NamesPassModel beforeEachCall(final Runnable action) {
        hook = action;
        return this;
    }

    /** Fails the first call whose response format has this name. */
    NamesPassModel failingFirst(final String format, final AppError error) {
        failingFormat = format;
        failure = error;
        return this;
    }

    /** Fails the n-th call, counted from one. */
    NamesPassModel failingNth(final int n, final AppError error) {
        failingCall = n;
        failure = error;
        return this;
    }

    List<String> formats() {
        return List.copyOf(formats);
    }

    List<CallKind> kinds() {
        return List.copyOf(kinds);
    }

    int requests() {
        return formats.size();
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        Objects.requireNonNull(request, "request");
        hook.run();
        final String format =
                request.responseFormat() == null ? "" : request.responseFormat().name();
        formats.add(format);
        kinds.add(request.callKind());
        final boolean isNth = failingCall == formats.size();
        final boolean isFirstOfFormat = format.equals(failingFormat) && formats.indexOf(format) == formats.size() - 1;
        if (isNth || isFirstOfFormat) {
            return Result.err(Objects.requireNonNull(failure, "failure"));
        }
        final String user = request.messages().getLast().content();
        return Result.ok(new ChatResponse(replyTo(format, user), FinishReason.STOP));
    }

    private String replyTo(final String format, final String user) {
        return switch (format) {
            case "review-terms" -> reviewReply(user);
            case "term-reading" -> readingReply(user);
            case "lexicon-reading" -> lexiconReply(user);
            case "suggest-targets" -> suggestReply(user);
            case "prescan" -> "{\"terms\":[]}";
            case "term-choice" -> "{\"terms\":[]}";
            default -> "{}";
        };
    }

    private String reviewReply(final String user) {
        final Matcher matcher = REVIEW_LINE.matcher(user);
        final List<String> verdicts = new ArrayList<>();
        while (matcher.find()) {
            final String term = matcher.group(1);
            verdicts.add(
                    notNames.contains(term)
                            ? "{\"term\":\"%s\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\",\"windows\":[],\"evidence\":\"%s\"}"
                                    .formatted(term, term)
                            : "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\",\"windows\":[],\"evidence\":\"\"}"
                                    .formatted(term));
        }
        return "{\"verdicts\":[" + String.join(",", verdicts) + "]}";
    }

    // Every window is a use of the name the model knows, with no pronoun quoted: the code's own tally decides gender.
    private String readingReply(final String user) {
        final Reading reading = readings.getOrDefault(termOf(user), new Reading("other", false, false));
        final Matcher matcher = READING_WINDOW.matcher(user);
        final List<String> windows = new ArrayList<>();
        while (matcher.find()) {
            windows.add("{\"window\":%s,\"quote\":\"none\",\"usage\":\"name\",\"kind\":\"%s\",\"gender\":\"none\"}"
                    .formatted(matcher.group(1), reading.kind()));
        }
        return "{\"windows\":[%s],\"speaking\":%s,\"brand\":%s}"
                .formatted(String.join(",", windows), reading.speaking(), reading.brand());
    }

    private String lexiconReply(final String user) {
        final String term = termOf(user);
        final String usage = lexiconUsage.getOrDefault(term, "word");
        return LexiconReplies.reading(term, usage, 1, "");
    }

    private String suggestReply(final String user) {
        final Matcher matcher = SUGGEST_LINE.matcher(user);
        final List<String> suggestions = new ArrayList<>();
        while (matcher.find()) {
            final String term = matcher.group(1);
            suggestions.add("{\"term\":\"%s\",\"target\":\"%s\",\"gender\":\"unknown\"}"
                    .formatted(term, targets.getOrDefault(term, "")));
        }
        return "{\"suggestions\":[" + String.join(",", suggestions) + "]}";
    }
}
