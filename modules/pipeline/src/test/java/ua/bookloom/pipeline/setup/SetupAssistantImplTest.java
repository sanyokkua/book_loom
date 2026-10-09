package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The model's proposal of the file name: what it is shown, and what is kept from its answer. */
class SetupAssistantImplTest {

    private static final String LONG_PARAGRAPH =
            "The rain had not stopped for three days, and I was beginning to think the city had forgotten the sun.";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private ScriptedChatModel model;
    private SetupAssistantImpl assistant;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(
                tempDir, LONG_PARAGRAPH + "\n\n" + LONG_PARAGRAPH + " Nobody came.\n\nOk.\n\n" + LONG_PARAGRAPH + "\n");
        model = new ScriptedChatModel();
        assistant = new SetupAssistantImpl(
                desk.projects(),
                desk.segments(),
                desk.glossary(),
                desk.openProjects(),
                new PromptTemplates(),
                new ObjectMapper(),
                java.time.Clock.systemUTC());
    }

    private static Result<ChatResponse> reply(final String json) {
        return Result.ok(new ChatResponse(json, FinishReason.STOP));
    }

    private String userMessage() {
        return model.requests().getFirst().messages().stream()
                .filter(message -> message.role() == ChatRole.USER)
                .map(ChatMessage::content)
                .findFirst()
                .orElseThrow();
    }

    private static final String LATIN_AUTHOR = "{\"target\":\"William Gibson. Палаючий хром. 1986\"}";
    private static final String UKRAINIAN_AUTHOR = "{\"target\":\"Вільям Гібсон. Палаючий хром. 1986\"}";

    // IF an author left in Latin letters were offered as it is, THEN every Ukrainian file name would need a hand fix.
    @Test
    void suggestFileName_authorLeftInLatinForACyrillicTarget_isAskedOnceMoreNamingThePart() {
        model.answer(reply(LATIN_AUTHOR)).answer(reply(UKRAINIAN_AUTHOR));

        final Result<FileNameSuggestion> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.data()).isEqualTo(new FileNameSuggestion("Вільям Гібсон. Палаючий хром. 1986", false));
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().getLast().messages().getLast().content())
                .contains("Correction:", "William Gibson")
                .contains("Latin letters");
    }

    // IF the model's lower-case second word were kept, THEN the file would name the book «Палаючий хром» while the book
    // says «Палаючий Хром».
    @Test
    void suggestFileName_bookHasATranslatedTitle_isShownIt_andTheNameSpellsTitleAndAuthorAsTheBookDoes() {
        translated(Map.of("aux:title", "Палаючий Хром", "aux:creator:0", "Вільям Гібсон"));
        model.answer(reply("{\"target\":\"вільям гібсон. палаючий хром. 1986\"}"));

        final Result<FileNameSuggestion> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.error()).isNull();
        assertThat(result.data()).isEqualTo(new FileNameSuggestion("Вільям Гібсон. Палаючий Хром. 1986", false));
        assertThat(userMessage())
                .contains("Translated title (use exactly as written): Палаючий Хром")
                .contains("Translated author (use exactly as written): Вільям Гібсон");
    }

    @Test
    void suggestFileName_titleNotTranslatedYet_isLeftAsTheModelWroteIt() {
        model.answer(reply("{\"target\":\"Вільям Гібсон. Палаючий хром. 1986\"}"));

        assertThat(assistant.suggestFileName(desk.projectId(), model).data().name())
                .isEqualTo("Вільям Гібсон. Палаючий хром. 1986");
        assertThat(userMessage()).doesNotContain("Translated title");
    }

    private void translated(final Map<String, String> targetsById) {
        desk.segments()
                .saveAll(
                        desk.projectId(),
                        targetsById.entrySet().stream()
                                .map(entry -> accepted(entry.getKey(), entry.getValue()))
                                .toList());
    }

    private SegmentRecord accepted(final String segmentId, final String target) {
        return new SegmentRecord(
                desk.projectId(),
                segmentId,
                "aux",
                1,
                SegmentKind.METADATA_TITLE,
                SegmentStatus.ACCEPTED,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }

    @Test
    void suggestFileName_authorStillLatinAfterTheCorrection_isKeptAndFlagged() {
        model.answer(reply(LATIN_AUTHOR)).answer(reply(LATIN_AUTHOR));

        final FileNameSuggestion suggestion =
                assistant.suggestFileName(desk.projectId(), model).data();

        assertThat(suggestion).isEqualTo(new FileNameSuggestion("William Gibson. Палаючий хром. 1986", true));
        assertThat(model.requests()).hasSize(2);
    }

    @Test
    void suggestFileName_correctionCallFails_keepsTheFirstNameFlagged() {
        model.answer(reply(LATIN_AUTHOR))
                .answer(Result.err(AppError.of(ErrorCode.unreachable, "Down", "The server is not answering.")));

        assertThat(assistant.suggestFileName(desk.projectId(), model).data())
                .isEqualTo(new FileNameSuggestion("William Gibson. Палаючий хром. 1986", true));
    }
}
