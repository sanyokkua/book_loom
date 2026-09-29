package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_ECHO;
import static ua.bookloom.pipeline.ChunkRunFixtures.DOOR_TARGET;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.FIX;
import static ua.bookloom.pipeline.ChunkRunFixtures.JUDGE;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.judged;
import static ua.bookloom.pipeline.ChunkRunFixtures.shown;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.FlaggedSegment;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.AlsoTranslate;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.FootnotePolicy;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Register;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.UnitPolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/** The reply precedence of design D3 and the acceptance rule of D8, seen through a whole run. */
class TranslationJobQualityTest {

    private static final String MISSING_TOKEN = "HE OPENED THE ⟦g0⟧OLD DOOR.";
    private static final String TOKENS = "⟦g0⟧ ⟦g1⟧";

    @TempDir
    private Path tempDir;

    @Test
    void run_missingTokenFast_flagsAfterThreeCalls() {
        final ScriptedChatModel model = replies(MISSING_TOKEN, MISSING_TOKEN, MISSING_TOKEN, "Вона пішла.");
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe left."),
                brief("en", "uk"));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, "placeholder-repair", FIX, DRAFT);
        assertThat(userMessage(model.requests().get(1))).contains(TOKENS);
        assertThat(userMessage(model.requests().get(2))).contains(TOKENS);
        assertThat(userMessage(model.requests().get(3))).contains(shown("She left."));
        assertFlaggedWithMarkup(stored(project, "Book.md:0"));
    }

    @Test
    void run_placeholderFailureBalanced_goesToSelfHeal() {
        final String reply = "Він відчинив ⟦g0⟧старі двері.";
        final ScriptedChatModel model = replies(reply, reply, reply, reply);
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door."),
                brief("en", "uk", QualityDial.BALANCED));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, "placeholder-repair", FIX, FIX);
        assertThat(userMessage(model.requests().get(2))).contains(TOKENS);
        assertThat(userMessage(model.requests().get(3))).contains(TOKENS);
        assertFlaggedWithMarkup(stored(project, "Book.md:0"));
    }

    @Test
    void run_refusalFast_isRepaired() {
        final ScriptedChatModel model = replies("I'm sorry, but I can't translate this text.", DOOR_TARGET);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk"));

        report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.REPAIRED, DOOR_TARGET);
    }

    @Test
    void run_blankTarget_flaggedAfterTwoCalls() {
        final ScriptedChatModel model = replies("", "  ");
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk"));

        final JobReport report = report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, "structural-repair");
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.validation));
        assertThat(stored(project, "Book.txt:0").findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .containsExactly(tuple("validation", Severity.HIGH, "reply"));
    }

    @Test
    void run_cutOffDirectedFix_keepsEarlierTarget() {
        final ScriptedChatModel model = replies(DOOR_ECHO)
                .answer(Result.ok(new ChatResponse(TranslationJobTestSupport.targetReply("ВІН"), FinishReason.LENGTH)))
                .answerTo(JUDGE, judged());
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.BALANCED));

        final JobReport report = report(job(project, model).run());

        assertThat(formats(model)).containsExactly(DRAFT, JUDGE, FIX);
        assertThat(report.flaggedSegments()).containsExactly(new FlaggedSegment("Book.txt:0", ErrorCode.validation));
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, DOOR_ECHO);
    }

    // Run as the command line runs, with no pause point: Assisted and Manual would otherwise wait on the flag.
    @ParameterizedTest
    @EnumSource(ReviewMode.class)
    void run_pseudoModel_flagsTheEchoInEveryMode(final ReviewMode mode) {
        final CountingModel model = new CountingModel(new PseudoChatModel(new ObjectMapper()));
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.BALANCED));
        final TranslationJobImpl translation = job(project, model, mode);
        translation.pauseAt(Set.of());

        report(translation.run());

        assertThat(model.formats()).containsExactly(DRAFT, JUDGE, FIX, FIX);
        assertThat(stored(project, "Book.txt:0"))
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget, SegmentRecord::repairRounds)
                .containsExactly(SegmentStatus.FLAGGED, DOOR_ECHO, 2);
    }

    @Test
    void run_draft_statesItsOutputAllowance() {
        final ScriptedChatModel model = replies(DOOR_TARGET).answerTo(JUDGE, judged());
        final TestProject project = project(ChunkRunFixtures.door(tempDir), brief("en", "uk", QualityDial.BALANCED));

        report(job(project, model).run());

        assertThat(model.requests())
                .extracting(ChatRequest::expectedOutputTokens, ChatRequest::maxOutputTokens)
                .containsExactly(tuple(16, 64), tuple(null, null));
    }

    @Test
    void run_styleSheet_reachesTheDraft() {
        final ScriptedChatModel model = replies(DOOR_TARGET);
        final TestProject project = project(ChunkRunFixtures.door(tempDir), seaAdventure());

        report(job(project, model).run());

        assertThat(model.requests().getFirst().messages().getFirst().content()).contains("Genre: Sea adventure");
    }

    private static void assertFlaggedWithMarkup(final SegmentRecord record) {
        assertThat(record)
                .extracting(SegmentRecord::status, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.FLAGGED, null);
        assertThat(record.findings())
                .extracting(QaFinding::kind, QaFinding::severity)
                .contains(tuple("markup", Severity.HIGH));
    }

    private static BookBrief seaAdventure() {
        return new BookBrief(
                "en",
                "uk",
                "Sea adventure",
                Register.NEUTRAL,
                null,
                null,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.KEEP,
                55,
                AlsoTranslate.defaults(),
                QualityDial.FAST);
    }

    /** The real pseudo model, with the response format of every request it answered kept in order. */
    private static final class CountingModel implements ChatModel {

        private final ChatModel delegate;
        private final List<String> formats = new CopyOnWriteArrayList<>();

        CountingModel(final ChatModel delegate) {
            this.delegate = delegate;
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            formats.add(
                    Objects.requireNonNull(request.responseFormat(), "format").name());
            return delegate.chat(request);
        }

        List<String> formats() {
            return List.copyOf(formats);
        }
    }
}
