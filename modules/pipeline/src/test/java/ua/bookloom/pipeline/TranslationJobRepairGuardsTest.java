package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
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
 * through a whole run on the scripted model. A case still wrong today sits in {@link KnownRed#until}; the cases
 * without it hold before and after the tasks.
 */
class TranslationJobRepairGuardsTest {

    private static final String T1 = "15i.T1";
    private static final String T2 = "15i.T2";

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

        KnownRed.until(T2, "fix-leading-quote-code", () -> {
            assertThat(stored(project, "Book.txt:0"))
                    .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                    .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, RIGHT);
        });
    }

    // IF the fix wrapped the words in tokens the segment never had, THEN the repair path would strip them and adopt a
    // text the model never wrote as the fix.
    @Test
    void run_fixWrapsWordsInTokensTheSegmentLacks_keepsTheAcceptedDraft() {
        final ScriptedChatModel model = replies(RUSSIAN_LETTER, WITH_INVENTED_TOKENS);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.FAST));

        report(job(project, model).run());

        KnownRed.until(T2, "fix-invents-tokens", () -> {
            assertThat(formats(model)).containsExactly(DRAFT, FIX);
            assertThat(stored(project, "Book.txt:0"))
                    .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                    .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT, RUSSIAN_LETTER);
        });
    }

    // IF a gender a scan only suggested were enforced, THEN a wrong guess about one main character would be
    // "corrected" into every paragraph that names her (26 of 41 wrong paragraphs of the Oct 10 26b run).
    @Test
    void run_womanOnlySuggested_earnsNoDirectedFix() {
        final ScriptedChatModel model = replies(MASCULINE, FEMININE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withSuggestedGender(Gender.FEMALE));

        report(job(project, model).run());

        KnownRed.until(T1, "suggested-gender-no-fix", () -> {
            assertThat(formats(model)).containsExactly(DRAFT);
            assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(MASCULINE);
            assertThat(stored(project, "Book.txt:0").findings())
                    .extracting(QaFinding::raisedBy)
                    .doesNotContain("name-gender");
        });
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

        KnownRed.until(T1, "suggested-gender-no-reviewer-edit", () -> {
            assertThat(formats(model)).containsExactly(DRAFT, REVIEW);
            assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(MASCULINE);
            assertThat(stored(project, "Book.txt:0").findings())
                    .filteredOn(finding -> finding.kind().equals("ignored-edit"))
                    .singleElement()
                    .satisfies(finding -> assertThat(finding.severity()).isEqualTo(Severity.LOW));
        });
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
