package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
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
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * Who decided a character's gender decides what a run does with it (15i.T1): a gender the code only guessed is shown
 * to the model as "likely" and held against nothing, one the book verified or the person set is enforced. Seen
 * through a whole run on the scripted model; the cases a suggested woman and a reviewer edit toward her sit in
 * {@link TranslationJobRepairGuardsTest}.
 */
class TranslationJobGenderOriginTest {

    private static final String SOURCE = "Chisel opened the old door.";
    private static final String MASCULINE = "Чизел відчинив старі двері.";
    private static final String FEMININE = "Чизел відчинила старі двері.";
    private static final String GENDER_EDIT = "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":["
            + "{\"criterion\":\"gender\",\"quote\":\"відчинив\",\"replacement\":\"відчинила\"}]}]}";

    @TempDir
    private Path tempDir;

    private static Result<ChatResponse> answer(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }

    // IF the draft saw a guess worded like a fact, THEN the model would build on a first-name list's mistake; "likely"
    // still helps it keep the agreement when the guess is right.
    @Test
    void run_genderOnlySuggested_theDraftIsShownItAsLikely() {
        final ScriptedChatModel model = replies(MASCULINE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withSuggestedGender(Gender.FEMALE));

        report(TranslationJobTestSupport.job(project, model).run());

        assertThat(userMessage(model.requests().getFirst()))
                .contains("Chisel — likely female")
                .doesNotContain("Chisel — female");
    }

    @Test
    void run_genderSetByThePerson_theDraftIsShownItAsAFact() {
        final ScriptedChatModel model = replies(FEMININE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withGender(Gender.FEMALE));

        report(TranslationJobTestSupport.job(project, model).run());

        assertThat(userMessage(model.requests().getFirst()))
                .contains("Chisel — female")
                .doesNotContain("likely");
    }

    // A gender the book verified (a pronoun count, a model answer its windows show) is enforced like the person's.
    @Test
    void run_womanVerified_earnsOneDirectedFix() {
        final ScriptedChatModel model = replies(MASCULINE, FEMININE);
        final TestProject project = chisel(QualityDial.FAST, entry -> entry.withInferredGender(Gender.FEMALE));

        report(TranslationJobTestSupport.job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentPath.REPAIRED, FEMININE);
    }

    @Test
    void run_reviewerEditsTowardAVerifiedGender_isApplied() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(DRAFT, answer(targetReply(MASCULINE)))
                .answerTo(REVIEW, answer(GENDER_EDIT));
        final TestProject project = chisel(QualityDial.BALANCED, entry -> entry.withInferredGender(Gender.FEMALE));

        report(TranslationJobTestSupport.job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, REVIEW);
        assertThat(stored(project, "Book.txt:0").machineTarget()).isEqualTo(FEMININE);
    }

    private TestProject chisel(final QualityDial dial, final UnaryOperator<GlossaryEntry> gender) {
        final Path book = TestBooks.txt(tempDir.resolve("Book.txt"), SOURCE);
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
