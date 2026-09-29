package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A review retry's draft carries the person's note and, when asked, the draft's lower temperature. */
class SegmentTranslatorRetryTest {

    private static final String NOTE = "keep it more formal";

    @TempDir
    private Path tempDir;

    @Test
    void translate_noteAndLowerTemperature_draftsAt01WithTheNoteAndRepairsAtTheRepairTemperature() {
        // only the draft has a lower temperature; the structural repair keeps its 0.2 but still carries the note
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("not json", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(
                        TranslationJobTestSupport.targetReply("Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
                        FinishReason.STOP)));
        final Segment segment = doorSegment();

        DraftStepFixtures.segmentTranslator(documents(), model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment, DraftContext.empty(), ProtectedMask.none(segment.masked()), NOTE, true);

        assertThat(model.requests())
                .extracting(
                        ChatRequest::temperature,
                        request -> userMessage(request).contains(NOTE))
                .containsExactly(tuple(0.1, true), tuple(0.2, true));
    }

    @Test
    void translate_emptyNoteAndNoLowerTemperature_isThePlainDraft() {
        // the plain overload's request: 0.2 and no extra-instruction block
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse(
                        TranslationJobTestSupport.targetReply("Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
                        FinishReason.STOP)));
        final Segment segment = doorSegment();

        DraftStepFixtures.segmentTranslator(documents(), model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment, DraftContext.empty(), ProtectedMask.none(segment.masked()), "", false);

        assertThat(model.requests().getFirst().temperature()).isEqualTo(0.2);
        assertThat(userMessage(model.requests().getFirst())).doesNotContain("[Extra instruction]");
    }

    private static DocumentPort documents() {
        return Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    private Segment doorSegment() {
        return Objects.requireNonNull(documents()
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door."))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().get(1).content();
    }
}
