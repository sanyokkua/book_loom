package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * A model that answers every call of Names &amp; style about a {@link NamesBook} the way its gold says, except where
 * the book's {@code model} script says otherwise, for the offline proof of the names suite: the name scan lists every
 * candidate that is not junk, the review gives each gold character its type and gender (citing every window) and calls
 * the gold's junk "not-a-name" with a phrase copied from its first window, the target suggestions are the book's
 * script, and the recurring-term choice follows the gold's {@code recurringTerms} and drops every other word.
 */
final class NamesFakeModel implements ChatModel {

    private static final String ALL_WINDOWS = "[1,2,3,4,5,6]";
    private static final Pattern REVIEW_LINE = Pattern.compile("^- (.+?) — ");
    private static final Pattern WINDOW_LINE = Pattern.compile("^\\s+\\[\\d+] (.*)$");
    private static final Pattern CHOICE_LINE = Pattern.compile("^- (.+?)(?: · \\d+ uses)?(?: — .*)?$");
    private static final Pattern CANDIDATE_LINE = Pattern.compile("^(.+?) — ");

    private final NamesBook book;
    private final Map<String, JudgementGold.Person> characters;
    private final Map<String, NamesBook.Verdict> scripted;

    NamesFakeModel(final NamesBook book) {
        this.book = Objects.requireNonNull(book, "book");
        this.characters = book.gold().characters().stream()
                .collect(Collectors.toMap(JudgementGold.Person::term, person -> person));
        this.scripted =
                book.model().verdicts().stream().collect(Collectors.toMap(NamesBook.Verdict::term, verdict -> verdict));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        final String format =
                request.responseFormat() == null ? "" : request.responseFormat().name();
        final String user = request.messages().getLast().content();
        return Result.ok(new ChatResponse(answer(format, user), FinishReason.STOP));
    }

    private String answer(final String format, final String user) {
        if (format.equals(PromptName.REVIEW_TERMS.responseFormatName())) {
            return "{\"verdicts\":[" + String.join(",", verdicts(user)) + "]}";
        }
        if (format.equals(PromptName.PRESCAN.responseFormatName())) {
            return "{\"terms\":[" + String.join(",", candidates(user)) + "]}";
        }
        if (format.equals(PromptName.SUGGEST_TARGETS.responseFormatName())) {
            return "{\"suggestions\":[" + String.join(",", suggestions(user)) + "]}";
        }
        if (format.equals(PromptName.TERM_CHOICE.responseFormatName())) {
            return "{\"terms\":[" + String.join(",", choices(user)) + "]}";
        }
        return "{}";
    }

    // The review prompt lists "- term — N× — pronouns: …" and its numbered windows beneath, so a phrase can be copied.
    private List<String> verdicts(final String user) {
        final Map<String, String> firstWindow = new LinkedHashMap<>();
        String term = null;
        for (final String line : (Iterable<String>) user.lines()::iterator) {
            final Matcher entry = REVIEW_LINE.matcher(line);
            final Matcher window = WINDOW_LINE.matcher(line);
            if (entry.find()) {
                term = entry.group(1);
                firstWindow.put(term, "");
            } else if (window.matches() && term != null && firstWindow.get(term).isEmpty()) {
                firstWindow.put(term, window.group(1));
            }
        }
        return firstWindow.entrySet().stream()
                .map(listed -> verdict(listed.getKey(), listed.getValue()))
                .toList();
    }

    private String verdict(final String term, final String window) {
        final NamesBook.Verdict said = scripted.get(term);
        if (said != null) {
            return "{\"term\":\"%s\",\"verdict\":\"%s\",\"type\":\"%s\",\"gender\":\"%s\",\"windows\":%s,\"evidence\":\"\"}"
                    .formatted(term, said.verdict(), said.type(), said.gender(), said.windows());
        }
        if (isJunk(term)) {
            return ("{\"term\":\"%s\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\","
                            + "\"windows\":[],\"evidence\":\"%s\"}")
                    .formatted(term, escaped(window));
        }
        final JudgementGold.Person gold = characters.get(term);
        return "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"%s\",\"gender\":\"%s\",\"windows\":%s,\"evidence\":\"\"}"
                .formatted(
                        term,
                        gold == null ? "other" : typeWord(gold.type()),
                        gold == null ? "unknown" : genderWord(gold.gender()),
                        ALL_WINDOWS);
    }

    // Every candidate the prescan prompt lists as "term — first sentence" is proposed unless the gold calls it junk.
    private List<String> candidates(final String user) {
        final List<String> proposed = new ArrayList<>();
        boolean listed = false;
        for (final String line : (Iterable<String>) user.lines()::iterator) {
            if (line.startsWith("[Candidates")) {
                listed = true;
            } else if (line.isBlank()) {
                listed = false;
            } else if (listed) {
                final Matcher candidate = CANDIDATE_LINE.matcher(line);
                if (candidate.find() && !isJunk(candidate.group(1))) {
                    proposed.add(proposal(candidate.group(1)));
                }
            }
        }
        return proposed;
    }

    private String proposal(final String term) {
        final JudgementGold.Person gold = characters.get(term);
        return "{\"term\":\"%s\",\"type\":\"%s\",\"gender\":\"%s\",\"note\":\"\",\"confidence\":0.9}"
                .formatted(
                        term,
                        gold == null ? "other" : typeWord(gold.type()),
                        gold == null ? "unknown" : genderWord(gold.gender()));
    }

    private List<String> suggestions(final String user) {
        final List<String> suggested = new ArrayList<>();
        for (final String line : (Iterable<String>) user.lines()::iterator) {
            final Matcher entry = REVIEW_LINE.matcher(line);
            if (entry.find()) {
                final String term = entry.group(1);
                suggested.add("{\"term\":\"%s\",\"target\":\"%s\",\"gender\":\"unknown\"}"
                        .formatted(term, escaped(book.model().targets().getOrDefault(term, ""))));
            }
        }
        return suggested;
    }

    private List<String> choices(final String user) {
        final Map<String, Boolean> wanted = new LinkedHashMap<>();
        book.gold().recurringTerms().forEach(term -> wanted.put(term.term().toLowerCase(Locale.ROOT), term.keep()));
        wanted.putAll(book.model().terms());
        final List<String> chosen = new ArrayList<>();
        for (final String line : (Iterable<String>) user.lines()::iterator) {
            final Matcher entry = CHOICE_LINE.matcher(line);
            if (entry.matches()) {
                final String term = entry.group(1);
                chosen.add("{\"term\":\"%s\",\"keep\":%s}"
                        .formatted(term, wanted.getOrDefault(term.toLowerCase(Locale.ROOT), false)));
            }
        }
        return chosen;
    }

    private boolean isJunk(final String term) {
        return book.gold().junk().contains(term);
    }

    private static String escaped(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String typeWord(final TermType type) {
        return switch (type) {
            case CHARACTER -> "person";
            case PLACE -> "place";
            case TERM -> "term";
            case TITLE -> "title";
            case OTHER -> "other";
        };
    }

    private static String genderWord(final Gender gender) {
        return gender.name().toLowerCase(Locale.ROOT);
    }
}
