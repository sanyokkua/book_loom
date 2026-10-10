package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * A character's type and gender decided from evidence across the book (15h.A2): a strong pronoun case needs no model, a
 * model's gender stands only on a window it cites that shows it, the calls are cut by a token budget, and the name
 * scan's first-sentence guess never overrides the review.
 */
class ReviewGenderEvidenceTest {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;
    private static final String PROJECT = "p1";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);

    private GlossaryRepository glossary;
    private TermReview review;
    private PreScan preScan;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        review = new TermReview(new PromptTemplates(), new ObjectMapper(), glossary, SUGGEST);
        preScan = new PreScan(new PromptTemplates(), new ObjectMapper(), glossary, review, SUGGEST);
    }

    // IF a character's clear pronoun case still went to the model, THEN a small model could overrule five "she"s.
    @Test
    void review_strongPronounCase_isDecidedWithoutAskingTheModel() {
        glossary.add(entry("p1:wren", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        final List<Segment> book = GlossaryTestSegments.of(List.of(
                "Wren sat. She ate.",
                "Wren ran. She fell.",
                "Wren woke. She yawned.",
                "Wren hid. She waited.",
                "Wren sang. She smiled."));
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, book, FRAME, POLICY, calls(model));

        assertThat(model.requests()).extracting(ChatRequest::callKind).containsExactly(CallKind.SUGGEST_TARGETS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple(TermType.CHARACTER, Gender.FEMALE, false));
    }

    // A gender stands when a cited window holds a pronoun of it outside speech; the Chrome shape cites Tiger's "he".
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Wren stood. She left. | female | 1 | FEMALE | false",
                "Wren stood. She left. | male | 1 | MALE | true",
                "Wren stood. She left. | female | 2 | FEMALE | true",
                "Wren nodded. \"He never pays,\" said Tiger. | male | 1 | MALE | true"
            })
    void review_modelGender_isDecidedOnlyWhenACitedWindowShowsIt(
            final String paragraph,
            final String gender,
            final int window,
            final Gender expected,
            final boolean suggestedOnly) {
        glossary.add(entry("p1:wren", "Wren", null, TermType.OTHER, Gender.UNKNOWN, false));
        final String verdict = "{\"verdicts\":[{\"term\":\"Wren\",\"verdict\":\"name\",\"type\":\"person\","
                + "\"gender\":\"" + gender + "\",\"windows\":[" + window + "],\"evidence\":\"\"}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdict)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, GlossaryTestSegments.of(List.of(paragraph)), FRAME, POLICY, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple(expected, suggestedOnly));
    }

    // IF a batch were forty names whatever their windows, THEN a book's long passages would overflow a small context.
    @Test
    void review_longWindows_areCutIntoBatchesByTheTokenBudget() {
        final String filler = "The long grey road ran on past the empty farms and the low stone walls, ".repeat(4);
        final List<String> paragraphs = IntStream.range(0, 20)
                .boxed()
                .flatMap(index -> IntStream.range(0, 4)
                        .mapToObj(use -> filler + "end. Xa" + index + " waited. " + filler + "end."))
                .toList();
        IntStream.range(0, 20)
                .forEach(index ->
                        glossary.add(entry("p1:x" + index, "Xa" + index, null, TermType.OTHER, Gender.UNKNOWN, false)));
        final List<ChatRequest> asked = new ArrayList<>();
        final ModelCalls recording = (kind, segmentId, request) -> {
            asked.add(request);
            return reply(kind == CallKind.REVIEW_TERMS ? "{\"verdicts\":[]}" : NO_SUGGESTIONS);
        };

        review.review(PROJECT, GlossaryTestSegments.of(paragraphs), FRAME, POLICY, recording);

        final List<ChatRequest> reviews = asked.stream()
                .filter(request -> request.callKind() == CallKind.REVIEW_TERMS)
                .toList();
        assertThat(reviews).hasSizeGreaterThan(1);
        assertThat(reviews.stream()
                        .mapToInt(request -> termLines(request).size())
                        .sum())
                .isEqualTo(20);
    }

    // IF the proposal's first-sentence guess won, THEN a speaker's "he" next to a woman's name would stand (15h.A2).
    @ParameterizedTest
    @CsvSource({"female,FEMALE,false", "unknown,MALE,true"})
    void scan_proposalGender_neverOverridesTheReview(
            final String reviewGender, final Gender expected, final boolean suggestedOnly) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[{\"term\":\"Justine\",\"type\":\"person\",\"gender\":\"male\"}]}"));
        final String verdict = "{\"verdicts\":[{\"term\":\"Justine\",\"verdict\":\"name\",\"type\":\"person\","
                + "\"gender\":\"" + reviewGender + "\",\"windows\":[1],\"evidence\":\"\"}]}";
        final ModelCalls calls = (kind, segmentId, request) -> switch (kind) {
            case REVIEW_TERMS -> reply(verdict);
            case SUGGEST_TARGETS -> reply("{\"suggestions\":[]}");
            default -> model.chat(request);
        };

        preScan.scan(
                PROJECT,
                GlossaryTestSegments.of(List.of("We met Justine today. She smiled at us.")),
                FRAME,
                POLICY,
                calls);

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple(expected, suggestedOnly));
    }

    // Five "she"s after Wren: a strong pronoun case.
    private static final List<Segment> STRONG_BOOK = GlossaryTestSegments.of(IntStream.range(0, 5)
            .mapToObj(index -> "Day " + index + ". Wren smiled. She left.")
            .toList());

    // IF a term or an untyped candidate were decided by its pronouns, THEN a ship called "she" became a character.
    @ParameterizedTest
    @EnumSource(
            value = TermType.class,
            names = {"TERM", "OTHER"})
    void review_strongPronounsOnATermOrUntypedEntry_stillAskTheModelAndForceNoCharacter(final TermType type) {
        // Seeding already tried (as after the scan), so only the review itself could retype the entry here.
        glossary.add(new GlossaryEntry(
                "p1:wren", PROJECT, "Wren", null, type, Gender.UNKNOWN, false, TargetOrigin.PERSON, false, true));
        final String verdicts = "{\"verdicts\":["
                + "{\"term\":\"Wren\",\"verdict\":\"name\",\"type\":\"term\",\"gender\":\"unknown\"}]}";
        final List<CallKind> kinds = new ArrayList<>();
        final ModelCalls calls = (kind, segmentId, request) -> {
            kinds.add(kind);
            return reply(kind == CallKind.REVIEW_TERMS ? verdicts : NO_SUGGESTIONS);
        };

        review.review(PROJECT, STRONG_BOOK, FRAME, POLICY, calls);

        assertThat(kinds).contains(CallKind.REVIEW_TERMS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::type, GlossaryEntry::gender)
                .containsExactly(tuple(TermType.TERM, Gender.UNKNOWN));
    }

    @Test
    void review_strongPronounsOnACharacter_decideTheGenderWithoutTheModel() {
        glossary.add(entry("p1:wren", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        final List<CallKind> kinds = new ArrayList<>();
        final ModelCalls calls = (kind, segmentId, request) -> {
            kinds.add(kind);
            return reply(NO_SUGGESTIONS);
        };

        review.review(PROJECT, STRONG_BOOK, FRAME, POLICY, calls);

        assertThat(kinds).doesNotContain(CallKind.REVIEW_TERMS);
        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple(Gender.FEMALE, false));
    }

    // IF a first-name seed outranked a verdict the book's own window proves, THEN Hale stayed a man.
    @Test
    void review_verifiedVerdict_replacesASuggestedGenderButNeverAPersonsOne() {
        glossary.add(entry("p1:hale", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE));
        glossary.add(entry("p1:wren", "Wren", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withGender(Gender.MALE));
        final List<Segment> book = GlossaryTestSegments.of(List.of("Hale smiled. She left.", "Wren smiled. She left."));
        final String verdicts = "{\"verdicts\":["
                + "{\"term\":\"Hale\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\","
                + "\"windows\":[1]},"
                + "{\"term\":\"Wren\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\","
                + "\"windows\":[1]}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdicts)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, book, FRAME, POLICY, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple("Hale", Gender.FEMALE, false), tuple("Wren", Gender.MALE, false));
    }

    private static final String NO_SUGGESTIONS = "{\"suggestions\":[]}";

    private static GlossaryEntry entry(
            final String id,
            final String term,
            @org.jspecify.annotations.Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        return new GlossaryEntry(id, PROJECT, term, target, type, gender, locked);
    }

    private static List<String> termLines(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .flatMap(String::lines)
                .filter(line -> line.startsWith("- "))
                .toList();
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }
}
