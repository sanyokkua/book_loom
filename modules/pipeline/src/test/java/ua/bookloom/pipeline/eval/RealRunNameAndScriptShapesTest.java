package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.checks.CheckFinding;
import ua.bookloom.pipeline.checks.TextChecks;
import ua.bookloom.pipeline.eval.ReplyJudge.Verdict;

/**
 * Two replies the Oct 9 runs accepted and read wrongly (15h.E1, invented text): a place name the draft lost (judged by the run's own draft step and judge) and a source
 * Latin run left in a Cyrillic target (read by the deterministic text checks).
 */
class RealRunNameAndScriptShapesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static EvalProject project(final String source, final List<EvalTerm> glossary) {
        return EvalProject.of(
                EvalProject.Setup.single("en", RunCorpus.TARGET_LANGUAGE, glossary, EvalContext.none(), source),
                (kind, segmentId, request) ->
                        Result.err(new AppError(ErrorCode.internal, "t", "no call", null, false, null)));
    }

    private static String reply(final String target) throws Exception {
        return MAPPER.writeValueAsString(java.util.Map.of("target", target));
    }

    // IF a lost place name were only an audit doubt, THEN the draft is kept without the place and nobody mends it.
    @Test
    void judgeReply_draftThatDroppedAGlossaryPlace_isNotKeptAsDrafted() throws Exception {
        final EvalTerm zurich = new EvalTerm("Zurich", "Цюріх", TermType.PLACE, Gender.UNKNOWN, false);
        final EvalProject project = project("They reached Zurich at dawn.", List.of(zurich));

        final Verdict verdict = ReplyJudge.judgeReply(project, 0, reply("Вони дісталися до міста на світанку."));

        assertThat(verdict.isKeptAsDrafted()).isFalse();
    }

    // IF a source Latin run left in a Cyrillic target were let through, THEN English words reach the exported book.
    @Test
    void run_latinRunOfTheSourceLeftInACyrillicTarget_hasABlockingFinding() {
        final List<CheckFinding> findings = TextChecks.run(
                "He read the Daily Courier on the train.", "Він читав Daily Courier у поїзді.", "en", "uk");

        assertThat(findings).anyMatch(CheckFinding::blocking);
    }
}
