package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.ModelCallFinished;
import ua.bookloom.api.pipeline.ModelCallStarted;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.JobModelCalls;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/**
 * The knowledge question through the model-call seam with a recording model (15i.B2): when no call is made, what the
 * request carries, and how each kind of reply or failure becomes a verdict. The same request is proven at the HTTP
 * seam for both dialects in {@code BookKnowledgeWireMockTest}.
 */
class BookKnowledgeTest {

    private static final String TITLE = "The Lantern Ledger";
    private static final String AUTHOR = "Orsolya Venn";
    private static final String STEM = "o_venn-the_lantern_ledger-2014";
    private static final BookFacts FACTS = new BookFacts(TITLE, AUTHOR, 2014, "Harbour Press", null);

    private static final String KNOWN_REPLY = """
            {"known":true,"title":"The Lantern Ledger","author":"Orsolya Venn","year":2013,\
            "genre":"fantasy novel","audience":"adult","era":"late twentieth century","narration":"third person",\
            "summary":"A clerk finds a ledger of unlit lanterns.","confidence":0.85}""";

    private static final String NOT_KNOWN_REPLY = """
            {"known":false,"title":"","author":"","year":0,"genre":"","audience":"","era":"","narration":"",\
            "summary":"","confidence":0.0}""";

    private final ScriptedChatModel model = new ScriptedChatModel();
    private final List<JobEvent> events = new ArrayList<>();
    private final BookKnowledge knowledge = new BookKnowledge(new PromptTemplates(), new ObjectMapper());
    private final CallFrame frame =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private JobModelCalls calls;

