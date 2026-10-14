package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.InputStream;
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
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SentenceSplitter;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.chunk.LongParagraphs;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * With the sentence-group arm on (15i.X3) a paragraph of more than 180 words is drafted as groups of at most 90 words,
 * each call showing the whole paragraph as plain text with no placeholder token, and the groups are joined back into
 * the one segment. With the arm off the paragraph is drafted whole, as it always was.
 */
class LongParagraphGroupsTest {

    private static final int BUDGET = 1200;
    private static final int GROUP_WORDS = 90;
    private static final int SENTENCE_WORDS = 10;
    private static final Pattern TOKEN = Pattern.compile("⟦g\\d+⟧");
    private static final String WHOLE_OPEN = "<WholeParagraph>\n";
    private static final String WHOLE_CLOSE = "\n</WholeParagraph>";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;
    private SentenceSplitter splitter;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(new DocumentModule());
        documents = injector.getInstance(DocumentPort.class);
        splitter = injector.getInstance(SentenceSplitter.class);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(LongParagraphs.PROPERTY_NAME);
    }

    @Test
    void translateSplit_switchOnParagraphOf187Words_draftsGroupsOfAtMost90WordsAndJoinsThem() throws IOException {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final Segment segment = segmentOf(paragraph());
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result = draft(model, segment);

        assertThat(model.bodies()).hasSizeBetween(3, 5);
        assertThat(model.bodies())
                .allSatisfy(body -> assertThat(body.strip().split("\\s+")).hasSizeLessThanOrEqualTo(GROUP_WORDS));
        assertThat(String.join("", model.bodies())).isEqualTo(segment.masked());
        final DraftOutcome.Drafted drafted = DraftStepFixtures.drafted(result);
        assertThat(drafted.restoredTarget()).isEqualTo(paragraph().toUpperCase(Locale.ROOT));
        assertThat(drafted.inPieces()).isTrue();
    }

    @Test
    void translateSplit_switchOn_everyGroupCallShowsTheWholeParagraph() throws IOException {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final UppercasingModel model = new UppercasingModel();

        draft(model, segmentOf(paragraph()));

        assertThat(model.userMessages())
                .hasSizeGreaterThan(2)
                .allSatisfy(user -> assertThat(user).contains(WHOLE_OPEN + paragraph() + WHOLE_CLOSE));
        assertThat(model.userMessages())
                .allSatisfy(user -> assertThat(user.indexOf("<WholeParagraph>")).isLessThan(user.indexOf("<Text>")));
    }

    @Test
    void translateSplit_switchOff_draftsTheParagraphWholeWithNoWholeParagraphBlock() throws IOException {
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result = draft(model, segmentOf(paragraph()));

        assertThat(model.userMessages()).hasSize(1);
        assertThat(model.userMessages().getFirst()).doesNotContain("WholeParagraph");
        assertThat(DraftStepFixtures.drafted(result).inPieces()).isFalse();
    }

    // The whole paragraph is shown as plain text: a token it showed could be copied into a group whose own tokens the
    // gate then counts, and a name behind a token is not written out in a context the model must not translate.
    @Test
    void translateSplit_paragraphWithMarkup_showsTheWholeParagraphWithoutTokensWhileTheGroupKeepsItsOwn()
            throws IOException {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final String text = paragraph().replace("the Adored Unknown", "*the Adored Unknown*");
        final Segment segment = segmentOf(text);
        final UppercasingModel model = new UppercasingModel();

        final Result<DraftOutcome> result = draft(model, segment);

        assertThat(segment.masked()).contains("⟦g0⟧the Adored Unknown⟦g1⟧");
        assertThat(model.wholeParagraphs())
                .hasSizeGreaterThan(2)
                .allSatisfy(whole -> assertThat(whole).doesNotContain("⟦").contains("the Adored Unknown lived"));
        assertThat(model.bodies().getFirst()).contains("⟦g0⟧the Adored Unknown⟦g1⟧");
        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).contains("*THE ADORED UNKNOWN*");
    }

    // More than 180 words, not 180 or more: the line is exclusive.
    @Test
    void translateSplit_switchOn_180WordsAreDraftedWholeAnd181InGroups() {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final UppercasingModel exactly = new UppercasingModel();
        final UppercasingModel beyond = new UppercasingModel();

        draft(exactly, segmentOf(sentences(180)));
        draft(beyond, segmentOf(sentences(181)));

        assertThat(exactly.bodies()).hasSize(1);
        assertThat(beyond.bodies()).hasSizeGreaterThan(1);
    }

    // One sentence of 200 words has no boundary to cut at: it is drafted whole, with no block that would repeat it.
    @Test
    void translateSplit_switchOnOneLongSentence_isDraftedWholeWithoutTheBlock() {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final UppercasingModel model = new UppercasingModel();

        draft(model, segmentOf("word ".repeat(199) + "end."));

        assertThat(model.userMessages()).hasSize(1);
        assertThat(model.userMessages().getFirst()).doesNotContain("WholeParagraph");
    }

    @Test
    void translateSplit_switchOnShortParagraph_isDraftedWholeWithoutTheBlock() {
        System.setProperty(LongParagraphs.PROPERTY_NAME, "groups");
        final UppercasingModel model = new UppercasingModel();

        draft(model, segmentOf("The sea was calm tonight. The wind was low."));

        assertThat(model.userMessages()).hasSize(1);
        assertThat(model.userMessages().getFirst()).doesNotContain("WholeParagraph");
    }

    private Result<DraftOutcome> draft(final ChatModel model, final Segment segment) {
        final SegmentTranslator translator =
                DraftStepFixtures.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en");
        return translator.translateSplit(
                segment, DraftContext.empty(), ProtectedMask.none(segment.masked()), splitter, BUDGET);
    }

    private Segment segmentOf(final String content) {
        return Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), content))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static String sentences(final int words) {
        final List<String> parts = new ArrayList<>();
        for (int done = 0; done < words; done += SENTENCE_WORDS) {
            parts.add(sentence(Math.min(SENTENCE_WORDS, words - done)));
        }
        return String.join(" ", parts);
    }

    private static String sentence(final int words) {
        return "Word" + " Word".repeat(words - 1) + ".";
    }

    private static String paragraph() throws IOException {
        try (InputStream in = LongParagraphGroupsTest.class.getResourceAsStream("/eval/long-paragraphs.json")) {
            final JsonNode first =
                    new ObjectMapper().readTree(Objects.requireNonNull(in)).get(0);
            return first.get("text").asText();
        }
    }

    /** Answers every draft with its own text body upper-cased, tokens untouched, and records what it was shown. */
    private static final class UppercasingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<Text>\n(.*)\n</Text>", Pattern.DOTALL);
        private static final Pattern WHOLE =
                Pattern.compile("<WholeParagraph>\n(.*)\n</WholeParagraph>", Pattern.DOTALL);
        private final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final String reply = TranslationJobTestSupport.targetReply(upperCase(bodyOf(userMessage(request))));
            return Result.ok(new ChatResponse(reply, FinishReason.STOP));
        }

        List<String> userMessages() {
            return requests.stream().map(UppercasingModel::userMessage).toList();
        }

        List<String> bodies() {
            return userMessages().stream().map(UppercasingModel::bodyOf).toList();
        }

        List<String> wholeParagraphs() {
            return userMessages().stream()
                    .map(WHOLE::matcher)
                    .filter(java.util.regex.Matcher::find)
                    .map(matcher -> matcher.group(1))
                    .toList();
        }

        private static String userMessage(final ChatRequest request) {
            return request.messages().get(1).content();
        }

        private static String bodyOf(final String user) {
            final var matcher = TEXT.matcher(user);
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
