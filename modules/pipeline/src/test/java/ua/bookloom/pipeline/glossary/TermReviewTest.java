package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        review = new TermReview(new PromptTemplates(), new ObjectMapper(), glossary);
    }

    @Test
    void review_verdicts_removeTheCommonWordFillTypeAndGenderAndLeaveThePersonsWork() {
        heldGlossary();
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(VERDICTS));

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, calls(model));

        assertThat(report.data())
                .extracting(GlossaryReviewReport::removed, GlossaryReviewReport::updated)
                .containsExactly(1, 2);
        assertThat(glossary.all(PROJECT).data())
                .containsExactly(
                        entry("p1:hale", "Hale", null, TermType.CHARACTER, Gender.MALE, false),
                        entry("p1:moreau", "Moreau", null, TermType.CHARACTER, Gender.FEMALE, false),
                        entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true),
                        entry("p1:baker", "Baker Street", "Бейкер-стріт", TermType.OTHER, Gender.UNKNOWN, false));
        assertThat(glossary.wasRemoved(PROJECT, "Well").data()).isTrue();
    }

    @Test
    void review_request_listsOnlyOpenTermsWithCountAndExamples() {
        heldGlossary();
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply("{\"verdicts\":[]}"));

        review.review(PROJECT, BOOK, FRAME, calls(model));

        final ChatRequest request = model.requests().getFirst();
        assertThat(termLines(request))
                .containsExactly(
                        "- Well — 2× — \"Well, Hale said.\" / \"He knew it well.\"",
                        "- Hale — 2× — \"Well, Hale said.\" / \"We met Hale today.\"",
                        "- Moreau — 1× — \"Moreau smiled.\"");
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

        review.review(PROJECT, BOOK, FRAME, lockingMeanwhile);

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

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, calls(model));

        assertThat(report.error()).isEqualTo(failure);
        assertThat(model.requests()).hasSize(2);
        assertThat(glossary.all(PROJECT).data()).isEqualTo(before);
    }

    @Test
    void review_nothingOpen_makesNoCall() {
        glossary.add(entry("p1:milton", "Milton", "Мілтон", TermType.OTHER, Gender.UNKNOWN, true));
        final ScriptedChatModel model = new ScriptedChatModel();

        final Result<GlossaryReviewReport> report = review.review(PROJECT, BOOK, FRAME, calls(model));

        assertThat(report.data()).extracting(GlossaryReviewReport::removed).isEqualTo(0);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void review_pseudoModel_changesNothing() {
        heldGlossary();
        final List<GlossaryEntry> before = glossary.all(PROJECT).data();
        final PseudoChatModel model = new PseudoChatModel(new ObjectMapper());

        final Result<GlossaryReviewReport> report =
                review.review(PROJECT, BOOK, FRAME, (kind, segmentId, request) -> model.chat(request));

        assertThat(report.data()).extracting(GlossaryReviewReport::entries).isEqualTo(before);
    }

    private static final String VERDICTS = "{\"verdicts\":["
            + "{\"term\":\"Well\",\"verdict\":\"not-a-name\",\"type\":\"other\",\"gender\":\"unknown\"},"
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
