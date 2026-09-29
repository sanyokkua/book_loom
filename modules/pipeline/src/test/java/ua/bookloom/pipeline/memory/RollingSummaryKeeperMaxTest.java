package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.PROJECT;
import static ua.bookloom.pipeline.memory.RollingSummaryFixtures.decideAccepted;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.persistence.PersistenceModule;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The Max dial's model-written summary at a unit's end: what it sends, what it stores and how it fails. */
class RollingSummaryKeeperMaxTest {

    private static final String GOOD_REPLY =
            "{\"summary\":{\"source\":\"Hale arrives.\",\"target\":\"Гейл прибуває.\"},\"facts\":[]}";

    private SummaryRepository summaries;
    private ScriptedChatModel model;
    private RollingSummaryKeeper keeper;
    private ListAppender<ILoggingEvent> logEvents;
    private Logger keeperLogger;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new PersistenceModule());
        summaries = injector.getInstance(SummaryRepository.class);
        model = new ScriptedChatModel();
        final CallFrame frame =
                new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
        keeper = new RollingSummaryKeeper(
                summaries,
                injector.getInstance(GlossaryRepository.class),
                new PromptTemplates(),
                new ObjectMapper(),
                frame,
                DialParameters.of(QualityDial.MAX),
                (kind, segmentId, request) -> model.chat(request));
        logEvents = new ListAppender<>();
        logEvents.start();
        keeperLogger = (Logger) LoggerFactory.getLogger(RollingSummaryKeeper.class);
        keeperLogger.addAppender(logEvents);
    }

    @AfterEach
    void tearDown() {
        keeperLogger.detachAppender(logEvents);
    }

    @Test
    void onUnitEnd_maxChapterOf25_makesExactlyOneSummaryCallAndTheEveryTwentyRefreshMakesNone() {
        model.answer(ok(GOOD_REPLY));

        decideAccepted(keeper, "ch02.xhtml", 0, 25);
        assertThat(model.requests()).isEmpty();
        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch02.xhtml");

        assertThat(model.requests()).hasSize(1);
        assertThat(atEnd.data()).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(2);
            assertThat(summary.target()).isEqualTo("Гейл прибуває.");
            assertThat(summary.tokensSince()).isEqualTo(5);
            assertThat(summary.lastSummarizedKey()).isEqualTo("ch02.xhtml:24");
        });
    }

    @Test
    void onUnitEnd_maxChapterOfExactlyTwenty_stillMakesTheUnitEndCall() {
        model.answer(ok(GOOD_REPLY));

        decideAccepted(keeper, "ch02.xhtml", 0, 20);
        keeper.onUnitEnd("ch02.xhtml");

        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void onUnitEnd_max_sendsTheChapterSourceAndAcceptedTargetsAndThePreviousSummaryAtPointTwo() {
        model.answer(ok(GOOD_REPLY));
        decideAccepted(keeper, "ch02.xhtml", 0, 20);

        keeper.onUnitEnd("ch02.xhtml");

        final ChatRequest request = model.requests().getFirst();
        final String user = request.messages().getLast().content();
        assertThat(user).contains("Sentence 0.\nSentence 1.", "Речення 0.\nРечення 1.", "Sentence 19.");
        assertThat(user).doesNotContain("[Running summary so far]");
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.expectedOutputTokens()).isNull();
    }

    @Test
    void onUnitEnd_maxWithAPreviousSummary_sendsItInTheUserMessage() {
        summaries.save(new RollingSummary(PROJECT, "ch01.xhtml", "old facts", "старі факти", 1, "ch01.xhtml:1", 2));
        model.answer(ok(GOOD_REPLY));
        decideAccepted(keeper, "ch02.xhtml", 0, 3);

        keeper.onUnitEnd("ch02.xhtml");

        assertThat(model.requests().getFirst().messages().getLast().content()).contains("старі факти");
    }

    @Test
    void onDecided_maxEveryTwentyRefreshAfterAModelSummary_keepsTheModelsTarget() {
        model.answer(ok(GOOD_REPLY));
        decideAccepted(keeper, "ch01.xhtml", 0, 3);
        keeper.onUnitEnd("ch01.xhtml");

        decideAccepted(keeper, "ch02.xhtml", 0, 20);

        assertThat(summaries.latest(PROJECT).data()).hasValueSatisfying(summary -> {
            assertThat(summary.version()).isEqualTo(2);
            assertThat(summary.target()).isEqualTo("Гейл прибуває.");
        });
        assertThat(model.requests()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not json",
                "{\"summary\":{\"source\":\"\",\"target\":\"\"},\"facts\":[]}",
                "{\"summary\":{\"source\":\"a\",\"target\":\"   \"}}",
                "{\"facts\":[]}",
                "{\"summary\":\"Hale arrives.\"}",
                ""
            })
    void onUnitEnd_maxReplyUnreadable_keepsThePreviousVersionAndWarnsOnce(final String reply) {
        decideAccepted(keeper, "ch02.xhtml", 0, 20);
        model.answer(ok(reply));

        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch02.xhtml");

        assertThat(atEnd.isOk()).isTrue();
        assertThat(atEnd.data()).isEmpty();
        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.version()).isEqualTo(1));
        assertThat(logEvents.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .hasSize(1);
    }

    @Test
    void onUnitEnd_maxCallAnswersUnreachable_returnsThatErrorAndKeepsThePreviousVersion() {
        decideAccepted(keeper, "ch02.xhtml", 0, 20);
        final AppError failure = AppError.of(ErrorCode.unreachable, "Offline", "The provider is unreachable.");
        model.answer(Result.err(failure));

        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch02.xhtml");

        assertThat(atEnd.error()).isEqualTo(failure);
        assertThat(summaries.latest(PROJECT).data())
                .hasValueSatisfying(summary -> assertThat(summary.version()).isEqualTo(1));
        assertThat(logEvents.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .hasSize(1);
    }

    @Test
    void onUnitEnd_maxModelThrows_returnsInternalError() {
        decideAccepted(keeper, "ch02.xhtml", 0, 3);
        model.throwFailure(new IllegalStateException("boom"));

        final Result<Optional<RollingSummary>> atEnd = keeper.onUnitEnd("ch02.xhtml");

        assertThat(atEnd.error()).extracting(AppError::code).isEqualTo(ErrorCode.internal);
    }

    private static Result<ChatResponse> ok(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
