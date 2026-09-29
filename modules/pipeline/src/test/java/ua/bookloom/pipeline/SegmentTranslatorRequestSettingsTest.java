package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;

/** A draft states the output allowance of its source, and its structural repair states the same one. */
class SegmentTranslatorRequestSettingsTest {

    @TempDir
    private Path tempDir;

    @Test
    void translate_structuralRepair_repeatsTheDraftsContextAndAllowance() {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Segment segment = Objects.requireNonNull(documents
                        .open(TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door."))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("not json", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(
                        TranslationJobTestSupport.targetReply("Він відчинив ⟦g0⟧старі⟦g1⟧ двері."),
                        FinishReason.STOP)));

        DraftStepFixtures.segmentTranslator(documents, model, BookFormat.MARKDOWN, "uk", "en")
                .translate(segment);

        assertThat(model.requests())
                .extracting(request -> request.contextWindow(), request -> request.expectedOutputTokens())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(8192, 16), org.assertj.core.groups.Tuple.tuple(8192, 16));
    }
}
