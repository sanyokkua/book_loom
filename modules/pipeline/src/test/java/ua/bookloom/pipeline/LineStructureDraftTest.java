package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * A TXT list drafted by a model that merges its lines, from the earth-gravity run (validator layout FAIL ch1-l1): the
 * line mark is shown to the model, a reply that keeps it is restored line for line even with a space after it, and a
 * reply that drops it gets its line end put back by position, with the low markup finding, rather than being accepted
 * as one line.
 */
class LineStructureDraftTest {

    private static final String LIST = """
            • Mass describes how much matter an object contains.
            • Weight describes the gravitational force acting on that mass.
            """;

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    @Test
    void translate_replyKeepingTheLineMarkWithASpace_isRestoredOnTwoLines() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "• Маса описує, скільки речовини містить тіло.⟦g0⟧ • Вага описує силу тяжіння, що діє на цю масу.");

        final DraftOutcome.Drafted drafted = drafted(model);

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("Copy this exact ordered sequence unchanged: ⟦g0⟧");
        assertThat(drafted.restoredTarget())
                .isEqualTo(
                        "• Маса описує, скільки речовини містить тіло.\n• Вага описує силу тяжіння, що діє на цю масу.");
        assertThat(drafted.autoRepair()).isNull();
    }

    @Test
    void translate_replyMergingTheLines_getsItsLineEndBackWithAMarkupFinding() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "• Маса описує, скільки речовини містить тіло. • Вага описує силу тяжіння, що діє на цю масу.");

        final DraftOutcome.Drafted drafted = drafted(model);

        assertThat(model.requests()).hasSize(1);
        assertThat(Objects.requireNonNull(drafted.restoredTarget()).lines()).hasSize(2);
        assertThat(drafted.autoRepair()).isNotNull();
    }

    private DraftOutcome.Drafted drafted(final ScriptedChatModel model) {
        final Path book = TestBooks.txt(tempDir.resolve("book.txt"), LIST);
        final Segment segment = Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
        final ProtectedMask mask = ProtectedMask.none(segment.masked());
        final GateFunction gate =
                ProtectedSpans.gate(Map.of(segment.id(), mask), GateFunction.of(documents, BookFormat.TXT));
        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        gate, (kind, segmentId, request) -> model.chat(request), BookFormat.TXT, "uk", "en")
                .translate(segment, DraftContext.empty(), mask);
        return DraftStepFixtures.drafted(result);
    }
}
