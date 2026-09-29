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
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.DraftContext;

/** A draft over the text with its protected spans hidden: what the model is shown, repaired with and accepted as. */
class SegmentTranslatorProtectedSpansTest {

    private static final GlossaryEntry HALE =
            new GlossaryEntry("p1:hale", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
    }

    @Test
    void translate_lockedNameHidden_isSentAsATokenAndAcceptedAsTheEnteredName() {
        final Segment segment = markdownSegment("Hale opened the *old* door.");
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of(HALE));
        final ScriptedChatModel model = TranslationJobTestSupport.replies("⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        final Result<Decision> result = translator(segment, mask, model, BookFormat.MARKDOWN)
                .translate(segment, DraftContext.empty(), mask.maskedText());

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("Copy this exact ordered sequence unchanged: ⟦g2⟧ ⟦g0⟧ ⟦g1⟧")
                .contains("<Text>\n⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>")
                .doesNotContain("Hale opened");
        final Decision decision = Objects.requireNonNull(result.data());
        assertThat(decision.segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decision.segment().targetInner()).isEqualTo("Гейл відчинив *старі* двері.");
    }

    @Test
    void translate_replyDropsTheNamesToken_repairsWithTheSameHiddenTextAndAllowance() {
        final Segment segment = markdownSegment("Hale opened the *old* door.");
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of(HALE));
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", "⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері.");

        final Result<Decision> result = translator(segment, mask, model, BookFormat.MARKDOWN)
                .translate(segment, DraftContext.empty(), mask.maskedText());

        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<Text>\n⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>")
                .contains("Copy this exact ordered sequence unchanged: ⟦g2⟧ ⟦g0⟧ ⟦g1⟧")
                .contains("came back 0 times");
        assertThat(model.requests().get(1).expectedOutputTokens())
                .isEqualTo(model.requests().getFirst().expectedOutputTokens());
        assertThat(Objects.requireNonNull(result.data()).segment().targetInner())
                .isEqualTo("Гейл відчинив *старі* двері.");
    }

    @Test
    void translate_replyIsNotJson_structuralRepairAlsoShowsTheHiddenText() {
        final Segment segment = markdownSegment("Hale opened the *old* door.");
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of(HALE));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.ok(new ChatResponse("not json", FinishReason.STOP)))
                .answer(Result.ok(new ChatResponse(
                        TranslationJobTestSupport.targetReply("⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері."),
                        FinishReason.STOP)));

        final Result<Decision> result = translator(segment, mask, model, BookFormat.MARKDOWN)
                .translate(segment, DraftContext.empty(), mask.maskedText());

        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<Text>\n⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>")
                .doesNotContain("Hale opened");
        assertThat(Objects.requireNonNull(result.data()).segment().status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void translate_foreignRunKept_isNeverSentAndComesBackAsItsOwnMarkup() {
        final Path book = TestBooks.epub(
                tempDir.resolve("Book.epub"),
                List.of(List.of("She whispered <i xml:lang=\"fr\">au revoir</i> and left.")),
                "en");
        final Document document = Objects.requireNonNull(documents.open(book).data());
        final Segment segment = document.units().getFirst().segments().getFirst();
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of());
        final ScriptedChatModel model = TranslationJobTestSupport.replies("Вона прошепотіла ⟦g2⟧ і пішла.");

        final Result<Decision> result = translator(segment, mask, model, BookFormat.EPUB)
                .translate(segment, DraftContext.empty(), mask.maskedText());

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("<Text>\nShe whispered ⟦g2⟧ and left.\n</Text>")
                .doesNotContain("au revoir");
        assertThat(Objects.requireNonNull(result.data()).segment().targetInner())
                .isEqualTo("Вона прошепотіла <i xml:lang=\"fr\">au revoir</i> і пішла.");
    }

    private SegmentTranslator translator(
            final Segment segment, final ProtectedMask mask, final ScriptedChatModel model, final BookFormat format) {
        final GateFunction gate = ProtectedSpans.gate(Map.of(segment.id(), mask), GateFunction.of(documents, format));
        return TranslationJobTestSupport.segmentTranslator(gate, model, format, "uk", "en");
    }

    private Segment markdownSegment(final String source) {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), source);
        return Objects.requireNonNull(documents.open(book).data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }
}
