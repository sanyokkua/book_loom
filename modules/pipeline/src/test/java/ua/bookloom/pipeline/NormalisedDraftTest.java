package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.typography.TypographyGate;

/** The checks and the reviewer read the typography-normalised reply, the text that is stored as the target. */
class NormalisedDraftTest {

    @TempDir
    private Path tempDir;

    @Test
    void translate_loneStraightQuote_draftsTheNormalisedMaskedReply() {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Path book = TestBooks.markdown(tempDir.resolve("book.md"), "“No, sir,” said the boy.");
        final Segment segment = Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
        final ProtectedMask mask = ProtectedMask.none(segment.masked());
        final GateFunction gate = TypographyGate.around(GateFunction.of(documents, BookFormat.MARKDOWN), "uk");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("\"Ні, сер, — відповів хлопчик.");

        final DraftOutcome.Drafted drafted = DraftStepFixtures.drafted(DraftStepFixtures.segmentTranslator(
                        gate, (kind, segmentId, request) -> model.chat(request), BookFormat.MARKDOWN, "uk", "en")
                .translate(segment, DraftContext.empty(), mask));

        assertThat(drafted.maskedReply()).isEqualTo(drafted.maskedForm()).startsWith("«");
    }
}
