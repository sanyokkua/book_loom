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

    // IF a complete token sequence restores as an empty Markdown link, THEN one formatting repair can correct it.
    @Test
    void translate_detachedLinkPair_repairsAndAcceptsCorrectedTarget() {
        final Segment segment = segmentOf("See [chapter two](ch2.md) now.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "See translated chapter two ⟦g0⟧ ⟦g1⟧ now.", "See ⟦g0⟧translated chapter two⟦g1⟧ now.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
        assertThat(DraftStepFixtures.drafted(result).restoredTarget())
                .isEqualTo("See [translated chapter two](ch2.md) now.");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("Paired placeholders must enclose nonblank translated text");
    }

    // The placeholder repair tells the model which rule the gate refused, not only which tokens are required.
    @Test
    void translate_emptiedPairReply_placeholderRepairNamesTheRule() {
        final Segment segment = segmentOf("The *second* marked paragraph.");
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Другий ⟦g0⟧⟦g1⟧ позначений абзац.", "Другий ⟦g0⟧позначений⟦g1⟧ абзац.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<RejectedTarget>\nДругий ⟦g0⟧⟦g1⟧ позначений абзац.\n</RejectedTarget>")
                .contains("Copy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧\n[Rule the rejected target broke]\n")
                .contains("emptied the formatting around its words");
    }

    // IF a task marker is dropped, THEN the one formatting repair restores its original checked state.
    @Test
    void translate_droppedTaskMarker_repairsAndAcceptsCorrectedTarget() {
        final Segment segment = segmentOf("- [ ] Gravity is identical everywhere.");
        final ScriptedChatModel model =
                TranslationJobTestSupport.replies("Гравітація всюди однакова.", "⟦g0⟧Гравітація всюди однакова.");

        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isEqualTo("[ ] Гравітація всюди однакова.");
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
}
