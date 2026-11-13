package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.ChatRequests;

/**
 * A reply that copies a shown example instead of translating its item is asked for once more with another seed, and
 * a second copy flags the segment (design D9). The ru→uk pair file shows {@code — Стривай, ⟦g0⟧! — гукнула вона.}.
 */
class EchoGateTest {

    private static final String SOURCE = "— Тише, Зотов! — сказал он.";
    private static final String COPY = "— Стривай, Гордєю! — гукнула вона.";
    private static final String TRANSLATION = "— Тихше, Зотове! — сказав він.";
    private static final DocumentPort DOCUMENTS =
            Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);

    @TempDir
    private Path tempDir;

    private Segment segment() {
        return Objects.requireNonNull(DOCUMENTS
                        .open(TestBooks.txt(tempDir.resolve("Book.txt"), SOURCE))
                        .data())
                .units()
                .getFirst()
                .segments()
                .getFirst();
    }

    private static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply(target), FinishReason.STOP));
    }

    private static Result<DraftOutcome> draft(final Segment segment, final ScriptedChatModel model) {
        return DraftStepFixtures.segmentTranslator(
                        GateFunction.of(DOCUMENTS, BookFormat.TXT),
                        (kind, segmentId, request) -> model.chat(request),
                        BookFormat.TXT,
                        "uk",
                        "ru")
                .translate(segment, (TranslateContext) null, ProtectedMask.none(segment.masked()));
    }

    @Test
    void gate_copiedExample_asksOnceThenFlags() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(COPY)).answer(reply(COPY));

        final AppError flagged = DraftStepFixtures.flaggedAtOnce(draft(segment(), model));

        assertThat(flagged)
                .extracting(AppError::code, AppError::title)
                .containsExactly(ErrorCode.validation, SegmentTranslator.EXAMPLE_COPIED);
        assertThat(model.requests()).extracting(ChatRequest::seed).containsExactly(null, ChatRequests.RETRY_SEED);
        assertThat(model.requests().get(1).messages())
                .isEqualTo(model.requests().get(0).messages());
    }

    @Test
    void gate_copiedExampleThenATranslation_takesTheSecondAnswer() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(reply(COPY)).answer(reply(TRANSLATION));

        final DraftOutcome.Drafted drafted = DraftStepFixtures.drafted(draft(segment(), model));

        assertThat(drafted.maskedReply()).isEqualTo(TRANSLATION);
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void gate_translationSharingNoExampleRun_isTakenAtOnce() {
        final ScriptedChatModel model = new ScriptedChatModel().answer(reply(TRANSLATION));

        DraftStepFixtures.drafted(draft(segment(), model));

        assertThat(model.requests()).hasSize(1);
    }
}
