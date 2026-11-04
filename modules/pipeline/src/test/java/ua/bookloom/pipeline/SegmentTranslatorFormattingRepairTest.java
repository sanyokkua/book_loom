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
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.DraftOutcome;

/** Formatting-repair regressions whose real Markdown restoration path is the contract under test. */
class SegmentTranslatorFormattingRepairTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // IF a complete token sequence restores as an empty Markdown link, THEN code places the pair again by position
    // before any model call, and the draft carries the low auto-restored note.
    @Test
    void translate_detachedLinkPair_isPlacedAgainByCodeWithNoCall() {
        final Segment segment = segmentOf("See [chapter two](ch2.md) now.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("See translated chapter two ⟦g0⟧ ⟦g1⟧ now.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget())
                .isEqualTo("See [translated chapter](ch2.md) two now.");
        assertThat(DraftStepFixtures.drafted(result).autoRepair()).isNotNull();
        assertThat(model.requests()).hasSize(1);
    }

    // IF a reply empties a pair, THEN code places it again before any model call.
    @Test
    void translate_emptiedPairReply_isPlacedAgainByCodeWithNoCall() {
        final Segment segment = segmentOf("The *second* marked paragraph.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("Другий ⟦g0⟧⟦g1⟧ позначений абзац.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
        assertThat(DraftStepFixtures.drafted(result).autoRepair()).isNotNull();
        assertThat(model.requests()).hasSize(1);
    }

    // IF a task marker is dropped, THEN it is put back first without a model call, keeping its checked state.
    @Test
    void translate_droppedTaskMarker_putsItBackFirstWithoutACall() {
        final Segment segment = segmentOf("- [ ] Gravity is identical everywhere.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies("Гравітація всюди однакова.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isEqualTo("[ ] Гравітація всюди однакова.");
        assertThat(model.requests()).hasSize(1);
    }

    private Segment segmentOf(String content) {
        final Document document = Objects.requireNonNull(
                documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), content, null))
                        .data(),
                "opened document");
        return document.units().getFirst().segments().getFirst();
    }
}
