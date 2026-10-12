package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.eval.StageCases.SetupCase;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * A model scripted from the setup cases' gold, for the offline proof of the per-field score: it finds which case a
 * request is about (by the file name or the case's first words in the request), answers the brief with the case's gold
 * genre, register and narrator, quoting the sample it was given, and answers every field of a case in {@code wrongIds}
 * the other way (the other narrator, and a register that is not the gold's). The narrator's gender is always unknown,
 * as a model that can verify nothing from three paragraphs says. Asked what it knows of a book, it knows the classics
 * (echoing their title, author and year) and not the invented books, and a case in {@code wrongIds} gets the other
 * answer.
 */
final class SetupScriptedModel implements ChatModel {

    private static final Pattern FIRST_PASSAGE = Pattern.compile("Passage 1: (\\S+(?: \\S+){0,4})");
    private static final Pattern FIRST_PERSON = Pattern.compile("(?<=\\s)I \\p{L}+");
    private static final int PREFIX = 24;
    private static final String NOT_KNOWN = "{\"known\":false,\"title\":\"\",\"author\":\"\",\"year\":0,"
            + "\"genre\":\"\",\"audience\":\"\",\"era\":\"\",\"narration\":\"\",\"summary\":\"\","
            + "\"confidence\":0.0}";

    private final List<SetupCase> cases;
    private final Set<String> wrongIds;
    // The case the last window was about: the synthesis that follows names a genre of its class (15i.B4).
    private @Nullable SetupCase lastCase;

    SetupScriptedModel(final List<SetupCase> cases, final Set<String> wrongIds) {
        this.cases = List.copyOf(Objects.requireNonNull(cases, "cases"));
        this.wrongIds = Set.copyOf(Objects.requireNonNull(wrongIds, "wrongIds"));
    }

    @Override
    public Result<ChatResponse> chat(final ChatRequest request) {
        final String format =
                request.responseFormat() == null ? "" : request.responseFormat().name();
        final String user = request.messages().getLast().content();
        if (format.equals(PromptName.FILE_NAME.responseFormatName())) {
            return ok("{\"target\":\"Книга. Автор. 2000\"}");
        }
        if (format.equals(PromptName.BOOK_KNOWLEDGE.responseFormatName())) {
            return ok(knowledge(user));
        }
        if (format.equals(PromptName.BRIEF_SYNTHESIS.responseFormatName())) {
            return ok(SynthesisReplies.of(lastCase == null ? null : SynthesisReplies.inClass(lastCase.genreClass())));
        }
        final Optional<SetupCase> found = caseOf(user);
        found.ifPresent(setupCase -> lastCase = setupCase);
        return ok(found.map(setupCase -> brief(setupCase, user)).orElse("{}"));
    }

    // A model that knows the classics echoes their title, author and year; one that is asked about an invented book
    // says it does not know. A case in wrongIds is answered the other way: a classic it "does not know", an invented
    // book it "knows", echoing what it was asked (a hallucination the echo check cannot catch by itself).
    private String knowledge(final String user) {
        final Optional<SetupCase> found = cases.stream()
                .filter(setupCase ->
                        setupCase.hasTitle() && user.contains(setupCase.title()) && user.contains(setupCase.author()))
                .findFirst();
        if (found.isEmpty()) {
            return NOT_KNOWN;
        }
        final SetupCase setupCase = found.get();
        final boolean knows = setupCase.classic() != wrongIds.contains(setupCase.id());
        return knows
                ? "{\"known\":true,\"title\":\"" + escaped(setupCase.title()) + "\",\"author\":\""
                        + escaped(setupCase.author()) + "\",\"year\":" + setupCase.year()
                        + ",\"genre\":\"novel\",\"audience\":\"adult\",\"era\":\"\",\"narration\":\"\","
                        + "\"summary\":\"\",\"confidence\":0.9}"
                : NOT_KNOWN;
    }

    private Optional<SetupCase> caseOf(final String user) {
        return cases.stream().filter(setupCase -> isAbout(setupCase, user)).findFirst();
    }

    // A request names the file, the title, or holds the start of one of the case's paragraphs (a sample is some of
    // them).
    private static boolean isAbout(final SetupCase setupCase, final String user) {
        return user.contains(setupCase.fileStem())
                || (setupCase.hasTitle() && user.contains("Title: " + setupCase.title() + "\n"))
                || setupCase.paragraphs().stream().anyMatch(paragraph -> user.contains(paragraph.substring(0, PREFIX)));
    }

    private String brief(final SetupCase setupCase, final String user) {
        final boolean wrong = wrongIds.contains(setupCase.id());
        final Matcher passage = FIRST_PASSAGE.matcher(user);
        final String quote = passage.find() ? escaped(passage.group(1)) : "";
        final Matcher first = FIRST_PERSON.matcher(user);
        final String register = register(setupCase.register(), wrong);
        final boolean saysFirst = (setupCase.narrator() == NarratorPerson.FIRST) != wrong;
        return "{" + field("genre", setupCase.genreClass().getFirst(), quote)
                + "," + field("register", register, "neutral".equals(register) ? "" : quote)
                + "," + field("voice", "plain", quote)
                + "," + field("audience", "adults", quote)
                + ","
                + field("narrator", saysFirst ? "first" : "third", saysFirst && first.find() ? first.group() : quote)
                + "," + field("narratorGender", "unknown", "") + "}";
    }

    private static String register(final Register register, final boolean wrong) {
        if (wrong) {
            return register == Register.CASUAL ? "formal" : "casual";
        }
        return switch (register) {
            case FORMAL_LITERARY -> "formal";
            case NEUTRAL -> "neutral";
            case CASUAL -> "casual";
        };
    }

    private static String field(final String name, final String value, final String quote) {
        return "\"" + name + "\":{\"value\":\"" + escaped(value.toLowerCase(Locale.ROOT)) + "\",\"evidence\":\"" + quote
                + "\",\"confidence\":0.9}";
    }

    private static String escaped(final String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Result<ChatResponse> ok(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
