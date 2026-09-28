package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A segment above the chunk budget is drafted in sentence-aligned pieces and decided once. */
class OversizedSegmentTest {

    private static final int BUDGET = 1200;
    private static final String SENTENCE = "The sea was calm tonight. ";
    private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");

    @TempDir
    private Path tempDir;

    private DocumentPort documents;
    private SentenceSplitter splitter;
    private ListAppender<ILoggingEvent> logEvents;
    private Logger pipelineLogger;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule());
        documents = injector.getInstance(DocumentPort.class);
        splitter = injector.getInstance(SentenceSplitter.class);
        logEvents = new ListAppender<>();
        logEvents.start();
        pipelineLogger = (Logger) LoggerFactory.getLogger("ua.bookloom.pipeline");
        pipelineLogger.addAppender(logEvents);
    }

    @AfterEach
    void tearDown() {
        pipelineLogger.detachAppender(logEvents);
    }

    @Test
    void translateSplit_400Sentences_draftsThreePiecesAndDecidesOnce() {
        final String text = SENTENCE.repeat(400).stripTrailing();
        final Segment segment = segments(text).getFirst();
        final UppercasingModel model = new UppercasingModel();

        final Result<Decision> result =
                translator(model).translateSplit(segment, DraftContext.empty(), splitter, BUDGET);

        assertThat(segment.masked()).hasSize(10_399);
        assertThat(TokenEstimator.estimate(segment.masked(), "en")).isEqualTo(2990);
        assertThat(model.textBodies())
                .extracting(body -> body.split("tonight\\.", -1).length - 1)
                .containsExactly(160, 160, 80);
        assertThat(model.textBodies())
                .allSatisfy(
                        body -> assertThat(TokenEstimator.estimate(body, "en")).isLessThanOrEqualTo(BUDGET));
        assertThat(String.join("", model.textBodies())).isEqualTo(segment.masked());
        assertThat(model.requests())
                .extracting(ChatRequest::expectedOutputTokens)
                .containsExactly(2870, 2870, 1435);
        final Decision decision = Objects.requireNonNull(result.data());
        assertThat(decision.segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision.segment().targetInner()).isEqualTo(text.toUpperCase(Locale.ROOT));
        assertThat(decision.flagReason()).isNull();
    }

    @Test
    void translateSplit_pairHoldingASentenceBoundary_neverSeparatesTheTokens() {
        final String text = SENTENCE.repeat(200) + "*Ends here. Starts there.* "
                + SENTENCE.repeat(200).stripTrailing();
        final Segment segment = segments(text).getFirst();
        final UppercasingModel model = new UppercasingModel();

        final Result<Decision> result =
                translator(model).translateSplit(segment, DraftContext.empty(), splitter, BUDGET);

        assertThat(segment.masked()).contains("⟦g0⟧Ends here. Starts there.⟦g1⟧");
        assertThat(model.textBodies()).hasSizeGreaterThan(1);
        assertThat(model.textBodies())
                .allSatisfy(body -> assertThat(body.contains("⟦g0⟧")).isEqualTo(body.contains("⟦g1⟧")));
        final Decision decision = Objects.requireNonNull(result.data());
        assertThat(decision.segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision.segment().targetInner()).contains("*ENDS HERE. STARTS THERE.*");
    }

    @Test
    void translateSplit_singleHugeSentence_isDraftedWholeWithoutPrecedingTargetsAndWarnsOnce() {
        final List<Segment> segments = segments("First short paragraph.\n\n" + "word ".repeat(1200));
        final Segment huge = segments.get(1);
        final UppercasingModel model = new UppercasingModel();

        final Result<Decision> result = translator(model)
                .translateSplit(huge, new DraftContext(List.of("Попередній абзац.")), splitter, BUDGET);

        assertThat(huge.id()).isEqualTo("Book.md:1");
        assertThat(model.requests()).hasSize(1);
        assertThat(model.userMessages().getFirst()).doesNotContain("<PreviousTranslations>");
        assertThat(Objects.requireNonNull(result.data()).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(logEvents.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(message -> message.contains("Book.md:1"))
                .hasSize(1);
    }

    private List<Segment> segments(final String content) {
        return Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), content))
                        .data())
                .units()
                .getFirst()
                .segments();
    }

    private SegmentTranslator translator(final ChatModel model) {
        return TranslationJobTestSupport.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en");
    }

    /** Answers every draft with its own text body upper-cased, leaving the tokens untouched. */
    private static final class UppercasingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<Text>\n(.*)\n</Text>", Pattern.DOTALL);
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String reply = TranslationJobTestSupport.targetReply(upperCase(bodyOf(request)));
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        }

        List<ChatRequest> requests() {
            return List.copyOf(requests);
        }

        List<String> userMessages() {
            return requests.stream().map(UppercasingModel::userMessage).toList();
        }

        List<String> textBodies() {
            return requests.stream().map(UppercasingModel::bodyOf).toList();
        }

        private static String userMessage(final ChatRequest request) {
            final ChatMessage user = request.messages().get(1);
            return user.content();
        }

        private static String bodyOf(final ChatRequest request) {
            final var matcher = TEXT.matcher(userMessage(request));
            return matcher.find() ? matcher.group(1) : "";
        }

        private static String upperCase(final String body) {
            final var matcher = TOKEN.matcher(body);
            final StringBuilder out = new StringBuilder();
            int from = 0;
            while (matcher.find()) {
                out.append(body.substring(from, matcher.start()).toUpperCase(Locale.ROOT))
                        .append(matcher.group());
                from = matcher.end();
            }
            return out.append(body.substring(from).toUpperCase(Locale.ROOT)).toString();
        }
    }
}
