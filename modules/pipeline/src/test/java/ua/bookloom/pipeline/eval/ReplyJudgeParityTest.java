package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/** The same recorded reply reaches the same verdict through the eval's judge and through the job's quality loop. */
class ReplyJudgeParityTest {

    private static final String SOURCE = "He opened the old door.";
    private static final ModelCalls NO_MODEL = (kind, segmentId, request) ->
            Result.err(AppError.of(ErrorCode.internal, "no model", "the parity test sends no call"));

    private static EvalProject project() {
        return EvalProject.of(EvalProject.Setup.single("en", "uk", List.of(), EvalContext.none(), SOURCE), NO_MODEL);
    }

    /** What the job's quality loop decides for the reply, with no repair round and no reviewer. */
    private static SegmentStatus jobStatus(final EvalProject project, final String reply) {
        final DraftOutcome drafted = project.translator()
                .adopt(project.segment(0), project.mask(0), reply)
                .orElseThrow();
        final PromptTemplates templates = new PromptTemplates();
        final QualityLoop loop = new QualityLoop(
                new ReviewerCall(templates, new ReviewReplyParser(new com.fasterxml.jackson.databind.ObjectMapper())),
                new EditApplier(new EditVerifier()),
                new DirectedFix(templates, new DraftReplyParser(new com.fasterxml.jackson.databind.ObjectMapper())));
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED,
                new DialParameters(0, 0, 0, false, false, 4),
                project.frame(),
                project.settings().names(),
                List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(drafted), settings, project.gate(), NO_MODEL).data());
        final SegmentOutcome decided =
                Objects.requireNonNull(decider.nextDecision().data());
        return decided.status();
    }

    @Test
    void judge_cleanTranslation_isAcceptedAsTheJobAccepts() {
        final String reply = "Він відчинив старі двері.";

        assertThat(ReplyJudge.judge(project(), 0, reply).accepted()).isTrue();
        assertThat(jobStatus(project(), reply)).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void judge_replyEndingWithAStrayBraceAndQuote_isBlockedAsTheJobFlagsIt() {
        final String reply = "Він відчинив старі двері.\"}";

        assertThat(ReplyJudge.judge(project(), 0, reply).accepted()).isFalse();
        assertThat(jobStatus(project(), reply)).isEqualTo(SegmentStatus.FLAGGED);
    }

    @Test
    void judge_untranslatedEcho_isBlockedAsTheJobFlagsIt() {
        assertThat(ReplyJudge.judge(project(), 0, SOURCE).accepted()).isFalse();
        assertThat(jobStatus(project(), SOURCE)).isEqualTo(SegmentStatus.FLAGGED);
    }
}
