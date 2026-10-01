package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
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
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The model pre-scan: batching, the reply's mapping, and what is merged into the glossary and what never is. */
class PreScanTest {

    private static final String PROJECT = "p1";
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final List<String> NAMES_BOOK =
            List.of("We met Moreau today.", "We met Acme today.", "We met Justine today.", "We met Milton today.");

    private GlossaryRepository glossary;
    private PreScan preScan;

    @BeforeEach
    void setUp() {
        glossary = Guice.createInjector(new DocumentModule(), new PersistenceModule())
                .getInstance(GlossaryRepository.class);
        preScan = new PreScan(new PromptTemplates(), new ObjectMapper(), glossary);
    }

    @Test
    void scan_modelProposesHeldAndNewTerm_keepsHeldEntryAndAddsUnlockedTargetlessNewOne() {
        final GlossaryEntry held =
                new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        glossary.add(held);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(
                        reply(
                                "{\"terms\":[{\"term\":\"hale\",\"type\":\"other\"},{\"term\":\"Milton\",\"type\":\"place\"}]}"));

        final Result<List<GlossaryEntry>> result =
                preScan.scan(PROJECT, book("We met Hale today.", "We met Milton today."), FRAME, calls(model));

        final GlossaryEntry milton =
                new GlossaryEntry("p1:milton", PROJECT, "Milton", null, TermType.PLACE, Gender.UNKNOWN, false);
        assertThat(result.data()).containsExactly(milton);
        assertThat(glossary.all(PROJECT).data()).containsExactlyInAnyOrder(held, milton);
    }

