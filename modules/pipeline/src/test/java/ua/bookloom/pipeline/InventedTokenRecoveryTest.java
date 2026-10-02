package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
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
 * The silent loss of a name from the gemma4:e4b run on the earth-gravity fixture: a paragraph with no token came back
 * as {@code …і ⟦g1⟧ ніколи не дозволяв…}, the deterministic repair dropped the invented token with the name it stood
 * for, and the segment was accepted. An invented token that stands for a word now goes to the model repair, told to
 * write the name out; one glued to a word is still dropped without a call, keeping the word.
 */
class InventedTokenRecoveryTest {

    private static final String SOURCE =
            "Words like heavy and light describe weight, not mass, and Vance never let a student mix them up.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    @Test
    void translate_nameReplacedByAnInventedToken_isRepairedByTheModelWithANoteNotStripped() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Слова «важкий» і «легкий» описують вагу, а не масу, і ⟦g1⟧ ніколи не дозволяла студентам їх плутати.",
                "Слова «важкий» і «легкий» описують вагу, а не масу, і Венс ніколи не дозволяла студентам їх плутати.");

        final DraftOutcome.Drafted drafted = drafted(model);

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("⟦g1⟧ is not a placeholder of this text; write names as plain text.");
        assertThat(drafted.restoredTarget()).contains("і Венс ніколи");
    }

    @Test
    void translate_inventedTokenKeptByTheRepairToo_isNeverRestoredWithoutTheName() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Слова описують вагу, а не масу, і ⟦g1⟧ ніколи не дозволяла студентам їх плутати.",
                "Слова описують вагу, а не масу, і ⟦g1⟧ ніколи не дозволяла студентам їх плутати.");

        final DraftOutcome.Drafted drafted = drafted(model);

        assertThat(drafted.restoredTarget()).isNull();
        assertThat(drafted.gateFinding()).isNotNull();
    }

    @Test
    void translate_inventedTokenGluedToTheName_isDroppedWithoutACallKeepingTheName() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Слова описують вагу, а не масу, і ⟦g1⟧Венс⟧ ніколи не дозволяла студентам їх плутати.");

        final DraftOutcome.Drafted drafted = drafted(model);

        assertThat(model.requests()).hasSize(1);
        assertThat(drafted.restoredTarget())
                .isEqualTo("Слова описують вагу, а не масу, і Венс ніколи не дозволяла студентам їх плутати.");
    }

    @Test
    void translate_textWithNoToken_tellsTheModelToWriteNoTokenAndNamesAsText() {
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Слова описують вагу, а не масу, і Венс ніколи не дозволяла студентам їх плутати.");

        drafted(model);

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("(none — write no ⟦gN⟧ token at all; write every name as plain text)");
    }

    private DraftOutcome.Drafted drafted(final ScriptedChatModel model) {
        final Segment segment = epubSegment();
        final ProtectedMask mask = ProtectedMask.none(segment.masked());
        final GateFunction gate =
                ProtectedSpans.gate(Map.of(segment.id(), mask), GateFunction.of(documents, BookFormat.EPUB));
        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        gate, (kind, segmentId, request) -> model.chat(request), BookFormat.EPUB, "uk", "en")
                .translate(segment, DraftContext.empty(), mask);
        return DraftStepFixtures.drafted(result);
    }

    private Segment epubSegment() {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), List.of(List.of(SOURCE)), "en");
        return Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
