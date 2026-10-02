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
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * The retry order for a reply the placeholder gate refuses, on the two paragraphs of a real chapter opening whose
 * export failed: a drop cap {@code <span>“A</span>bove} and an italic run before a closing quote. A dropped token is
 * put back without a call; a reply only a model can fix gets one repair told exactly what is wrong, and the
 * repair's own dropped token is then put back without another call; a drop cap is shown to the model as a whole word.
 */
class PlaceholderRecoveryTest {

    private static final String DROP_CAP = "<span class=\"calibre8\">“A</span>bove all,” said his master.";
    private static final String ITALIC = "“Remember <i class=\"calibre3\">this,”</i> he said in a soft voice.";

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    @Test
    void translate_italicReplyDroppingTheClosingToken_isRestoredWithoutARepairCall() {
        final Segment segment = epubSegment(ITALIC);
        final ScriptedChatModel model =
                TranslationJobTestSupport.replies("«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.");

        final DraftOutcome.Drafted drafted = drafted(segment, ProtectedMask.none(segment.masked()), model);

        assertThat(model.requests()).hasSize(1);
        assertThat(drafted.restoredTarget())
                .isEqualTo("«Пам'ятай <i class=\"calibre3\">це»,</i> — сказав він тихим голосом.");
        assertThat(drafted.autoRepair())
                .extracting(QaFinding::kind, QaFinding::severity)
                .containsExactly("markup", Severity.LOW);
    }

    @Test
    void translate_swappedPairThenRepairDroppingTheClosingToken_isRestoredAfterOneRepairCall() {
        final Segment segment = epubSegment(ITALIC);
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "«Пам'ятай ⟦g1⟧це»,⟦g0⟧ — сказав він тихим голосом.", "«Пам'ятай ⟦g0⟧це», — сказав він тихим голосом.");

        final DraftOutcome.Drafted drafted = drafted(segment, ProtectedMask.none(segment.masked()), model);

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("Out of order: ⟦g1⟧ comes before ⟦g0⟧.")
                .contains("In <Text>, ⟦g0⟧…⟦g1⟧ wraps \"this,”\"");
        assertThat(drafted.restoredTarget())
                .isEqualTo("«Пам'ятай <i class=\"calibre3\">це»,</i> — сказав він тихим голосом.");
    }

    @Test
    void translate_dropCap_isShownAsAWholeWordAndPutBackOnTheFirstLetterWithNoFinding() {
        final Segment segment = epubSegment(DROP_CAP);
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of());
        final ScriptedChatModel model = TranslationJobTestSupport.replies("«Понад усе», — сказав його господар.");

        final DraftOutcome.Drafted drafted = drafted(segment, mask, model);

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("<Text>\n“Above all,” said his master.\n</Text>")
                .contains(
                        "Copy this exact ordered sequence unchanged: (none — write no ⟦gN⟧ token at all; write every name as plain text)");
        assertThat(drafted.restoredTarget())
                .isEqualTo("<span class=\"calibre8\">«П</span>онад усе», — сказав його господар.");
        assertThat(drafted.autoRepair()).isNull();
    }

    @Test
    void translate_dropCapWrappedAroundTheWholeReply_isRefusedAndRepairedOnTheFirstLetter() {
        final Segment segment = epubSegment(DROP_CAP);
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "⟦g0⟧«Понад усе», — сказав його господар.⟦g1⟧", "«Понад усе», — сказав.");

        final DraftOutcome.Drafted drafted = drafted(segment, ProtectedMask.none(segment.masked()), model);

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("put all of its words inside one piece of formatting");
        assertThat(drafted.restoredTarget()).isEqualTo("<span class=\"calibre8\">«П</span>онад усе», — сказав.");
    }

    private DraftOutcome.Drafted drafted(
            final Segment segment, final ProtectedMask mask, final ScriptedChatModel model) {
        final GateFunction gate =
                ProtectedSpans.gate(Map.of(segment.id(), mask), GateFunction.of(documents, BookFormat.EPUB));
        final Result<DraftOutcome> result = DraftStepFixtures.segmentTranslator(
                        gate, (kind, segmentId, request) -> model.chat(request), BookFormat.EPUB, "uk", "en")
                .translate(segment, DraftContext.empty(), mask);
        return DraftStepFixtures.drafted(result);
    }

    private Segment epubSegment(final String paragraph) {
        final Path book = TestBooks.epub(tempDir.resolve("book.epub"), List.of(List.of(paragraph)), "en");
        return Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
