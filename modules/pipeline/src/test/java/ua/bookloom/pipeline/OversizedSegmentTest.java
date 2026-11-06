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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.context.WindowPair;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;

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
        TranslationJobTestSupport.shutdownAll();
    }

    // Six sentence-aligned pieces at the small tier's current part of 600 tokens, all echoed: the English paragraph is
    // a blocking text finding, so no reviewer is asked about it; the round repairs by drafting each piece again, naming
    // the echo finding in the piece's own request, and never sends the whole oversized segment to a directed fix.
    @Test
    void run_oversizedBalancedSegmentFailingEcho_redraftsEveryPieceWithFindings() {
        final String text = IntStream.range(100, 500)
                .mapToObj(number -> "The night number " + number + " was calm.")
                .collect(Collectors.joining(" "));
        final TestProject project = TranslationJobTestSupport.project(
                TestBooks.markdown(tempDir.resolve("Book.md"), text),
                TranslationJobTestSupport.brief("en", "uk", QualityDial.BALANCED));
        final RedraftingModel model = new RedraftingModel();

        TranslationJobTestSupport.report(
                TranslationJobTestSupport.job(project, model).run());

        assertThat(model.formats()).hasSize(12).containsOnly("draft");
        assertThat(model.userMessages().subList(0, 6))
                .allSatisfy(user -> assertThat(user).doesNotContain("[Extra instruction]"));
        assertThat(model.userMessages().subList(6, 12))
                .allSatisfy(user -> assertThat(user)
                        .contains("[Extra instruction]\n")
                        .contains("echo similarity 1.0 at or above 0.9\n\nTranslate every item"));
        assertThat(TranslationJobTestSupport.stored(project, "Book.md:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path)
                .containsExactly(ua.bookloom.api.document.SegmentStatus.ACCEPTED, SegmentPath.REPAIRED);
    }

    // A paragraph of about 2,000 tokens is above either tier's current part, so a run cuts it at sentence ends into
    // pieces that each fit the tier's budget (600 small, 1,000 large) and decides it once over the joined target.
    @ParameterizedTest
    @CsvSource({"SMALL, 600", "LARGE, 1000"})
    void run_twoThousandTokenParagraph_isDraftedInPiecesWithinTheTiersCurrentPart(
            final ModelProfile.Tier tier, final int currentPart) {
        final String text = IntStream.range(100, 330)
                .mapToObj(number -> "The night number " + number + " was calm.")
                .collect(Collectors.joining(" "));
        final TestProject project = TranslationJobTestSupport.project(
                TestBooks.markdown(tempDir.resolve("Book.md"), text),
                TranslationJobTestSupport.brief("en", "uk", QualityDial.BALANCED));
        final NightModel model = new NightModel(tiered(tier));

        TranslationJobTestSupport.report(
                TranslationJobTestSupport.job(project, model).run());

        assertThat(TokenEstimator.estimate(text, "en")).isBetween(1900, 2100);
        assertThat(model.draftBodies())
                .hasSizeGreaterThan(1)
                .allSatisfy(
                        body -> assertThat(TokenEstimator.estimate(body, "en")).isLessThanOrEqualTo(currentPart));
        assertThat(String.join("", model.draftBodies())).isEqualTo(text);
        assertThat(TranslationJobTestSupport.stored(project, "Book.md:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path)
                .containsExactly(ua.bookloom.api.document.SegmentStatus.ACCEPTED, SegmentPath.DRAFT);
    }

    private static ModelProfile tiered(final ModelProfile.Tier tier) {
        final ModelProfile base = ModelProfile.defaults();
        return new ModelProfile(
                base.reasoning(),
                base.markupFilters(),
                base.systemRole(),
                base.quirks(),
                base.sampling(),
                base.thinking(),
                base.context(),
                base.templateOverhead(),
                base.batchSize(),
                tier);
    }

    @Test
    void translateSplit_400Sentences_draftsThreePiecesAndDecidesOnce() {
        final String text = SENTENCE.repeat(400).stripTrailing();
        final Segment segment = segments(text).getFirst();
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result =
                translator(model).translateSplit(segment, null, ProtectedMask.none(segment.masked()), splitter, BUDGET);

        assertThat(segment.masked()).hasSize(10_399);
        assertThat(TokenEstimator.estimate(segment.masked(), "en")).isEqualTo(2917);
        assertThat(model.textBodies())
                .extracting(body -> body.split("tonight\\.", -1).length - 1)
                .containsExactly(164, 164, 72);
        assertThat(model.textBodies())
                .allSatisfy(
                        body -> assertThat(TokenEstimator.estimate(body, "en")).isLessThanOrEqualTo(BUDGET));
        assertThat(String.join("", model.textBodies())).isEqualTo(segment.masked());
        assertThat(model.requests())
                .extracting(ChatRequest::expectedOutputTokens)
                .containsExactly(1810, 1810, 804);
        assertThat(DraftStepFixtures.drafted(result))
                .extracting(DraftOutcome.Drafted::restoredTarget, DraftOutcome.Drafted::gateFinding)
                .containsExactly(text.toUpperCase(Locale.ROOT), null);
    }

    @Test
    void translateSplit_400Sentences_isDraftedInPieces() {
        final Segment segment = segments(SENTENCE.repeat(400).stripTrailing()).getFirst();

        final DraftOutcome.Drafted drafted = DraftStepFixtures.drafted(translator(new UppercasingModel())
                .translateSplit(segment, null, ProtectedMask.none(segment.masked()), splitter, BUDGET));

        assertThat(drafted.inPieces()).isTrue();
    }

    @Test
    void translateSplit_withinTheBudget_isNotDraftedInPieces() {
        final Segment segment = segments("The sea was calm tonight.").getFirst();

        final DraftOutcome.Drafted drafted = DraftStepFixtures.drafted(translator(new UppercasingModel())
                .translateSplit(segment, null, ProtectedMask.none(segment.masked()), splitter, BUDGET));

        assertThat(drafted.inPieces()).isFalse();
    }

    @Test
    void translateSplit_pairHoldingASentenceBoundary_neverSeparatesTheTokens() {
        final String text = SENTENCE.repeat(200) + "*Ends here. Starts there.* "
                + SENTENCE.repeat(200).stripTrailing();
        final Segment segment = segments(text).getFirst();
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result =
                translator(model).translateSplit(segment, null, ProtectedMask.none(segment.masked()), splitter, BUDGET);

        assertThat(segment.masked()).contains("⟦g0⟧Ends here. Starts there.⟦g1⟧");
        assertThat(model.textBodies()).hasSizeGreaterThan(1);
        assertThat(model.textBodies())
                .allSatisfy(body -> assertThat(body.contains("⟦g0⟧")).isEqualTo(body.contains("⟦g1⟧")));
        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).contains("*ENDS HERE. STARTS THERE.*");
    }

    @Test
    void translateSplit_singleHugeSentence_isDraftedWholeWithoutPreviousPairsAndWarnsOnce() {
        final List<Segment> segments = segments("First short paragraph.\n\n" + "word ".repeat(1200));
        final Segment huge = segments.get(1);
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result = translator(model)
                .translateSplit(huge, previousPair(huge), ProtectedMask.none(huge.masked()), splitter, BUDGET);

        assertThat(huge.id()).isEqualTo("Book.md:1");
        assertThat(model.requests()).hasSize(1);
        assertThat(model.userMessages().getFirst()).doesNotContain("[Previous pairs", "Попередній абзац.");
        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
        assertThat(logEvents.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(message -> message.contains("Book.md:1") && message.contains("cannot be split"))
                .hasSize(1);
    }

    // The window a run would show the huge paragraph: the short one before it, accepted.
    private static TranslateContext previousPair(final Segment huge) {
        return new TranslateContext(
                new Window(
                        List.of(new WindowPair("Book.md:0", "First short paragraph.", "Попередній абзац.")),
                        TranslatePlanner.focusOf(huge.id(), huge.masked()),
                        null),
                BookMap.of(List.of(), List.of(), BookBrief.defaults("en").withLanguages("en", "uk")),
                List.of(),
                Map.of(),
                List.of());
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
        return DraftStepFixtures.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en");
    }

    /**
     * Echoes each piece of the first draft, translates it once the request carries an extra instruction, and accepts
     * whatever the reviewer is shown.
     */
    private static final class RedraftingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<s id=\"1\">(.*?)</s>", Pattern.DOTALL);
        private static final Pattern NIGHT = Pattern.compile("The night number (\\d+) was calm\\.");
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String format = format(request);
            final String content = "reviewer".equals(format) ? "{\"results\":[]}" : draftReply(request);
            return Result.ok(new ChatResponse(content, FinishReason.STOP));
        }

        List<String> formats() {
            return requests.stream().map(RedraftingModel::format).toList();
        }

        List<String> userMessages() {
            return requests.stream().map(RedraftingModel::user).toList();
        }

        private static String draftReply(final ChatRequest request) {
            final String user = user(request);
            final var matcher = TEXT.matcher(user);
            final String body = matcher.find() ? matcher.group(1) : "";
            final String target = user.contains("[Extra instruction]")
                    ? NIGHT.matcher(body).replaceAll("Ніч номер $1 була дуже тихою.")
                    : body;
            return TranslationJobTestSupport.targetReply(target);
        }

        private static String format(final ChatRequest request) {
            return ScriptedChatModel.callName(request);
        }

        private static String user(final ChatRequest request) {
            return request.messages().get(1).content();
        }
    }

    /** Translates every night sentence a draft shows it, accepts whatever the reviewer is shown, on a given profile. */
    private static final class NightModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<s id=\"1\">(.*?)</s>", Pattern.DOTALL);
        private static final Pattern NIGHT = Pattern.compile("The night number (\\d+) was calm\\.");
        private final List<ChatRequest> drafts = new ArrayList<>();
        private final ModelProfile profile;

        NightModel(final ModelProfile profile) {
            this.profile = profile;
        }

        @Override
        public ModelProfile profile() {
            return profile;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            if ("reviewer".equals(ScriptedChatModel.callName(request))) {
                return Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP));
            }
            drafts.add(request);
            final String body = bodyOf(request);
            return Result.ok(new ChatResponse(
                    TranslationJobTestSupport.targetReply(
                            NIGHT.matcher(body).replaceAll("Ніч номер $1 була дуже тихою.")),
                    FinishReason.STOP));
        }

        List<String> draftBodies() {
            return drafts.stream().map(NightModel::bodyOf).toList();
        }

        private static String bodyOf(final ChatRequest request) {
            final var matcher = TEXT.matcher(request.messages().get(1).content());
            return matcher.find() ? matcher.group(1) : "";
        }
    }

    /** Answers every draft with its own text body upper-cased, leaving the tokens untouched. */
    private static final class UppercasingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<s id=\"1\">(.*?)</s>", Pattern.DOTALL);
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
