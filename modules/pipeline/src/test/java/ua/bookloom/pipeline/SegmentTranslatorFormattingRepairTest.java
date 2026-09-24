package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.document.DocumentModule;

/** Formatting-repair regressions whose real Markdown restoration path is the contract under test. */
class SegmentTranslatorFormattingRepairTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // IF a complete token sequence restores as an empty Markdown link, THEN one formatting repair can correct it.
    @Test
    void translate_detachedLinkPair_repairsAndAcceptsCorrectedTarget() {
        final Segment segment = segmentOf("See [chapter two](ch2.md) now.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "See translated chapter two ⟦g0⟧ ⟦g1⟧ now.", "See ⟦g0⟧translated chapter two⟦g1⟧ now.");

        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decisionOf(result).segment().targetInner()).isEqualTo("See [translated chapter two](ch2.md) now.");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("Paired placeholders must enclose nonblank translated text");
    }

    // IF a task marker is dropped, THEN the one formatting repair restores its original checked state.
    @Test
    void translate_droppedTaskMarker_repairsAndAcceptsCorrectedTarget() {
        final Segment segment = segmentOf("- [ ] Gravity is identical everywhere.");
        final ScriptedChatModel model =
                TranslationJobTestSupport.replies("Гравітація всюди однакова.", "⟦g0⟧Гравітація всюди однакова.");

        final Result<Decision> result = TranslationJobTestSupport.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(decisionOf(result).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decisionOf(result).segment().targetInner()).isEqualTo("[ ] Гравітація всюди однакова.");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("task-list marker placeholder begins <Text>, keep it first");
    }

    private Segment segmentOf(String content) {
        final Document document = Objects.requireNonNull(
                documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), content, null))
                        .data(),
                "opened document");
        return document.units().getFirst().segments().getFirst();
    }

    private static Decision decisionOf(Result<Decision> result) {
        return Objects.requireNonNull(result.data(), "decision");
    }
}
