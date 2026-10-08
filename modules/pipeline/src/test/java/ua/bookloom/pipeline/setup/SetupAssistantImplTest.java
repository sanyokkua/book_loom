package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.FileNameSuggestion;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** The model's proposals for the file name and the Book Brief: what it is shown, and what is kept from its answer. */
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

    // IF the file name were taken from the model unchecked, THEN a reply could name a path outside the folder.
    @Test
    void suggestFileName_modelReply_isShownTheOriginalNameAndReturnedCleaned() {
        model.answer(reply("{\"target\":\"Джордж Орвел. Ферма тварин. 1945\"}"));

        final Result<FileNameSuggestion> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.data()).isEqualTo(new FileNameSuggestion("Джордж Орвел. Ферма тварин. 1945", false));
        assertThat(userMessage()).contains("File name: Book");
    }

    // IF the proposal's model call were not announced, THEN the busy card could not show what the file name asked.
    @Test
    void suggestFileName_withProgress_announcesTheCallWithTheMessagesSent() {
        model.answer(reply("{\"target\":\"Ферма тварин\"}"));
        final List<JobEvent> events = new ArrayList<>();

        assistant.suggestFileName(desk.projectId(), model, events::add);

        final CallSnapshot last = events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(last.kind()).isEqualTo(CallKind.FILE_NAME);
        assertThat(last.sent()).isEqualTo(model.requests().getFirst().messages());
    }

    @Test
    void suggestFileName_replyWithPathCharacters_isMadeASafeName() {
        model.answer(reply("{\"target\":\"../etc/Книга: частина *1*?\"}"));

        assertThat(assistant.suggestFileName(desk.projectId(), model).data().name())
                .isEqualTo("etc Книга частина 1");
    }

    @Test
    void suggestFileName_replyThatIsNoName_isAValidationError() {
        model.answer(reply("{\"target\":\"///\"}"));

        final Result<FileNameSuggestion> result = assistant.suggestFileName(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"  A. B. 1999 |A. B. 1999", "x\\y|x y", "..hidden..|hidden"})
    void cleanFileName_proposal_losesWhatAFileNameCannotHold(final String raw, final String expected) {
        assertThat(SetupAssistantImpl.cleanFileName(raw)).isEqualTo(expected);
    }

    // IF the name were cut by characters, THEN 150 Cyrillic letters would be 300 bytes and no file system would take
    // it.
    @Test
    void cleanFileName_longCyrillicName_isCutToTheByteLimitBetweenWholeCharacters() {
        final String cut = SetupAssistantImpl.cleanFileName("Ї".repeat(150));

        assertThat(cut.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(200);
        assertThat(cut).isEqualTo("Ї".repeat(100));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"CON|CON_", "nul.txt|nul.txt_", "Console|Console", "A\u202Eb|A b"})
    void cleanFileName_deviceNamesAndInvisibleFormatCharacters_areMadeSafe(final String raw, final String expected) {
        assertThat(SetupAssistantImpl.cleanFileName(raw)).isEqualTo(expected);
    }

    // IF the model were not shown the opening of the book, THEN it could only guess from the title.
    @Test
    void suggestBrief_modelReply_isShownTheOpeningAndMappedToTheBriefsChoices() {
        model.answer(reply("{\"genre\":\"noir\",\"register\":\"casual\",\"voice\":\"first-person, short sentences\","
                + "\"audience\":\"adult readers\",\"narrator\":\"first\",\"narratorGender\":\"female\"}"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion)
                .isEqualTo(new BriefSuggestion(
                        "noir",
                        Register.CASUAL,
                        "first-person, short sentences",
                        "adult readers",
                        NarratorPerson.FIRST,
                        Gender.FEMALE));
        assertThat(userMessage())
                .contains("Opening paragraphs:", "The rain had not stopped")
                .doesNotContain("\nOk.\n");
    }

    @Test
    void suggestBrief_unknownWordsInTheReply_fallBackToTheNeutralChoices() {
        model.answer(reply("{\"genre\":\"\",\"register\":\"wild\",\"voice\":\"\",\"audience\":\"\","
                + "\"narrator\":\"fourth\",\"narratorGender\":\"robot\"}"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.genre()).isNull();
        assertThat(suggestion.register()).isEqualTo(Register.NEUTRAL);
        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.UNSPECIFIED);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.UNKNOWN);
    }

    @Test
    void suggestBrief_replyThatIsNotJson_isAValidationError() {
        model.answer(reply("I think it is a noir."));

        final Result<BriefSuggestion> result = assistant.suggestBrief(desk.projectId(), model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void suggestBrief_unknownProject_isAValidationErrorAndAsksNothing() {
        final Result<BriefSuggestion> result = assistant.suggestBrief("no-such-project", model);

        assertThat(result.error()).isNotNull();
        assertThat(result.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
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

    // IF a first-person book carried a "third-person limited" voice note, THEN every prompt would contradict itself.
    @Test
    void suggestBrief_narratorAlreadySet_isKeptAndAContradictingVoiceNoteIsDropped() {
        final Project stored = desk.projects().find(desk.projectId()).data().orElseThrow();
        desk.projects()
                .save(stored.withBrief(stored.brief().withNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE))));
        model.answer(reply("{\"genre\":\"noir\",\"register\":\"casual\",\"voice\":\"third-person limited, terse\","
                + "\"audience\":\"adults\",\"narrator\":\"third\",\"narratorGender\":\"unknown\"}"));

        final BriefSuggestion suggestion =
                assistant.suggestBrief(desk.projectId(), model).data();

        assertThat(suggestion.narrator()).isEqualTo(NarratorPerson.FIRST);
        assertThat(suggestion.narratorGender()).isEqualTo(Gender.MALE);
        assertThat(suggestion.voiceEra()).isNull();
        assertThat(suggestion.genre()).isEqualTo("noir");
    }
}
