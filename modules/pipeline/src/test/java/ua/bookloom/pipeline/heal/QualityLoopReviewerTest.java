package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.calls;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.readable;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.segment;
import static ua.bookloom.pipeline.heal.QualityLoopTestSupport.targetReply;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.ScriptedChatModel;

/**
 * The reviewer's answer through the real quality loop, the acceptance table of
 * {@code specs/quality-gates/spec.md} "Review each chunk once per pass when the quality dial enables the reviewer":
 * what the reviewer asks for changes the book only when the code can verify it.
 */
class QualityLoopReviewerTest {

    private static final String DRAFT = "Він відчинив старі дверзі.";
    private static final String FIXED = "Він відчинив старі двері.";
    private static final String CLEAN_DRAFT = "Він відчинив старі двері.";
    private static final String LATIN_E = "двeрі";
    private static final String OK = "{\"results\":[{\"id\":\"s1\",\"status\":\"ok\"}]}";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    private static String edits(final String... criterionQuoteReplacement) {
        final StringBuilder list = new StringBuilder();
        for (int index = 0; index < criterionQuoteReplacement.length; index += 3) {
            list.append(index == 0 ? "" : ",")
                    .append("{\"criterion\":\"")
                    .append(criterionQuoteReplacement[index])
                    .append("\",\"quote\":\"")
                    .append(criterionQuoteReplacement[index + 1])
                    .append("\",\"replacement\":\"")
                    .append(criterionQuoteReplacement[index + 2])
                    .append("\"}");
        }
        return "{\"results\":[{\"id\":\"s1\",\"status\":\"edits\",\"edits\":[" + list + "]}]}";
    }

    private static String rewrite(final String text) {
        return "{\"results\":[{\"id\":\"s1\",\"status\":\"rewrite\",\"rewrite\":\"" + text + "\"}]}";
    }

