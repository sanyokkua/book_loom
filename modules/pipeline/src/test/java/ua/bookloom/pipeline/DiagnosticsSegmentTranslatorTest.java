package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;

/** Verifies diagnostics from the per-segment decision boundary. */
class DiagnosticsSegmentTranslatorTest {

    private static final Path TEST_LOG = Path.of("build/test-logs/test.log");
    private static final String SYSTEM_PROMPT = "Translate the following text from en into uk. Preserve every "
            + "⟦gN⟧ placeholder exactly as written. Return only the translated text.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // DEBUG identifies every branch, WARN exposes token sets, and a thrown call logs its cause exactly once.
    @Test
    void translate_decisionBranches_logsDebugWarningAndSingleCauseError() {
        final Segment segment = markdownSegment();
        final long offset = testLogSize();

        translator(response("HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.")).translate(segment);
        translator(response("HE OPENED THE ⟦g0⟧OLD DOOR.")).translate(segment);
        translator(new ScriptedChatModel().throwFailure(new IllegalStateException("diagnostic model explosion")))
                .translate(segment);

        final String log = testLogSince(offset);
        assertThat(log)
                .contains("Translating segment id=" + segment.id() + " format=MARKDOWN targetLanguage=uk")
                .contains("Building system prompt targetLanguage=uk sourceLanguagePresent=true")
                .contains("System prompt sourceBranch=known sourceLanguage=en")
                .contains("Building chat request segmentId=" + segment.id() + " maskedLength=31")
                .contains("Built chat request segmentId=" + segment.id() + " messageCount=2")
                .contains("Calling chat model segmentId=" + segment.id() + " messageCount=2")
                .contains("Chat model completed segmentId=" + segment.id() + " result=success")
                .contains("Model reply segment=" + segment.id() + " kind=text finish=STOP empty=false")
                .contains("Restoring segment id=" + segment.id() + " format=MARKDOWN trimmedLength=31")
                .contains("Restoring whitespace sourceLength=31 trimmedLength=31")
                .contains("Restored whitespace leadingLength=0 trailingLength=0 restoredLength=31")
                .contains("Segment decision id=" + segment.id() + " decision=ACCEPTED errorCode=null")
                .contains("Flagged segment id=" + segment.id() + " code=validation")
                .contains("Collecting expected tokens segmentId=" + segment.id() + " placeholderCount=2")
                .contains("Collected observed tokens textLength=27 tokenCount=1")
                .contains("expectedTokens=[⟦g0⟧, ⟦g1⟧] observedTokens=[⟦g0⟧]")
                .contains("IllegalStateException: diagnostic model explosion");
        assertThat(count(log, "Unexpected model failure segment=" + segment.id() + " code=internal"))
                .isEqualTo(1);
    }

    // At TRACE the exact prompt, raw reply, whitespace-restored value and unmask input/output are diagnosable; the test
    // raises the level itself, so the gate, which runs at DEBUG, still proves book text stays at TRACE.
    @Test
    void translate_traceEnabled_logsBookTextOnlyAtTrace() {
        final String source = "  Sensitive manuscript sentence.  ";
        final String reply = "SENSITIVE MANUSCRIPT SENTENCE.";
        final Segment segment = txtSegment(source);
        final long offset = testLogSize();

        atTraceLevel(
                () -> new SegmentTranslator(documents, response(reply), BookFormat.TXT, "uk", "en").translate(segment));

        final String log = testLogSince(offset);
        assertThat(log)
                .contains("TRACE", "Segment prompt system=" + SYSTEM_PROMPT, "user=" + source)
                .contains("Segment reply raw=" + reply, "trimmed=" + reply)
                .contains("Segment reply restored=  SENSITIVE MANUSCRIPT SENTENCE.  ")
                .contains("Segment unmask input=  SENSITIVE MANUSCRIPT SENTENCE.  ");
        assertSensitiveTextOnlyAtTrace(log, "Sensitive manuscript sentence.", "SENSITIVE MANUSCRIPT SENTENCE.");
    }

    /** Raises the {@code ua.bookloom} loggers to TRACE for one action, then restores the configured level. */
    private static void atTraceLevel(final Runnable action) {
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        final Level configured = bookloom.getLevel();
        bookloom.setLevel(Level.TRACE);
        try {
            action.run();
        } finally {
            bookloom.setLevel(configured);
        }
    }

    private Segment markdownSegment() {
        return onlySegment(TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door."));
    }

    private Segment txtSegment(final String content) {
        final Segment parsed = onlySegment(TestBooks.txt(tempDir.resolve("Book.txt"), content));
        return new Segment(
                parsed.id(),
                parsed.unit(),
                parsed.order(),
                parsed.kind(),
                content,
                content,
                parsed.placeholders(),
                parsed.sourceHash(),
                parsed.prevKey(),
                parsed.nextKey(),
                parsed.anchor(),
                parsed.targetInner(),
                parsed.status(),
                parsed.confidence());
    }

    private Segment onlySegment(final Path path) {
        final Document document = Objects.requireNonNull(documents.open(path).data(), "opened document");
        return document.units().getFirst().segments().getFirst();
    }

    private SegmentTranslator translator(final ScriptedChatModel model) {
        return new SegmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en");
    }

    private static ScriptedChatModel response(final String content) {
        return new ScriptedChatModel().answer(Result.ok(new ChatResponse(content, FinishReason.STOP)));
    }

    private static long testLogSize() {
        try {
            return Files.exists(TEST_LOG) ? Files.size(TEST_LOG) : 0L;
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static String testLogSince(final long offset) {
        try {
            final byte[] log = Files.readAllBytes(TEST_LOG);
            final int start = Math.toIntExact(Math.min(offset, log.length));
            return new String(Arrays.copyOfRange(log, start, log.length), StandardCharsets.UTF_8);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    private static int count(final String text, final String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static void assertSensitiveTextOnlyAtTrace(
            final String log, final String sourceText, final String replyText) {
        assertThat(log.lines().filter(line -> line.contains(sourceText) || line.contains(replyText)))
                .isNotEmpty()
                .allMatch(line -> line.contains(" TRACE "));
    }
}