    @BeforeEach
    void connect() {
        calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return model;
                },
                events::add,
                Clock.systemUTC(),
                "uk");
    }

    private static Result<ChatResponse> reply(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    private Knowledge ask(final BookFacts facts, final String stem) {
        return java.util.Objects.requireNonNull(
                knowledge.ask(facts, stem, frame, calls).data());
    }

    private String userMessage() {
        return model.requests().getFirst().messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    // IF a book with no title were still asked about, THEN the model would answer about nothing and cost a call.
    @Test
    void ask_bookHasNoTitle_makesNoCallAndIsUnknownWithTheReason() {
        final Knowledge result = ask(new BookFacts(null, AUTHOR, 2014, null, null), STEM);

        assertThat(model.requests()).isEmpty();
        assertThat(events).isEmpty();
        assertThat(result.isKnown()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.NO_TITLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"The Lantern Ledger", "the_lantern_ledger", "THE-LANTERN-LEDGER", "lantern ledger"})
    void ask_titleIsTheFileStem_makesNoCallAndIsUnknownWithTheReason(final String stem) {
        final Knowledge result = ask(FACTS, stem);

        assertThat(model.requests()).isEmpty();
        assertThat(events).isEmpty();
        assertThat(result.reason()).isEqualTo(Reason.TITLE_IS_FILE_STEM);
    }

    // The request: one call of its own kind, deterministic, short, and held to the schema. The year the book states is
    // not sent, so the model's year is its own and the year check is not an echo of the prompt.
    @Test
    void ask_titledBook_sendsOneRequestOfItsKindAtTemperatureZeroWithTheLimitAndTheSchema() {
        model.answer(reply(KNOWN_REPLY));

        ask(FACTS, STEM);

        assertThat(model.requests()).hasSize(1);
        final ChatRequest request = model.requests().getFirst();
        assertThat(request.callKind()).isEqualTo(CallKind.BOOK_KNOWLEDGE);
        assertThat(request.temperature()).isEqualTo(0.0);
        assertThat(request.expectedOutputTokens()).isEqualTo(160);
        assertThat(request.maxOutputTokens()).isEqualTo(320);
        assertThat(Objects.requireNonNull(request.responseFormat()).name()).isEqualTo("book-knowledge");
        assertThat(Objects.requireNonNull(request.responseFormat()).jsonSchema())
                .contains("\"known\"", "\"title\"", "\"author\"", "\"year\"", "\"genre\"", "\"audience\"", "\"era\"")
                .contains("\"narration\"", "\"summary\"", "\"confidence\"")
                .contains("\"additionalProperties\":false");
        assertThat(request.reasoningEnabled()).isEqualTo(ReasoningLevel.OFF);
    }

    @Test
    void ask_titledBook_isAnnouncedAsABookKnowledgeCall() {
        model.answer(reply(KNOWN_REPLY));

        ask(FACTS, STEM);

        assertThat(events)
                .filteredOn(event -> event instanceof ModelCallStarted)
                .extracting(event -> ((ModelCallStarted) event).kind())
                .containsExactly(CallKind.BOOK_KNOWLEDGE);
        assertThat(events)
                .filteredOn(event -> event instanceof ModelCallFinished)
                .extracting(event -> ((ModelCallFinished) event).kind())
                .containsExactly(CallKind.BOOK_KNOWLEDGE);
    }

    @Test
    void ask_titleAndAuthor_areInTheUserMessageAndTheStatedYearAndPublisherAreNot() {
        model.answer(reply(KNOWN_REPLY));

        ask(FACTS, STEM);

        assertThat(userMessage()).contains(TITLE, AUTHOR).doesNotContain("2014", "Harbour Press");
    }

    @Test
    void ask_bookWithNoAuthor_sendsTheTitleAndNoAuthorLine() {
        model.answer(reply(KNOWN_REPLY));

        ask(new BookFacts(TITLE, null, null, null, null), STEM);

        assertThat(userMessage()).contains(TITLE).doesNotContain(AUTHOR, "null");
    }

    @Test
    void ask_matchingAnswer_isKnownWithEveryFieldOfTheAnswer() {
        model.answer(reply(KNOWN_REPLY));

        final Knowledge result = ask(FACTS, STEM);

        assertThat(result.isKnown()).isTrue();
        assertThat(result.reply())
                .isEqualTo(new BookKnowledgeReply(
                        true,
                        TITLE,
                        AUTHOR,
                        2013,
                        "fantasy novel",
                        "adult",
                        "late twentieth century",
                        "third person",
                        "A clerk finds a ledger of unlit lanterns.",
                        0.85));
    }

    // A reply is read leniently, as the other setup replies are: the object is taken from any prose around it.
    @Test
    void ask_replyWrappedInProse_isReadFromTheObject() {
        model.answer(reply("Here is my answer:\n" + KNOWN_REPLY + "\nHope that helps."));

        assertThat(ask(FACTS, STEM).isKnown()).isTrue();
    }

    // IF a model that does not know an invented book is believed, THEN the brief would carry a made-up genre.
    @Test
    void ask_modelDoesNotKnowTheBook_isUnknownNotKnown() {
        model.answer(reply(NOT_KNOWN_REPLY));

        final Knowledge result = ask(FACTS, STEM);

        assertThat(result.isKnown()).isFalse();
        assertThat(result.reply()).isNull();
        assertThat(result.reason()).isEqualTo(Reason.NOT_KNOWN);
    }

    @Test
    void ask_modelEchoesAnotherBook_isTitleMismatch() {
        model.answer(reply(KNOWN_REPLY.replace(TITLE, "The Copper Compass")));

        assertThat(ask(FACTS, STEM).reason()).isEqualTo(Reason.TITLE_MISMATCH);
    }

    @Test
    void ask_modelUnsure_isLowConfidence() {
        model.answer(reply(KNOWN_REPLY.replace("0.85", "0.69")));

        assertThat(ask(FACTS, STEM).reason()).isEqualTo(Reason.LOW_CONFIDENCE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"I know this book well.", "", "{\"known\":", "[]", "{\"title\":\"x\"}", "{\"known\":\"yes\"}"})
    void ask_replyThatIsNotTheObject_isUnparsable(final String content) {
        model.answer(reply(content));

        assertThat(ask(FACTS, STEM).reason()).isEqualTo(Reason.UNPARSABLE);
    }

    // A reply that is the object but misses fields the model could not fill is still an answer: it is judged, not lost.
    @Test
    void ask_knownReplyWithoutTheOptionalFields_isJudgedOnTheEcho() {
        model.answer(
                reply(
                        "{\"known\":true,\"title\":\"The Lantern Ledger\",\"author\":\"Orsolya Venn\",\"year\":2014,\"confidence\":0.9}"));

        final Knowledge result = ask(FACTS, STEM);

        assertThat(result.isKnown()).isTrue();
        assertThat(Objects.requireNonNull(result.reply()).genre()).isNull();
    }

    @Test
    void ask_callFails_isUnknownWithCallFailedAndRaisesNothing() {
        model.answer(Result.err(AppError.of(ErrorCode.unreachable, "Down", "The provider is not reachable.")));

        final Knowledge result = ask(FACTS, STEM);

        assertThat(result.isKnown()).isFalse();
        assertThat(result.reason()).isEqualTo(Reason.CALL_FAILED);
    }

    // IF a stop during the question were read as an unknown book, THEN the suggestion would go on to read windows.
    @Test
    void ask_callCancelled_isTheCancelledErrorNotAnUnknownBook() {
        model.answer(Result.err(AppError.of(ErrorCode.cancelled, "Stopped", "The call was stopped.")));

        final Result<Knowledge> result = knowledge.ask(FACTS, STEM, frame, calls);

        assertThat(result.isErr()).isTrue();
        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.cancelled);
    }

    @Test
    void judge_replyAlreadyGiven_isJudgedWithoutAnotherCall() {
        final Knowledge result = knowledge.judge(FACTS, STEM, KNOWN_REPLY);

        assertThat(result.isKnown()).isTrue();
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void judge_noReply_isUnparsable() {
        assertThat(knowledge.judge(FACTS, STEM, null).reason()).isEqualTo(Reason.UNPARSABLE);
    }
}