    @Test
    void scan_ninetyFiveCandidates_makesCallsOfFortyFortyAndFifteenAtTempPointTwo() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[]}"))
                .answer(reply("{\"terms\":[]}"))
                .answer(reply("{\"terms\":[]}"));

        final Result<List<GlossaryEntry>> result = preScan.scan(PROJECT, ninetyFiveNames(), FRAME, calls(model));

        assertThat(result.isOk()).isTrue();
        final List<ChatRequest> requests = model.requests();
        assertThat(requests).hasSize(3);
        assertThat(requests)
                .extracting(request -> candidateLines(request).size())
                .containsExactly(40, 40, 15);
        assertThat(candidateLines(requests.getFirst())).first().isEqualTo("Xaa — We met Xaa today.");
        assertThat(candidateLines(requests.get(2))).last().isEqualTo("Xdq — We met Xdq today.");
        assertThat(requests).extracting(ChatRequest::temperature).containsOnly(0.2);
        assertThat(requests).extracting(ChatRequest::expectedOutputTokens).containsOnlyNulls();
        assertThat(requests.getFirst().responseFormat()).isNotNull();
    }

    @Test
    void scan_secondOfThreeBatchesFails_returnsThatErrorAndWritesNothing() {
        final GlossaryEntry held =
                new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);
        glossary.add(held);
        final AppError failure = AppError.of(ErrorCode.unreachable, "Offline", "The provider is unreachable.");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[{\"term\":\"Xaa\",\"type\":\"person\"}]}"))
                .answer(Result.err(failure));

        final Result<List<GlossaryEntry>> result = preScan.scan(PROJECT, ninetyFiveNames(), FRAME, calls(model));

        assertThat(result.error()).isEqualTo(failure);
        assertThat(glossary.all(PROJECT).data()).containsExactly(held);
    }

    @Test
    void scan_modelThrows_returnsInternalErrorAndWritesNothing() {
        final ScriptedChatModel model = new ScriptedChatModel().throwFailure(new IllegalStateException("boom"));

        final Result<List<GlossaryEntry>> result = preScan.scan(PROJECT, book(NAMES_BOOK), FRAME, calls(model));

        assertThat(result.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
        assertThat(glossary.all(PROJECT).data()).isEmpty();
    }

    @Test
    void scan_termWithNoTypeOrGender_addsOtherUnknown() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"terms\":[{\"term\":\"Moreau\"}]}"));

        preScan.scan(PROJECT, book(NAMES_BOOK), FRAME, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .containsExactly(
                        new GlossaryEntry("p1:moreau", PROJECT, "Moreau", null, TermType.OTHER, Gender.UNKNOWN, false));
    }

    @ParameterizedTest
    @CsvSource({
        "Acme,org,feminine,OTHER,UNKNOWN",
        "Acme,other,,OTHER,UNKNOWN",
        "Justine,person,female,CHARACTER,FEMALE",
        "Justine,PERSON,Female,CHARACTER,FEMALE",
        "Milton,place,male,PLACE,MALE",
        "Moreau,term,neuter,TERM,NEUTER",
        "Moreau,creature,,OTHER,UNKNOWN"
    })
    void scan_replyTypeAndGender_mapToGlossaryTypeAndGender(
            final String term,
            final String type,
            final String gender,
            final TermType expectedType,
            final Gender expectedGender) {
        final String json = "{\"terms\":[{\"term\":\"" + term + "\",\"type\":\"" + type + "\",\"gender\":\""
                + (gender == null ? "" : gender) + "\"}]}";
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(json));

        preScan.scan(PROJECT, book(NAMES_BOOK), FRAME, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::type, GlossaryEntry::gender, GlossaryEntry::locked)
                .containsExactly(tuple(term, expectedType, expectedGender, false));
    }

    @Test
    void scan_personRemovedTheTerm_doesNotBringItBack() {
        glossary.add(new GlossaryEntry("e2", PROJECT, "Chapter", null, TermType.TERM, Gender.NEUTER, false));
        glossary.remove(PROJECT, "e2");
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(
                        reply(
                                "{\"terms\":[{\"term\":\"Chapter\",\"type\":\"term\"},{\"term\":\"Milton\",\"type\":\"place\"}]}"));

        final Result<List<GlossaryEntry>> result =
                preScan.scan(PROJECT, book("We read Chapter today.", "We read Milton today."), FRAME, calls(model));

        assertThat(result.data()).extracting(GlossaryEntry::term).containsExactly("Milton");
        assertThat(glossary.all(PROJECT).data()).extracting(GlossaryEntry::term).containsExactly("Milton");
    }

    @Test
    void scan_proposalOutsideItsBatchCandidates_isDropped() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply("{\"terms\":[{\"term\":\"Ghost\"},{\"term\":\"Moreau\"}]}"));

        preScan.scan(PROJECT, book("We met Moreau today."), FRAME, calls(model));

        assertThat(glossary.all(PROJECT).data()).extracting(GlossaryEntry::term).containsExactly("Moreau");
    }

    @Test
    void scan_candidateOfAnotherBatch_isDroppedFromThisBatchReply() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[{\"term\":\"Xdq\"}]}"))
                .answer(reply("{\"terms\":[]}"))
                .answer(reply("{\"terms\":[]}"));

        preScan.scan(PROJECT, ninetyFiveNames(), FRAME, calls(model));

        assertThat(glossary.all(PROJECT).data()).isEmpty();
    }

    @Test
    void scan_sameTermProposedTwiceInDifferentCase_addsOneEntryInTheBooksSpelling() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("{\"terms\":[{\"term\":\"milton\",\"type\":\"place\"},{\"term\":\"MILTON\"}]}"));

        preScan.scan(PROJECT, book("We met Milton today."), FRAME, calls(model));

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::type)
                .containsExactly(tuple("Milton", TermType.PLACE));
    }

    @ParameterizedTest
    @CsvSource({"not json", "'{\"terms\":\"none\"}'", "'[]'", "''"})
    void scan_unreadableReply_addsNothingAndReportsNoError(final String replyText) {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(replyText));

        final Result<List<GlossaryEntry>> result = preScan.scan(PROJECT, book(NAMES_BOOK), FRAME, calls(model));

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).isEmpty();
        assertThat(glossary.all(PROJECT).data()).isEmpty();
    }

    @Test
    void scan_realPseudoModel_addsOnlyTheBookNameNotTheTemplateWords() {
        final PseudoChatModel pseudo = new PseudoChatModel(new ObjectMapper());

        final Result<List<GlossaryEntry>> result = preScan.scan(
                PROJECT,
                book("We saw Hale at the door.", "We saw Hale at the door.", "We saw Hale at the door."),
                FRAME,
                (kind, segmentId, request) -> pseudo.chat(request));

        assertThat(result.data()).extracting(GlossaryEntry::term).containsExactly("Hale");
    }

    @Test
    void scan_glossaryHoldsTerms_requestListsThem() {
        glossary.add(new GlossaryEntry("e1", PROJECT, "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true));
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"terms\":[]}"));

        preScan.scan(PROJECT, book("We met Milton today."), FRAME, calls(model));

        assertThat(userMessage(model.requests().getFirst()))
                .contains("[Existing glossary terms")
                .contains("Hale");
    }

    @Test
    void scan_emptyGlossary_requestHasNoExistingTermsBlock() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"terms\":[]}"));

        preScan.scan(PROJECT, book("We met Milton today."), FRAME, calls(model));

        assertThat(userMessage(model.requests().getFirst())).doesNotContain("[Existing glossary terms");
    }

    @Test
    void scan_noCandidates_makesNoCallAndAddsNothing() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<List<GlossaryEntry>> result = preScan.scan(PROJECT, book("We saw the door."), FRAME, calls(model));

        assertThat(result.data()).isEmpty();
        assertThat(model.requests()).isEmpty();
    }

    private static List<String> candidateLines(final ChatRequest request) {
        return userMessage(request)
                .lines()
                .filter(line -> line.contains(" — We met "))
                .toList();
    }

    private static String userMessage(final ChatRequest request) {
        final List<ChatMessage> messages = request.messages();
        return messages.getLast().content();
    }

    private static List<Segment> ninetyFiveNames() {
        return GlossaryTestSegments.of(IntStream.range(0, 95)
                .mapToObj(index -> "We met X" + (char) ('a' + index / 26) + (char) ('a' + index % 26) + " today.")
                .toList());
    }

    private static List<Segment> book(final String... lines) {
        return GlossaryTestSegments.of(List.of(lines));
    }

    private static List<Segment> book(final List<String> lines) {
        return GlossaryTestSegments.of(lines);
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(Objects.requireNonNull(content), FinishReason.STOP));
    }
}
