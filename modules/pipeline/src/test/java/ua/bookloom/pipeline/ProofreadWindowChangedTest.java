package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.ProofreadProjects.decideAll;
import static ua.bookloom.pipeline.ProofreadProjects.lines;
import static ua.bookloom.pipeline.ProofreadProjects.ok;
import static ua.bookloom.pipeline.ProofreadProjects.openDeferrals;
import static ua.bookloom.pipeline.ProofreadProjects.update;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.review.SegmentActions;

/**
 * A segment the person acts on while a proofread window's call waits (design D15 "Resume"): the answer written for the
 * text that is gone is stored for none, the window is asked again from the text now stored, and a segment that changes
 * again leaves the window unread. Invented en→uk lines, every segment decided before the run.
 */
class ProofreadWindowChangedTest {

    private static final String PROOFREAD = "proofread";
    private static final int SIXTEEN = 16;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    private static final String BELL = "Book.md:3";
    private static final String BELL_BROKEN = "Дзвін ударив 3 разы на світанку.";
    private static final String BELL_MENDED = "Дзвін ударив 3 разів на світанку.";
    private static final QaFinding BELL_FINDING =
            new QaFinding("gender", Severity.MEDIUM, "A word to check.", "name-gender");

    // The person reverts their edit while the window's call waits: the answer written for the edit is not stored, the
    // call is made again from the reverted text, and that answer becomes the machine target.
    @Test
    void window_revertedWhileTheCallWaited_redoesTheCallFromTheRevertedText() {
        final TestProject project = markedBell(QualityDial.BALANCED);
        update(
                project,
                BELL,
                record -> record.withStatus(SegmentStatus.REVISED).withUserTarget(BELL_BROKEN, BELL_BROKEN));
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(PROOFREAD, rewrite(BELL, "Дзвін ударив 3 рази на світанку."))
                .answerTo(PROOFREAD, rewrite(BELL, BELL_MENDED));

        runActingDuringCalls(project, model, List.of(() -> ok(actions(project).revert(project.id(), BELL))));

        assertThat(proofreadCalls(model)).isEqualTo(2);
        assertThat(stored(project, BELL))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget)
                .containsExactly(BELL_MENDED, null);
    }

    // The person saves an edit while the call waits: the answer is redone from the edit and waits as a proposal, the
    // edit itself untouched.
    @Test
    void window_editedWhileTheCallWaited_redoesTheCallFromTheEditAsAProposal() {
        final TestProject project = markedBell(QualityDial.BALANCED);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(PROOFREAD, rewrite(BELL, BELL_MENDED))
                .answerTo(PROOFREAD, rewrite(BELL, "Дзвін ударив 3 рази на світанку тут."));

        runActingDuringCalls(
                project,
                model,
                List.of(() ->
                        ok(actions(project).saveEdit(project.id(), BELL, "Дзвін ударив 3 разы на світанку тут."))));

        assertThat(proofreadCalls(model)).isEqualTo(2);
        assertThat(stored(project, BELL))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget)
                .containsExactly(BELL_BROKEN, "Дзвін ударив 3 разы на світанку тут.");
        assertThat(openDeferrals(project))
                .extracting(Deferral::reason, Deferral::proposal)
                .containsExactly(tuple(DeferralReason.PROOFREAD, "Дзвін ударив 3 рази на світанку тут."));
    }

    // The segment changes during both calls: the window is redone once only, neither answer is stored, and the window
    // is left unread.
    @Test
    void window_changedDuringBothCalls_storesNeitherAnswerAfterOneRedo() {
        final TestProject project = markedBell(QualityDial.BALANCED);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(PROOFREAD, rewrite(BELL, BELL_MENDED))
                .answerTo(PROOFREAD, rewrite(BELL, BELL_MENDED));

        runActingDuringCalls(
                project,
                model,
                List.of(
                        () -> ok(actions(project).saveEdit(project.id(), BELL, "Дзвін ударив 3 разы на світанку тут.")),
                        () -> ok(actions(project)
                                .saveEdit(project.id(), BELL, "Дзвін ударив 3 разы на світанку там."))));

        assertThat(proofreadCalls(model)).isEqualTo(2);
        assertThat(stored(project, BELL))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::userTarget, SegmentRecord::proofread)
                .containsExactly(BELL_BROKEN, "Дзвін ударив 3 разы на світанку там.", null);
        assertThat(openDeferrals(project)).noneMatch(deferral -> deferral.proposal() != null);
    }

    /** Sixteen decided lines, the one at {@code BELL} holding a Russian letter and a repair-level finding. */
    private TestProject markedBell(final QualityDial dial) {
        final TestProject project = project(
                TestBooks.markdown(
                        tempDir.resolve("Book.md"),
                        "Hale went away.\n\n"
                                + lines(SIXTEEN - 1, line -> "The bell rang " + (line + 1) + " times at dawn.")),
                brief("en", "uk", dial));
        decideAll(project, SIXTEEN, line -> "Дзвін ударив " + line + " разів на світанку.");
        ProofreadProjects.decide(project, "Book.md:0", "Хейл пішов.");
        update(
                project,
                BELL,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(BELL_BROKEN, BELL_BROKEN)
                        .withFindings(List.of(BELL_FINDING)));
        return project;
    }

    private static Result<ChatResponse> rewrite(final String id, final String target) {
        return Result.ok(new ChatResponse(
                "{\"items\":[{\"id\":\"" + id + "\",\"target\":\"" + target + "\"}]}", FinishReason.STOP));
    }

    private static SegmentActions actions(final TestProject project) {
        return new SegmentActions(
                project.documents(),
                project.stores().openProjects(),
                project.stores().segments(),
                project.deferrals());
    }

    // Each action runs on the job's thread as the call at its position is made, which is as the person acting while
    // that call waits.
    private static void runActingDuringCalls(
            final TestProject project, final ScriptedChatModel model, final List<Runnable> actions) {
        final AtomicInteger calls = new AtomicInteger();
        final ChatModel acting = new ChatModel() {
            @Override
            public Result<ChatResponse> chat(final ChatRequest request) {
                final int index = PROOFREAD.equals(ScriptedChatModel.callName(request))
                        ? calls.getAndIncrement()
                        : actions.size();
                if (index < actions.size()) {
                    actions.get(index).run();
                }
                return model.chat(request);
            }

            @Override
            public ModelProfile profile() {
                return model.profile();
            }
        };
        assertThat(report(job(project, acting).run()).end()).isEqualTo(JobState.COMPLETED);
    }

    private static long proofreadCalls(final ScriptedChatModel model) {
        return model.requests().stream()
                .filter(request -> PROOFREAD.equals(ScriptedChatModel.callName(request)))
                .count();
    }
}