    private ChunkDecider start(final String draft, final ScriptedChatModel model, final QualityDial dial) {
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), draft, draft, draft, null);
        return Objects.requireNonNull(loop.start(
                        List.of(outcome),
                        QualityLoopFixtures.settings(ReviewMode.UNATTENDED, dial),
                        QualityLoopFixtures.PASSTHROUGH_GATE,
                        calls(model))
                .data());
    }

    private SegmentOutcome decide(final String draft, final ScriptedChatModel model) {
        return Objects.requireNonNull(
                start(draft, model, QualityDial.BALANCED).nextDecision().data());
    }

    private static List<String> formats(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(request -> Objects.requireNonNull(request.responseFormat()).name())
                .toList();
    }

    @Test
    void nextDecision_reviewerSaysOk_acceptsTheDraftAsItIs() {
        final SegmentOutcome decided = decide(CLEAN_DRAFT, new ScriptedChatModel().answer(readable(OK)));

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(decided.machineTarget()).isEqualTo(CLEAN_DRAFT);
        assertThat(decided.repairRounds()).isZero();
    }

    @Test
    void nextDecision_idiomNitpickAsAStyleEdit_acceptsTheDraftAndNotesItLow() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(edits("style", "старі двері", "давні двері")));

        final SegmentOutcome decided = decide(CLEAN_DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(CLEAN_DRAFT);
        assertThat(decided.findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple("style", Severity.LOW, "reviewer"));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_coinedWordWithAQuote_appliesTheEditAndRecordsIt() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(edits("invented-word", "дверзі", "двері")));

        final SegmentOutcome decided = decide(DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(FIXED);
        assertThat(decided.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(decided.repairRounds()).isEqualTo(1);
        assertThat(decided.findings())
                .flatMap(finding -> AppliedEdit.from(finding).stream().toList())
                .containsExactly(new AppliedEdit("invented-word", "дверзі", "двері"));
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_hallucinatedQuote_isIgnoredAndTheDraftIsAccepted() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(edits("meaning", "відімкнув ключем", "відчинив")));

        final SegmentOutcome decided = decide(CLEAN_DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(CLEAN_DRAFT);
        assertThat(decided.repairRounds()).isZero();
        assertThat(decided.findings())
                .noneMatch(finding -> AppliedEdit.from(finding).isPresent());
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_genderPairOfEdits_appliesBothInOrder() {
        final String draft = "Вона втомився, але йшов далі.";
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(
                        edits("gender", "Вона втомився", "Вона втомилася", "gender", "йшов далі", "йшла далі")));

        final SegmentOutcome decided = decide(draft, model);

        assertThat(decided.machineTarget()).isEqualTo("Вона втомилася, але йшла далі.");
        assertThat(decided.findings())
                .filteredOn(finding -> AppliedEdit.from(finding).isPresent())
                .hasSize(2);
    }

    // The edit's quote is in the text, but its result mixes alphabets inside a word, which a hard check refuses: the
    // reviewer's finding stands as evidence and the segment gets exactly one directed fix that names the quote.
    @Test
    void nextDecision_editWhoseResultMixesScripts_makesOneDirectedFixNamingTheQuoteAndAcceptsItsResult() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(edits("invented-word", "дверзі", LATIN_E)))
                .answer(readable(targetReply(FIXED)));

        final SegmentOutcome decided = decide(DRAFT, model);

        assertThat(formats(model)).containsExactly("reviewer", "directed-fix");
        assertThat(model.requests().get(1).messages().getLast().content()).contains("дверзі", "invented-word");
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(FIXED);
        assertThat(decided.repairRounds()).isEqualTo(1);
    }

    @Test
    void nextDecision_directedFixThatLeavesTheQuote_flagsWithTheEvidenceAndKeepsTheDraft() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(edits("invented-word", "дверзі", LATIN_E)))
                .answer(readable(targetReply(DRAFT)));

        final SegmentOutcome decided = decide(DRAFT, model);

        assertThat(formats(model)).containsExactly("reviewer", "directed-fix");
        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(DRAFT);
        assertThat(decided.findings())
                .filteredOn(finding -> finding.raisedBy().equals("reviewer"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.kind()).isEqualTo("invented-word");
                    assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
                    assertThat(finding.note()).contains("дверзі", "introduces a blocking defect");
                });
    }

    @Test
    void nextDecision_directedFixAnswersAuth_endsTheStepAndARetryRepeatsOnlyTheFix() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(edits("invented-word", "дверзі", LATIN_E)))
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")))
                .answer(readable(targetReply(FIXED)));
        final ChunkDecider decider = start(DRAFT, model, QualityDial.BALANCED);

        final Result<SegmentOutcome> failed = decider.nextDecision();
        final SegmentOutcome decided =
                Objects.requireNonNull(decider.nextDecision().data());

        assertThat(Objects.requireNonNull(failed.error()).code()).isEqualTo(ErrorCode.auth);
        assertThat(formats(model)).containsExactly("reviewer", "directed-fix", "directed-fix");
        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(FIXED);
    }

    @Test
    void nextDecision_rewritePassingTheChecks_isAccepted() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(rewrite("Він відчинив старі дубові двері.")));

        final SegmentOutcome decided = decide(DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив старі дубові двері.");
        assertThat(decided.repairRounds()).isEqualTo(1);
    }

    @Test
    void nextDecision_rewriteWithAMixedScriptWord_keepsTheDraftAndFlagsWithTheEvidence() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answer(readable(rewrite("Він відчинив старі дубові " + LATIN_E + ".")));

        final SegmentOutcome decided = decide(CLEAN_DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(CLEAN_DRAFT);
        assertThat(decided.repairRounds()).isZero();
        assertThat(decided.findings())
                .filteredOn(finding -> finding.kind().equals("rewrite"))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.severity()).isEqualTo(Severity.MEDIUM);
                    assertThat(finding.note()).contains("draft was kept");
                });
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void nextDecision_reviewerTimedOutTwice_flagsWithReviewerUnavailableAndTheTimeout() {
        final AppError timeout = AppError.of(ErrorCode.timeout, "No answer", "the reviewer did not answer");
        final ScriptedChatModel model = new ScriptedChatModel().answerTimes(2, Result.err(timeout));

        final SegmentOutcome decided = decide(CLEAN_DRAFT, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.machineTarget()).isEqualTo(CLEAN_DRAFT);
        assertThat(Objects.requireNonNull(decided.flagReason()).code()).isEqualTo(ErrorCode.timeout);
        assertThat(decided.findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple("reviewer-unavailable", Severity.MEDIUM, "reviewer"));
    }

    @Test
    void nextDecision_unreadableReply_flagsWithReviewerUnavailableAndNoReason() {
        final SegmentOutcome decided =
                decide(CLEAN_DRAFT, new ScriptedChatModel().answer(readable("Looks good to me!")));

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.flagReason()).isNull();
        assertThat(decided.findings()).extracting(QaFinding::kind).contains("reviewer-unavailable");
    }

    @Test
    void nextDecision_replyThatNamesNoSegment_readsTheSegmentAsOk() {
        final SegmentOutcome decided =
                decide(CLEAN_DRAFT, new ScriptedChatModel().answer(readable("{\"results\":[]}")));

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    @Test
    void nextDecision_reviewerAnswersAuth_endsTheStartWithThatError() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(Result.err(AppError.of(ErrorCode.auth, "Rejected", "the key was refused")));
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                segment(), QualityLoopTestSupport.SOURCE, List.of(), CLEAN_DRAFT, CLEAN_DRAFT, CLEAN_DRAFT, null);

        final Result<ChunkDecider> started = loop.start(
                List.of(outcome),
                QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED),
                QualityLoopFixtures.PASSTHROUGH_GATE,
                calls(model));

        assertThat(Objects.requireNonNull(started.error()).code()).isEqualTo(ErrorCode.auth);
    }

    @Test
    void nextDecision_maxDial_makesASecondPassWhoseEditsFollowTheFirstPassEdits() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(edits("invented-word", "дверзі", "двері")))
                .answer(readable(edits("meaning", "старі", "давні")));

        final SegmentOutcome decided = Objects.requireNonNull(
                start(DRAFT, model, QualityDial.MAX).nextDecision().data());

        assertThat(formats(model)).containsExactly("reviewer", "reviewer");
        assertThat(model.requests().get(1).messages().getLast().content()).contains("second pass");
        assertThat(decided.machineTarget()).isEqualTo("Він відчинив давні двері.");
        assertThat(decided.findings())
                .flatMap(finding -> AppliedEdit.from(finding).stream().toList())
                .extracting(AppliedEdit::criterion)
                .containsExactly("invented-word", "meaning");
    }

    @Test
    void nextDecision_maxDialSecondPassUnreadable_keepsTheFirstPassEdits() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(edits("invented-word", "дверзі", "двері")))
                .answer(readable("not json"));

        final SegmentOutcome decided = Objects.requireNonNull(
                start(DRAFT, model, QualityDial.MAX).nextDecision().data());

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.machineTarget()).isEqualTo(FIXED);
    }

    @Test
    void nextDecision_fastDial_acceptsOnTheChecksAloneWithNoReviewerCall() {
        final ScriptedChatModel model = new ScriptedChatModel();

        final SegmentOutcome decided = Objects.requireNonNull(
                start(CLEAN_DRAFT, model, QualityDial.FAST).nextDecision().data());

        assertThat(decided.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(decided.confidence()).isGreaterThan(0.0);
        assertThat(model.requests()).isEmpty();
    }
}
