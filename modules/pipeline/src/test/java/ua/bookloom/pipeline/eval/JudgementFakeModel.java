package ua.bookloom.pipeline.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * A model that judges a {@link JudgementBook} the way its gold says, for the offline proof of the judgement suites: the
 * brief reply quotes the sample it was given (the first words of its first passage, an "I" clause for a first-person
 * narrator, and the book's narrator quote when the sample holds one), and the review gives each listed term its gold
 * type and gender, citing every window. Under a seed in {@code wrongSeeds} it answers like an unstable model: a casual
 * register and every character's other gender.
 */
final class JudgementFakeModel implements ChatModel {

    private static final Pattern FIRST_PASSAGE = Pattern.compile("Passage 1: ((?:\\S+ ){4}\\S+)");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?<=\\s)I \\p{L}+");
    private static final Pattern TERM_LINE = Pattern.compile("(?m)^- (.+?) — ");
    private static final String ALL_WINDOWS = "[1,2,3,4,5,6]";

    private final JudgementBook book;
    private final Set<Integer> wrongSeeds;
    private final Map<String, JudgementGold.Person> characters;

    JudgementFakeModel(final JudgementBook book, final Set<Integer> wrongSeeds) {
        this.book = Objects.requireNonNull(book, "book");
        this.wrongSeeds = Set.copyOf(wrongSeeds);
        this.characters = book.gold().characters().stream()
                .collect(Collectors.toMap(JudgementGold.Person::term, Function.identity()));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        final String format =
                request.responseFormat() == null ? "" : request.responseFormat().name();
        final String user = request.messages().getLast().content();
        final boolean wrong = request.seed() != null && wrongSeeds.contains(request.seed());
        return Result.ok(new ChatResponse(answer(format, user, wrong), FinishReason.STOP));
    }

    private String answer(final String format, final String user, final boolean wrong) {
        if (format.equals(PromptName.REVIEW_TERMS.responseFormatName())) {
            return "{\"verdicts\":[" + String.join(",", verdicts(user, wrong)) + "]}";
        }
        if (format.equals(PromptName.SUGGEST_TARGETS.responseFormatName())) {
            return "{\"suggestions\":[]}";
        }
        return brief(user, wrong);
    }

    private String brief(final String user, final boolean wrong) {
        final JudgementGold.Brief gold = book.gold().brief();
        final Matcher passage = FIRST_PASSAGE.matcher(user);
        final String quote = passage.find() ? passage.group(1).replace("\"", "\\\"") : "";
        final String register = wrong ? "casual" : registerWord(gold.register());
        return "{" + field("genre", Objects.requireNonNullElse(gold.genre(), ""), quote)
                + "," + field("register", register, "neutral".equals(register) ? "" : quote)
                + "," + field("voice", "plain", quote)
                + "," + field("audience", "adult readers", quote)
                + "," + narrator(gold.narrator(), user, quote)
                + "," + narratorGender(gold.narratorGender(), user, wrong) + "}";
    }

    private static String narrator(final NarratorPerson person, final String user, final String quote) {
        final Matcher first = FIRST_PERSON.matcher(user);
        return switch (person) {
            case FIRST -> field("narrator", "first", first.find() ? first.group() : "");
            case THIRD -> field("narrator", "third", quote);
            case UNSPECIFIED -> field("narrator", "unspecified", "");
        };
    }

    private String narratorGender(final Gender gender, final String user, final boolean wrong) {
        final JudgementBook.Quote shown = book.narratorQuotes().stream()
                .filter(candidate -> user.contains(candidate.quote()))
                .findFirst()
                .orElse(null);
        if (wrong || shown == null || gender == Gender.UNKNOWN) {
            return "\"narratorGender\":{\"value\":\"unknown\",\"evidence\":\"\",\"name\":\"\",\"confidence\":0.5}";
        }
        return "\"narratorGender\":{\"value\":\"%s\",\"evidence\":\"%s\",\"name\":\"%s\",\"confidence\":0.9}"
                .formatted(gender.name().toLowerCase(Locale.ROOT), shown.quote().replace("\"", "\\\""), shown.name());
    }

    private static String field(final String name, final String value, final String quote) {
        return "\"" + name + "\":{\"value\":\"" + value + "\",\"evidence\":\"" + quote + "\",\"confidence\":0.9}";
    }

    private static String registerWord(final Register register) {
        return switch (register) {
            case FORMAL_LITERARY -> "formal";
            case NEUTRAL -> "neutral";
            case CASUAL -> "casual";
        };
    }

    private List<String> verdicts(final String user, final boolean wrong) {
        final List<String> verdicts = new ArrayList<>();
        final Matcher line = TERM_LINE.matcher(user);
        while (line.find()) {
            final String term = line.group(1);
            final JudgementGold.Person gold = characters.get(term);
            verdicts.add(
                    gold == null
                            ? "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\",\"windows\":[]}"
                                    .formatted(term)
                            : "{\"term\":\"%s\",\"verdict\":\"name\",\"type\":\"%s\",\"gender\":\"%s\",\"windows\":%s}"
                                    .formatted(
                                            term,
                                            typeWord(gold.type()),
                                            genderWord(gold.gender(), wrong),
                                            ALL_WINDOWS));
        }
        return verdicts;
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

    private static String genderWord(final Gender gender, final boolean wrong) {
        final Gender said =
                switch (gender) {
                    case FEMALE -> wrong ? Gender.MALE : Gender.FEMALE;
                    case MALE -> wrong ? Gender.FEMALE : Gender.MALE;
                    case NEUTER, UNKNOWN -> gender;
                };
        return said.name().toLowerCase(Locale.ROOT);
    }
}
