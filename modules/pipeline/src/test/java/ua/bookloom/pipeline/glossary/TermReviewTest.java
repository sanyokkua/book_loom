package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.GlossaryReviewReport;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TargetOrigin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The model's glossary review: what it is asked, what it may change, and that a failure changes nothing. */
class TermReviewTest {

    private static final SuggestTargets SUGGEST = new SuggestTargets(new PromptTemplates(), new ObjectMapper());
    private static final NamePolicy POLICY = NamePolicy.TRANSLITERATE;

    private static final String PROJECT = "p1";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<Segment> BOOK = GlossaryTestSegments.of(
            List.of("Well, Hale said.", "He knew it well.", "We met Hale today.", "Moreau smiled.", "We saw Milton."));

    private GlossaryRepository glossary;
    private TermReview review;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        review = new TermReview(new PromptTemplates(), new ObjectMapper(), glossary, SUGGEST);
    }

    @Test
    void review_verdicts_removeTheCommonWordFillTypeAndGenderAndLeaveThePersonsWork() {
        heldGlossary();
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(VERDICTS)).answer(reply(NO_SUGGESTIONS));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.data())
                .extracting(GlossaryReviewReport::removed, GlossaryReviewReport::updated)
                .containsExactly(1, 2);
        // No window shows Hale's or Moreau's gender by a pronoun, so the model's genders are suggestions only.
        assertThat(glossary.all(PROJECT).data())
                .containsExactly(
                        entry("p1:hale", "Hale", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                                .withSuggestedGender(Gender.MALE),
                        entry("p1:moreau", "Moreau", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                                .withSuggestedGender(Gender.FEMALE),
                        entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true),
                        entry("p1:baker", "Baker Street", "Бейкер-стріт", TermType.OTHER, Gender.UNKNOWN, false));
        assertThat(glossary.wasRemoved(PROJECT, "Well").data()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "'' | no quote at all",
                "'The well was deep' | a sentence the book does not hold",
                "'Well Hale' | words that are not one phrase of an example"
            })
    void review_notANameWithoutAQuotedExample_leavesTheEntryInTheGlossary(final String evidence, final String why) {
        glossary.add(entry("p1:well", "Well", null, TermType.OTHER, Gender.UNKNOWN, false));
        final String reply = "{\"verdicts\":[{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\","
                + "\"gender\":\"unknown\",\"evidence\":\"" + evidence + "\"}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(reply)).answer(reply(NO_SUGGESTIONS));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.data()).extracting(GlossaryReviewReport::removed).isEqualTo(0);
        assertThat(glossary.all(PROJECT).data()).extracting(GlossaryEntry::term).containsExactly("Well");
        assertThat(glossary.wasRemoved(PROJECT, "Well").data()).as(why).isFalse();
    }

    @Test
    void review_notANameQuotingAnExampleWithOtherCaseAndQuotes_removesTheEntry() {
        glossary.add(entry("p1:well", "Well", null, TermType.OTHER, Gender.UNKNOWN, false));
        final String reply = "{\"verdicts\":[{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\","
                + "\"gender\":\"unknown\",\"evidence\":\"“HE knew it well…”\"}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(reply)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(glossary.all(PROJECT).data()).isEmpty();
    }

    @Test
    void review_request_listsOnlyOpenTermsWithCountPronounsAndNumberedWindows() {
        heldGlossary();
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"verdicts\":[]}"));

        review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        final ChatRequest request = model.requests().getFirst();
        assertThat(blockLines(request))
                .containsExactly(
                        "- Well — 2× — pronouns: none",
                        "  [1] Well, Hale said.",
                        "  [2] He knew it well.",
                        "- Hale — 2× — pronouns: none",
                        "  [1] Well, Hale said.",
                        "  [2] We met Hale today.",
                        "- Moreau — 1× — pronouns: none",
                        "  [1] Moreau smiled.");
        assertThat(request.callKind()).isEqualTo(CallKind.REVIEW_TERMS);
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(2048);
    }

    @Test
    void review_entryLockedWhileTheModelThinks_isLeftAsThePersonSetIt() {
        heldGlossary();
        final GlossaryEntry locked = entry("p1:well", "Well", "Ну", TermType.OTHER, Gender.UNKNOWN, true);
        final ModelCalls lockingMeanwhile = (kind, segmentId, request) -> {
            glossary.update(locked);
            return reply(VERDICTS);
        };

        review.review(PROJECT, BOOK, FRAME, POLICY, lockingMeanwhile);

        assertThat(glossary.all(PROJECT).data()).contains(locked);
    }

    @Test
    void review_secondBatchFails_returnsThatErrorAndChangesNothing() {
        IntStream.range(0, TermReview.BATCH_SIZE + 1)
                .forEach(index ->
                        glossary.add(entry("p1:x" + index, "Xa" + index, null, TermType.OTHER, Gender.UNKNOWN, false)));
        final List<GlossaryEntry> before = glossary.all(PROJECT).data();
        final AppError failure = AppError.of(ErrorCode.unreachable, "Offline", "The provider is unreachable.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"verdicts\":[{\"term\":\"Xa0\",\"verdict\":\"not-a-name\",\"type\":\"other\","
                        + "\"gender\":\"unknown\"}]}"))
                .answer(Result.err(failure));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.error()).isEqualTo(failure);
        assertThat(model.requests()).hasSize(2);
        assertThat(glossary.all(PROJECT).data()).isEqualTo(before);
    }

    @Test
    void review_nothingOpen_makesNoCall() {
        glossary.add(entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true));
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.data()).extracting(GlossaryReviewReport::removed).isEqualTo(0);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void review_pseudoModel_changesNothing() {
        heldGlossary();
        final List<GlossaryEntry> before = glossary.all(PROJECT).data();
        final PseudoChatModel model = new PseudoChatModel(new ObjectMapper());

        final Result<GlossaryReviewReport> report =
                review.review(PROJECT, BOOK, FRAME, POLICY, (kind, segmentId, request) -> model.chat(request));

        assertThat(report.data()).extracting(GlossaryReviewReport::entries).isEqualTo(before);
    }

    @Test
    void review_suggestions_fillTheOpenTargetsAsSuggestionsAndCountThem() {
        heldGlossary();
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(VERDICTS)).answer(reply(SUGGESTIONS));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.data())
                .extracting(
                        GlossaryReviewReport::removed, GlossaryReviewReport::updated, GlossaryReviewReport::suggested)
                .containsExactly(1, 2, 2);
        assertThat(glossary.all(PROJECT).data())
                .containsExactly(
                        suggested("p1:hale", "Hale", "Гейл", TermType.CHARACTER, Gender.UNKNOWN)
                                .withSuggestedGender(Gender.MALE),
                        suggested("p1:moreau", "Moreau", "Моро", TermType.CHARACTER, Gender.UNKNOWN)
                                .withSuggestedGender(Gender.FEMALE),
                        entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true),
                        entry("p1:baker", "Baker Street", "Бейкер-стріт", TermType.OTHER, Gender.UNKNOWN, false));
    }

    @Test
    void review_suggestionRequest_listsTheSurvivorsWithTheirVerdictsTypeAndOneSentence() {
        heldGlossary();
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(VERDICTS)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        final ChatRequest request = model.requests().getLast();
        assertThat(request.callKind()).isEqualTo(CallKind.SUGGEST_TARGETS);
        assertThat(termLines(request))
                .containsExactly(
                        "- Hale — person, male — \"Well, Hale said.\"",
                        "- Moreau — person, female — \"Moreau smiled.\"");
    }

    @Test
    void review_personTypesATargetWhileTheModelSuggests_keepsThePersonsTarget() {
        heldGlossary();
        final GlossaryEntry typed = entry("p1:hale", "Hale", "Хейл", TermType.OTHER, Gender.UNKNOWN, false);

        review.review(PROJECT, BOOK, FRAME, POLICY, updatingWhileSuggesting(typed));

        assertThat(glossary.all(PROJECT).data())
                .contains(entry("p1:hale", "Hale", "Хейл", TermType.OTHER, Gender.UNKNOWN, false));
    }

    @Test
    void review_earlierSuggestion_isRefreshedAndAnswersAPersonsTargetNever() {
        glossary.add(suggested("p1:hale", "Hale", "Хейл", TermType.CHARACTER, Gender.MALE));
        glossary.add(entry("p1:moreau", "Moreau", "Мору", TermType.CHARACTER, Gender.FEMALE, false));
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"verdicts\":[]}")).answer(reply(SUGGESTIONS));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.data()).extracting(GlossaryReviewReport::suggested).isEqualTo(1);
        assertThat(glossary.all(PROJECT).data())
                .containsExactly(
                        suggested("p1:hale", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE),
                        entry("p1:moreau", "Moreau", "Мору", TermType.CHARACTER, Gender.FEMALE, false));
    }

    @Test
    void review_suggestionCallFails_returnsThatErrorAndChangesNothing() {
        heldGlossary();
        final List<GlossaryEntry> before = glossary.all(PROJECT).data();
        final AppError failure = AppError.of(ErrorCode.timeout, "Too slow", "The provider did not answer in time.");
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(VERDICTS)).answer(Result.err(failure));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(report.error()).isEqualTo(failure);
        assertThat(glossary.all(PROJECT).data()).isEqualTo(before);
    }

    @Test
    void review_placeNamedLikeAPerson_staysAPlaceAfterTheModelsVerdictAndSeeding() {
        glossary.add(entry("p1:vs", "Victoria Station", null, TermType.OTHER, Gender.UNKNOWN, false));
        glossary.add(entry("p1:fl", "Florence", null, TermType.OTHER, Gender.UNKNOWN, false));
        final String verdicts = "{\"verdicts\":["
                + "{\"term\":\"Victoria Station\",\"verdict\":\"name\",\"type\":\"place\",\"gender\":\"unknown\"},"
                + "{\"term\":\"Florence\",\"verdict\":\"name\",\"type\":\"place\",\"gender\":\"unknown\"}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdicts)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::type, GlossaryEntry::gender)
                .containsExactly(
                        tuple("Victoria Station", TermType.PLACE, Gender.UNKNOWN),
                        tuple("Florence", TermType.PLACE, Gender.UNKNOWN));
    }

    @Test
    void review_suggestedGender_staysSuggestedAndAConfirmedOneIsNeverOverwritten() {
        glossary.add(entry("p1:jo", "John", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withSuggestedGender(Gender.MALE));
        glossary.add(entry("p1:ma", "Mary", null, TermType.CHARACTER, Gender.UNKNOWN, false)
                .withGender(Gender.MALE));
        final String verdicts = "{\"verdicts\":["
                + "{\"term\":\"John\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"male\"},"
                + "{\"term\":\"Mary\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"female\"}]}";
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(verdicts)).answer(reply(NO_SUGGESTIONS));

        review.review(PROJECT, BOOK, FRAME, POLICY, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::isGenderSuggested)
                .containsExactly(tuple("John", Gender.MALE, true), tuple("Mary", Gender.MALE, false));
    }

    private static final String NO_SUGGESTIONS = "{\"suggestions\":[]}";

    private static final String SUGGESTIONS = "{\"suggestions\":["
            + "{\"term\":\"Hale\",\"target\":\"Гейл\",\"gender\":\"female\"},"
            + "{\"term\":\"Moreau\",\"target\":\"Моро\",\"gender\":\"male\"},"
            + "{\"term\":\"Milton\",\"target\":\"Мілтон-2\",\"gender\":\"male\"}]}";

    private static final String VERDICTS = "{\"verdicts\":["
            + "{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\","
            + "\"evidence\":\"He knew it well\"},"
            + "{\"term\":\"Hale\",\"verdict\":\"name\",\"type\":\"person\",\"gender\":\"male\"},"
            + "{\"term\":\"Moreau\",\"verdict\":\"name\",\"type\":\"place\",\"gender\":\"female\"},"
            + "{\"term\":\"Milton\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\"},"
            + "{\"term\":\"Baker Street\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\"}]}";

    private void heldGlossary() {
        glossary.add(entry("p1:well", "Well", null, TermType.OTHER, Gender.UNKNOWN, false));
        glossary.add(entry("p1:hale", "Hale", null, TermType.OTHER, Gender.UNKNOWN, false));
        glossary.add(entry("p1:moreau", "Moreau", null, TermType.CHARACTER, Gender.UNKNOWN, false));
        glossary.add(entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true));
        glossary.add(entry("p1:baker", "Baker Street", "Бейкер-стріт", TermType.OTHER, Gender.UNKNOWN, false));
    }

    private static GlossaryEntry entry(
            final String id,
            final String term,
            @Nullable final String target,
            final TermType type,
            final Gender gender,
            final boolean locked) {
        return new GlossaryEntry(id, PROJECT, term, target, type, gender, locked);
    }

    /** Answers the verdicts, then stores {@code edit} as the person would while the suggestion call is out. */
    private ModelCalls updatingWhileSuggesting(final GlossaryEntry edit) {
        return (kind, segmentId, request) -> switch (kind) {
            case SUGGEST_TARGETS -> {
                glossary.update(edit);
                yield reply(SUGGESTIONS);
            }
            default -> reply(VERDICTS);
        };
    }

    private static GlossaryEntry suggested(
            final String id, final String term, final String target, final TermType type, final Gender gender) {
        return new GlossaryEntry(id, PROJECT, term, target, type, gender, false, TargetOrigin.SUGGESTED);
    }

    private static List<String> blockLines(final ChatRequest request) {
        return request.messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .flatMap(String::lines)
                .filter(line -> line.startsWith("- ") || line.startsWith("  ["))
                .toList();
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
