package ua.bookloom.pipeline.eval;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * A model that answers the glossary and setup stages the way a good one would for the stage cases' invented text, by the
 * response-format name of the request, so the runners are proven end to end without a provider.
 */
final class StageFakeModel implements ChatModel {

    private static final Pattern FIRST_PASSAGE = Pattern.compile("Passage 1: ((?:\\S+ ){4}\\S+)");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?<=\\s)I \\p{L}+");
    private static final Pattern SCAN_LINE = Pattern.compile("(?m)^(\\S+) — ");
    private static final Pattern TERM_LINE = Pattern.compile("(?m)^- (.+?) (?:·|—) ");
    private static final Map<String, String> PEOPLE =
            Map.of("Flint", "female", "Marta", "female", "Флинт", "female", "Марта", "female");
    private static final Map<String, String> PLACES = Map.of("Koreth", "place", "Корет", "place");
    private static final Map<String, Boolean> TERMS =
            Map.of("datavault", true, "gridrunner", true, "нейрошунт", true, "сетевик", true);

    private final boolean silent;

    StageFakeModel(final boolean silent) {
        this.silent = silent;
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        final String name =
                request.responseFormat() == null ? "" : request.responseFormat().name();
        final String user = request.messages().getLast().content();
        return Result.ok(new ChatResponse(silent ? "{}" : answer(name, user), FinishReason.STOP));
    }

    private static String answer(final String format, final String user) {
        if (format.equals(PromptName.PRESCAN.responseFormatName())) {
            return "{\"terms\":[" + String.join(",", lines(SCAN_LINE, user, StageFakeModel::proposal)) + "]}";
        }
        if (format.equals(PromptName.TERM_CHOICE.responseFormatName())) {
            return "{\"terms\":[" + String.join(",", lines(TERM_LINE, user, StageFakeModel::choice)) + "]}";
        }
        if (format.equals(PromptName.REVIEW_TERMS.responseFormatName())) {
            return "{\"verdicts\":[" + String.join(",", lines(TERM_LINE, user, StageFakeModel::verdict)) + "]}";
        }
        if (format.equals(PromptName.SUGGEST_TARGETS.responseFormatName())) {
            return "{\"suggestions\":[]}";
        }
        if (format.equals(PromptName.FILE_NAME.responseFormatName())) {
            return "{\"target\":\"Джейн Остлер. Скляний сад. 2009\"}";
        }
        return brief(user);
    }

    // A good model quotes the sample it was given: the first words of its first passage, and an "I" clause for a
    // first-person narrator.
    private static String brief(final String user) {
        final Matcher passage = FIRST_PASSAGE.matcher(user);
        final String quote = passage.find() ? passage.group(1) : "";
        final Matcher first = FIRST_PERSON.matcher(user);
        final boolean isFirst = first.find();
        return "{" + field("genre", "drama", quote) + "," + field("register", "neutral", "") + ","
                + field("voice", "plain", quote) + "," + field("audience", "adults", quote) + ","
                + field("narrator", isFirst ? "first" : "third", isFirst ? first.group() : quote) + ","
                + field("narratorGender", "unknown", "") + "}";
    }

    private static String field(final String name, final String value, final String quote) {
        return "\"" + name + "\":{\"value\":\"" + value + "\",\"evidence\":\"" + quote + "\",\"confidence\":0.9}";
    }

    private static java.util.List<String> lines(
            final Pattern pattern, final String user, final java.util.function.Function<String, String> entry) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        final Matcher matcher = pattern.matcher(user);
        while (matcher.find()) {
            out.add(entry.apply(matcher.group(1)));
        }
        return out;
    }

    private static String proposal(final String term) {
        if (PEOPLE.containsKey(term)) {
            return "{\"term\":\"%s\",\"type\":\"person\",\"gender\":\"%s\"}".formatted(term, PEOPLE.get(term));
        }
        return PLACES.containsKey(term)
                ? "{\"term\":\"%s\",\"type\":\"place\"}".formatted(term)
                : "{\"term\":\"%s\"}".formatted(term);
    }

    private static String choice(final String term) {
        return "{\"term\":\"%s\",\"keep\":%s}".formatted(term, TERMS.getOrDefault(term, false));
    }

    private static String verdict(final String term) {
        if (PEOPLE.containsKey(term)) {
            return "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"%s\"}"
                    .formatted(term, PEOPLE.get(term));
        }
        return PLACES.containsKey(term)
                ? "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"place\"}".formatted(term)
                : "{\"term\":\"%s\",\"verdict\":\"not-a-name\",\"evidence\":\"%s\"}".formatted(term, term);
    }
}
