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
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.RepairReply;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.memory.ProtectedSpans;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.qa.LockedRendering;

/** A draft over the text with its protected spans hidden: what the model is shown, repaired with and accepted as. */
class SegmentTranslatorProtectedSpansTest {

    private static final GlossaryEntry HALE =
            GlossaryEntry.of("p1:hale", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, true);

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

        final Result<DraftOutcome> result =
                translator(segment, mask, model, BookFormat.MARKDOWN).translate(segment, DraftContext.empty(), mask);

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("<s id=\"1\">⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.</s>")
                .endsWith("Keep every ⟦…⟧ mark exactly once.")
                .doesNotContain("Hale opened");
        assertThat(DraftStepFixtures.drafted(result))
                .extracting(DraftOutcome.Drafted::restoredTarget, DraftOutcome.Drafted::lockedRenderings)
                .containsExactly("Гейл відчинив *старі* двері.", List.of(new LockedRendering("Hale", "Гейл")));
    }

    // The draft step makes no call for the lost name token; the one repair it hands on shows the same hidden text,
    // names every token and asks for the same output allowance as the draft.
    @Test
    void repairCall_replyDroppedTheNamesToken_repairsWithTheSameHiddenTextAndAllowance() {
        final Segment segment = markdownSegment("Hale opened the *old* door.");
        final ProtectedMask mask = ProtectedSpans.mask(segment, "en", ForeignPassagePolicy.KEEP, List.of(HALE));
        final ScriptedChatModel model = TranslationJobTestSupport.replies(
                "Він відчинив ⟦g0⟧старі⟦g1⟧ двері.", "⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері.");
        final SegmentTranslator translator = translator(segment, mask, model, BookFormat.MARKDOWN);

        final DraftOutcome.Drafted draft =
                DraftStepFixtures.drafted(translator.translate(segment, DraftContext.empty(), mask));
        final Result<RepairReply> repaired = translator
                .repairCall(segment, null, mask)
                .send(
                        draft.maskedReply(),
                        List.of(Objects.requireNonNull(draft.gateFinding())),
                        (kind, segmentId, request) -> model.chat(request));

        assertThat(draft.restoredTarget()).isNull();
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<s id=\"1\">⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.</s>")
                .contains("The protected token ⟦g2⟧ came back 0 times")
                .contains("<PreviousAttempt>\nВін відчинив ⟦g0⟧старі⟦g1⟧ двері.\n</PreviousAttempt>");
        assertThat(model.requests().get(1).expectedOutputTokens())
                .isEqualTo(model.requests().getFirst().expectedOutputTokens());
        assertThat(repaired.data()).isEqualTo(new RepairReply.Rewritten("⟦g2⟧ відчинив ⟦g0⟧старі⟦g1⟧ двері."));
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

        final Result<DraftOutcome> result =
                translator(segment, mask, model, BookFormat.MARKDOWN).translate(segment, DraftContext.empty(), mask);

        assertThat(model.requests().get(1).messages().get(1).content())
                .contains("<s id=\"1\">⟦g2⟧ opened the ⟦g0⟧old⟦g1⟧ door.</s>")
                .doesNotContain("Hale opened");
        assertThat(DraftStepFixtures.drafted(result).restoredTarget()).isNotNull();
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

        final Result<DraftOutcome> result =
                translator(segment, mask, model, BookFormat.EPUB).translate(segment, DraftContext.empty(), mask);

        assertThat(model.requests().getFirst().messages().get(1).content())
                .contains("<s id=\"1\">She whispered ⟦g2⟧ and left.</s>")
                .doesNotContain("au revoir");
        assertThat(DraftStepFixtures.drafted(result).restoredTarget())
                .isEqualTo("Вона прошепотіла <i xml:lang=\"fr\">au revoir</i> і пішла.");
    }

    private SegmentTranslator translator(
            final Segment segment, final ProtectedMask mask, final ScriptedChatModel model, final BookFormat format) {
        final GateFunction gate = ProtectedSpans.gate(Map.of(segment.id(), mask), GateFunction.of(documents, format));
        return DraftStepFixtures.segmentTranslator(
                gate, (kind, segmentId, request) -> model.chat(request), format, "uk", "en");
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
