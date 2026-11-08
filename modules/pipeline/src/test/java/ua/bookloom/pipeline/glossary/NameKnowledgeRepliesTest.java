package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.PlaceKind;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.AnalysisData;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * The name-knowledge stage (design D5): bare names in batches of 40, an answer kept only when it repeats its name, a
 * missing one asked once more, and every kept answer a likely value that never replaces the person's, the book's or a
 * rule's.
 */
class NameKnowledgeRepliesTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final Pattern LINE = Pattern.compile("(?m)^- (.+)$");

    private final ObjectMapper mapper = new ObjectMapper();
    private final NameKnowledgeCalls knowledge = new NameKnowledgeCalls(new PromptTemplates(), mapper);

    /** Answers every name it is shown from a table; a name it does not know it leaves out; one name it may misspell once. */
    private static final class BareNameModel implements ModelCalls {

        private final Map<String, String> answers;
        private final @Nullable String misspelledOnce;
        private final @Nullable String misspelling;
        private final List<List<String>> asked = new ArrayList<>();

        BareNameModel(
                final Map<String, String> answers,
                @Nullable final String misspelledOnce,
                @Nullable final String misspelling) {
            this.answers = answers;
            this.misspelledOnce = misspelledOnce;
            this.misspelling = misspelling;
        }

        @Override
        public Result<ChatResponse> call(
                final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
            assertThat(kind).isEqualTo(CallKind.NAME_KNOWLEDGE);
            final List<String> names = new ArrayList<>();
            final Matcher matcher =
                    LINE.matcher(AnalysisData.of(request.messages().getLast().content()));
            while (matcher.find()) {
                names.add(matcher.group(1).strip());
            }
            asked.add(List.copyOf(names));
            final String items =
                    names.stream().map(this::item).filter(Objects::nonNull).collect(Collectors.joining(","));
            return Result.ok(new ChatResponse("{\"items\":[" + items + "]}", FinishReason.STOP));
        }

        private @Nullable String item(final String name) {
            final boolean first =
                    asked.stream().filter(batch -> batch.contains(name)).count() == 1;
            final String echoed = name.equals(misspelledOnce) && first ? misspelling : name;
            final String answer = answers.get(name);
            return answer == null ? null : "{\"term\":\"%s\",%s}".formatted(echoed, answer);
        }

        @Override
        public void announce(final JobEvent event) {
            // the batches are counted from the calls
        }
    }

    private static GlossaryEntry character(final String term) {
        return GlossaryEntry.of("p:" + term, "p", term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static GlossaryEntry other(final String term) {
        return GlossaryEntry.of("p:" + term, "p", term, null, TermType.OTHER, Gender.UNKNOWN, false);
    }

    private static String person(final String gender, final double confidence) {
        return "\"gender\":\"%s\",\"category\":\"person\",\"confidence\":%s".formatted(gender, confidence);
    }

    private static NameKnowledge answerFor(final Map<String, NameKnowledge> answers, final String id) {
        return Objects.requireNonNull(answers.get(id), id);
    }

    private Map<String, NameKnowledge> asked(final List<GlossaryEntry> entries, final BareNameModel model) {
        return Objects.requireNonNull(knowledge.ask(entries, FRAME, model).data(), "answers");
    }

    @Test
    void keep_itemThatEchoesTheTerm() {
        final BareNameModel model = new BareNameModel(
                Map.of(
                        "Ivar", person("male", 0.8),
                        "Mira Holt", person("female", 0.9),
                        "Danube", "\"gender\":\"male\",\"category\":\"river\",\"confidence\":0.95"),
                null,
                null);
        final List<GlossaryEntry> entries = List.of(character("Ivar"), other("Mira Holt"), other("Danube"));

        final Map<String, NameKnowledge> answers = asked(entries, model);

        assertThat(entries.stream().map(entry -> NameKnowledgeReplies.applyTo(entry, answerFor(answers, entry.id()))))
                .extracting(GlossaryEntry::term, GlossaryEntry::type, GlossaryEntry::placeKind, GlossaryEntry::gender)
                .containsExactly(
                        tuple("Ivar", TermType.CHARACTER, null, Gender.MALE),
                        tuple("Mira Holt", TermType.CHARACTER, null, Gender.FEMALE),
                        tuple("Danube", TermType.PLACE, PlaceKind.RIVER, Gender.UNKNOWN));
        assertThat(NameKnowledgeReplies.applyTo(character("Ivar"), answerFor(answers, "p:Ivar")))
                .extracting(GlossaryEntry::genderOrigin, GlossaryEntry::knowledge)
                .containsExactly(Origin.LIKELY, new NameKnowledge(Gender.MALE, TermType.CHARACTER, null, 0.8));
        assertThat(model.asked).containsExactly(List.of("Ivar", "Mira Holt", "Danube"));
    }

    @Test
    void prompt_offersMaleFemaleNeuterAndNoUnknown() {
        final String task = new PromptTemplates()
                .renderTask(ua.bookloom.pipeline.prompt.PromptName.NAME_KNOWLEDGE, FRAME, Map.of("names", "- Aldo"));

        assertThat(task).contains("male", "female", "neuter").doesNotContain("unknown");
    }

    @Test
    void drop_itemWithAnotherTerm() {
        final String reply = "{\"items\":[{\"term\":\"Tobias\",\"gender\":\"male\",\"category\":\"person\","
                + "\"confidence\":0.9}]}";

        final Map<String, NameKnowledge> read = NameKnowledgeReplies.read(mapper, reply, List.of(character("Tobin")));

        assertThat(read).isEmpty();
    }

    @Test
    void lowConfidence_isMarkedLow() {
        final String reply = "{\"items\":[{\"term\":\"Sasha\",\"gender\":\"female\",\"category\":\"person\","
                + "\"confidence\":0.55}]}";

        final Map<String, NameKnowledge> read = NameKnowledgeReplies.read(mapper, reply, List.of(character("Sasha")));
        final GlossaryEntry sasha = NameKnowledgeReplies.applyTo(character("Sasha"), answerFor(read, "p:Sasha"));

        assertThat(sasha.gender()).isEqualTo(Gender.FEMALE);
        assertThat(sasha.genderOrigin()).isEqualTo(Origin.LIKELY);
        assertThat(answerFor(read, "p:Sasha").isLowConfidenceFor(sasha.gender(), sasha.genderOrigin()))
                .isTrue();
    }

    @Test
    void missingItem_isAskedOnceMore() {
        final BareNameModel model = new BareNameModel(
                Map.of("Tobin", person("male", 0.85), "Ivar", person("male", 0.8)), "Tobin", "Tobias");
        final List<GlossaryEntry> entries = List.of(character("Tobin"), character("Ivar"), character("Quillon"));

        final Map<String, NameKnowledge> answers = asked(entries, model);

        assertThat(model.asked).containsExactly(List.of("Tobin", "Ivar", "Quillon"), List.of("Tobin", "Quillon"));
        assertThat(answers).containsOnlyKeys("p:Tobin", "p:Ivar");
        assertThat(answerFor(answers, "p:Tobin").gender()).isEqualTo(Gender.MALE);
    }

    @Test
    void ask_95names_makesThreeCallsOf40And40And15() {
        final List<GlossaryEntry> entries = IntStream.rangeClosed(1, 95)
                .mapToObj(number -> character("Name" + (char) ('A' + number / 26) + (char) ('a' + number % 26)))
                .toList();
        final Map<String, String> answers =
                entries.stream().collect(Collectors.toMap(GlossaryEntry::term, entry -> person("female", 0.9)));
        final BareNameModel model = new BareNameModel(answers, null, null);

        asked(entries, model);

        assertThat(model.asked).extracting(List::size).containsExactly(40, 40, 15);
    }

    @Test
    void apply_higherOrigins_areNotReplacedAndARuleDisagreementIsRecorded() {
        final GlossaryEntry wren = character("Wren").withGender(Gender.FEMALE);
        final GlossaryEntry mara = character("Mara Quill").withGenderFrom(Gender.FEMALE, Origin.BOOK);
        final GlossaryEntry ivar = character("Ivar").withGenderFrom(Gender.FEMALE, Origin.RULE);
        final NameKnowledge male = new NameKnowledge(Gender.MALE, TermType.CHARACTER, null, 0.9);

        assertThat(List.of(wren, mara, ivar).stream().map(entry -> NameKnowledgeReplies.applyTo(entry, male)))
                .extracting(GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple(Gender.FEMALE, Origin.USER),
                        tuple(Gender.FEMALE, Origin.BOOK),
                        tuple(Gender.FEMALE, Origin.RULE));
        final GlossaryEntry ruled = NameKnowledgeReplies.applyTo(ivar, male);
        assertThat(ruled.knowledge()).isEqualTo(male);
        assertThat(male.disagreesWithRule(ruled.gender(), ruled.genderOrigin())).isTrue();
    }

    // A bare-name guess of neuter is no person's gender: a character keeps the gender it held (the hand check's
    // Newton, held male from one reading, and Cavendish, held none, both came out "neuter (likely)").
    @Test
    void apply_neuterGuessForCharacter_genderNotTaken() {
        final GlossaryEntry held = character("Newton").withGenderFrom(Gender.MALE, Origin.LIKELY);
        final GlossaryEntry none = other("Cavendish");
        final NameKnowledge neuter = new NameKnowledge(Gender.NEUTER, TermType.CHARACTER, null, 0.8);

        assertThat(List.of(held, none).stream().map(entry -> NameKnowledgeReplies.applyTo(entry, neuter)))
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple(TermType.CHARACTER, Gender.MALE, Origin.LIKELY),
                        tuple(TermType.CHARACTER, Gender.UNKNOWN, none.genderOrigin()));
    }

    @Test
    void asked_settledCharactersAndKnownKinds_areNotAsked() {
        final List<GlossaryEntry> held = List.of(
                character("Wren").withGender(Gender.FEMALE),
                character("Mara Quill").withGenderFrom(Gender.FEMALE, Origin.BOOK),
                character("Ivar").withGenderFrom(Gender.MALE, Origin.RULE),
                character("Sasha"),
                other("Danube"),
                GlossaryEntry.of("p:Vell", "p", "Vell", null, TermType.PLACE, Gender.UNKNOWN, false));

        assertThat(NameKnowledgeCalls.askedOf(held))
                .extracting(GlossaryEntry::term)
                .containsExactly("Ivar", "Sasha", "Danube");
    }
}
