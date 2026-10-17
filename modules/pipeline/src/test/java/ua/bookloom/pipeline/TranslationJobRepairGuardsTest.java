package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;
import static ua.bookloom.pipeline.TranslationJobTestSupport.targetReply;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A repair never makes a clean draft worse, and only a gender somebody confirmed is enforced (15i.T1, 15i.T2), seen
 * through a whole run on the scripted model.
 */
class TranslationJobRepairGuardsTest {

    private static final String RUSSIAN_LETTER = "Я відчинив стары двері.";
    private static final String RIGHT = "Я відчинив старі двері.";
    private static final String WITH_INVENTED_TOKENS = "Я відчинив ⟦g0⟧старі⟦g1⟧ двері.";
    private static final String STARTS_WITH_QUOTE_CODE = "{\"target\":\"\\u001eЯ відчинив старі двері.\"}";

    private static final String CHISEL_SOURCE = "Chisel opened the old door.";
    private static final String MASCULINE = "Чизел відчинив старі двері.";
    private static final String FEMININE = "Чизел відчинила старі двері.";
    private static final String GENDER_EDIT = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
            + "{\"criterion\":\"gender\",\"quote\":\"відчинив\",\"replacement\":\"відчинила\"}]}]}";

    @TempDir
    private Path tempDir;

    private static Result<ChatResponse> answer(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    // IF a fix reply that opens with a stray quote code were refused as malformed, THEN the finding it was asked to
    // mend would stay in the book although the model wrote the right text.
    @Test
    void run_fixReplyStartsWithAQuoteCode_isReadAndRepairsTheSegment() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER).answer(answer(STARTS_WITH_QUOTE_CODE));
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
    }

    // IF a fix reply that cannot be read dropped the draft's text, THEN one bad reply would leave the book without the
    // paragraph; the Russian letter stays blocking, so the segment is flagged with its draft as the machine text.
    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
            strings = {
                "not a json reply at all",
                "{\"target\":",
                "{\"target\":\"Я відчинив\\u0001 старі двері.\"}",
                "{\"other\":\"Я відчинив старі двері.\"}"
            })
    void run_fixReplyIsMalformed_flagsTheSegmentAndKeepsTheDraftAsItsMachineText(final String malformed) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(answer(targetReply(RUSSIAN_LETTER)))
                .answerTimes(2, answer(malformed));
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, SegmentPath.REPAIRED, RUSSIAN_LETTER);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .contains("alphabet");
    }

    // IF a fix that still holds a Russian letter (a different one) were adopted, THEN the repair would only move the
    // defect; the alphabet check reads the fix too, and the segment is flagged with the first draft.
    @Test
    void run_fixStillHoldsARussianLetter_flagsTheSegmentAndKeepsTheDraft() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER, "Я відчинив стыри двері.");
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, SegmentPath.REPAIRED, RUSSIAN_LETTER);
    }

    // A fix that wraps words in tokens the segment never had is not refused: the designed document-gate path puts the
    // tokens aside, the good fix is adopted, and a low note says the markup was restored without a model.
    @Test
    void run_fixWrapsWordsInTokensTheSegmentLacks_adoptsTheTextWithTheTokensRemovedAndALowNote() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER, WITH_INVENTED_TOKENS);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        final SegmentRecord stored = stored(project, "Book.txt:0");
        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored)
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        assertThat(stored.findings())
                .extracting(QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple(Severity.LOW, "placeholder"));
    }

    // IF a gender a scan only suggested were enforced, THEN a wrong guess about one main character would be
    // "corrected" into every paragraph that names her (26 of 41 wrong paragraphs of the Oct 10 26b run).
    @Test
    void run_womanOnlySuggested_earnsNoDirectedFix() {
        final ScriptedChatModel model = replies(MASCULINE, FEMININE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withSuggestedGender(Gender.FEMALE));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT);
        assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(MASCULINE);
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::raisedBy)
                .doesNotContain("name-gender");
    }

    // The same woman set by the person is enforced, before and after the task.
    @Test
    void run_womanSetByThePerson_earnsOneDirectedFix() {
        final ScriptedChatModel model = replies(MASCULINE, FEMININE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withGender(Gender.FEMALE));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentPath.REPAIRED, FEMININE);
    }

    // IF the reviewer's edit toward a suggested gender were applied, THEN the reviewer would enforce the guess that the
    // checks no longer do: the edit becomes a low note and the draft stays.
    @Test
    void run_reviewerEditsTowardAGenderOnlySuggested_leavesALowNoteAndTheDraft() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(DRAFT, answer(targetReply(MASCULINE)))
                .answerTo(FIX, answer(targetReply(FEMININE)))
                .answerTo(REVIEW, answer(GENDER_EDIT));
        final TestProject project = chisel(QualityDial.BALANCED, entry -> entry.withSuggestedGender(Gender.FEMALE));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, REVIEW);
        assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(MASCULINE);
        assertThat(stored(project, "Book.txt:0").findings())
                .filteredOn(finding -> finding.kind().equals("ignored-edit"))
                .singleElement()
                .satisfies(finding -> assertThat(finding.severity()).isEqualTo(Severity.LOW));
    }

    // The same edit toward a gender the person set is applied, before and after the task.
    @Test
    void run_reviewerEditsTowardAGenderSetByThePerson_isApplied() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(DRAFT, answer(targetReply(MASCULINE)))
                .answerTo(REVIEW, answer(GENDER_EDIT));
        final TestProject project = chisel(QualityDial.BALANCED, entry -> entry.withGender(Gender.FEMALE));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, REVIEW);
        assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(FEMININE);
    }

    private TestProject chisel(final QualityDial dial, final UnaryOperator<GlossaryEntry> gender) {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), CHISEL_SOURCE);
        final TestProject project = project(book, brief("en", "uk", dial));
        final GlossaryEntry chisel = gender.apply(new GlossaryEntry(
                GlossaryIds.of(project.id(), "Chisel"),
                project.id(),
                "Chisel",
                "Чизел",
                TermType.CHARACTER,
                Gender.UNKNOWN,
                false));
        Objects.requireNonNull(project.stores().glossary().add(chisel).data(), "added");
        return project;
    }
}
