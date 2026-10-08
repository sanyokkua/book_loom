package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgress.Step;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.ExportRequest;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The export's consistency pass as the busy card shows it: each model call reaches the listener as a call snapshot
 * with its prompt part by part, the retry step counts segments, and the report says what each step came to.
 */
class ExportJobPassCallsTest {

    private static final String FIRST = "ch01.xhtml:0";
    private static final String SECOND = "ch01.xhtml:1";
    private static final ContextSnapshot SNAPSHOT = new ContextSnapshot(List.of(), List.of(), List.of(), null, "");

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;
    private String projectId;
    private final List<ExportProgress> progress = new CopyOnWriteArrayList<>();
    private final List<CallSnapshot> calls = new CopyOnWriteArrayList<>();
    private final ExportProgressListener listener = new ExportProgressListener() {
        @Override
        public void onProgress(final ExportProgress reached) {
            progress.add(reached);
        }

        @Override
        public void onCall(final CallSnapshot snapshot) {
            calls.add(snapshot);
        }
    };

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
        projectId = fixture.importBook(
                TestBooks.epubAtRoot(
                        tempDir.resolve("Book.epub"), List.of("ch01.xhtml"), List.of(List.of("One.", "Two."))),
                "en");
    }

    private ExportReport export(final ScriptedChatModel model) {
        final ExportRequest request =
                new ExportRequest(projectId, tempDir.resolve("Book.uk.epub"), false, Set.of(), true);
        return ok(ok(fixture.service().newExport(request, model, listener)).run());
    }

    // IF the export's calls were sent past the shown seam, THEN the busy card could not show what the model is asked.
    @Test
    void run_flaggedParagraph_itsNeighbourCallReachesTheListenerWithThePreviousParagraphFirst() {
        fixture.accept(projectId, FIRST, "Один.");
        fixture.flag(projectId, SECOND, "Два.");
        final ScriptedChatModel model = new ScriptedChatModel().answerTo("consistency", reply("Два."));

        export(model);

        assertThat(calls).extracting(CallSnapshot::state).containsExactly(CallState.WAITING, CallState.ANSWERED);
        final CallSnapshot answered = calls.getLast();
        assertThat(answered.kind()).isEqualTo(CallKind.REVISION);
        assertThat(answered.label()).isEqualTo("consistency");
        assertThat(answered.segments()).extracting(CallSegment::id).containsExactly(SECOND);
        assertThat(answered.sections())
                .filteredOn(section -> section.origin() == PromptSection.Origin.USER)
                .extracting(PromptSection::slot)
                .containsSubsequence("previous", "source", "text");
        assertThat(answered.reply()).isEqualTo("{\"target\":\"Два.\"}");
    }

    // IF the retry step counted calls, THEN a draft and its reviewer would move the bar twice for one segment.
    @Test
    void run_flaggedParagraphWithASnapshot_announcesTheRetryStepBySegment() {
        fixture.flag(projectId, FIRST, "Один.");
        fixture.decide(projectId, FIRST, record -> ReviewFixtures.withContext(record, SNAPSHOT));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo("draft", reply("Один."))
                .answerTo("reviewer", Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP)))
                .answerTo("consistency", reply("Один."));

        export(model);

        assertThat(progress)
                .filteredOn(reached -> reached.step() == Step.RETRY_DOUBTED)
                .containsExactly(
                        new ExportProgress(Step.RETRY_DOUBTED, 0, 1), new ExportProgress(Step.RETRY_DOUBTED, 1, 1));
    }

    // IF the report kept only the fixes, THEN a pass that looked at thirty paragraphs and changed none would say
    // nothing of what it did.
    @Test
    void run_flaggedParagraphTheModelLeavesAlone_reportsItUnchanged() {
        fixture.flag(projectId, SECOND, "Два.");
        final ScriptedChatModel model = new ScriptedChatModel().answerTo("consistency", reply("Два."));

        final ConsistencySummary summary = export(model).consistency();

        assertThat(summary.status()).isEqualTo(ConsistencySummary.Status.RAN);
        assertThat(summary.neighbourFixes()).isZero();
        assertThat(summary.checks().neighbourUnchanged()).isEqualTo(1);
    }

    private static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse("{\"target\":\"" + target + "\"}", FinishReason.STOP));
    }
}
