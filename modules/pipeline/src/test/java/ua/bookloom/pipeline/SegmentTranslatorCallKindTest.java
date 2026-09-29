package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.GateFunction;

/** Each call the translator makes for a segment goes through the run's seam under its own kind. */
class SegmentTranslatorCallKindTest {

    private static final String ACCEPTED = "HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    // Naming the repair DRAFT would tell a screen the model is still on the first attempt.
    @Test
    void translate_invalidStructuredReply_callsDraftThenStructuralRepairForTheSegment() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("{\"segments\":", FinishReason.STOP)))
                .answer(Result.ok(
                        new ChatResponse(TranslationJobTestSupport.targetReply(ACCEPTED), FinishReason.STOP)));

        translator(model).translate(segment());

        assertThat(calls).containsExactly("DRAFT Book.md:0", "STRUCTURAL_REPAIR Book.md:0");
    }

    @Test
    void translate_missingPlaceholder_callsDraftThenPlaceholderRepairForTheSegment() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies("HE OPENED THE ⟦g0⟧OLD DOOR.", ACCEPTED);

        translator(model).translate(segment());

        assertThat(calls).containsExactly("DRAFT Book.md:0", "PLACEHOLDER_REPAIR Book.md:0");
    }

    private SegmentTranslator translator(final ScriptedChatModel model) {
        return DraftStepFixtures.segmentTranslator(
                GateFunction.of(documents, BookFormat.MARKDOWN),
                (kind, segmentId, request) -> {
                    calls.add(kind + " " + segmentId);
                    return model.chat(request);
                },
                BookFormat.MARKDOWN,
                "uk",
                "en");
    }

    private Segment segment() {
        final Path path = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.");
        return Objects.requireNonNull(documents.open(path).data(), "opened document")
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
