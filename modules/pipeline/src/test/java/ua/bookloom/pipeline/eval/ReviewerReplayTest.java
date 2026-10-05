package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.AppliedEdit;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.AcceptanceRule;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftEvaluation;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.Polish;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.ReflectImprove;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewCriterion;
import ua.bookloom.pipeline.reviewer.ReviewEdit;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Replays the 15d.1 defect corpus through the new acceptance rule with no model. The log of the 8-hour run whose 170
 * flagged decisions the task names is no longer available, so the corpus stands in for it: the clean candidates the old
 * judge refused as false alarms are accepted untouched when the reviewer finds nothing; the defects the checks decide — a missing clause or word shows in the length, a stray quote or letter in the
 * text checks — are never accepted; and each defect only a model can see is changed into the clean text by the edits a reviewer would
 * ask for, verified and applied by the code. Every expected value is written out.
 */
class ReviewerReplayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GateFunction PASSTHROUGH =
            (segment, maskedReply) -> new GateResult.Restored(maskedReply, maskedReply);
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String NO_RESULTS = "{\"results\":[]}";

    private static QualityLoop loop() {
        final PromptTemplates templates = new PromptTemplates();
        final DraftReplyParser draftReplies = new DraftReplyParser(MAPPER);
        return new QualityLoop(
                new ReviewerCall(templates, new ReviewReplyParser(MAPPER)),
                new EditApplier(new EditVerifier()),
                new DirectedFix(templates, draftReplies),
                new ReflectImprove(templates, draftReplies, MAPPER),
                new Polish(templates, draftReplies));
    }

    private static LoopSettings settings() {
        return new LoopSettings(
                ReviewMode.UNATTENDED,
                DialParameters.of(QualityDial.BALANCED),
                FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
    }

    private static DefectCase corpusCase(final String id) {
        return EvalCorpus.defects().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DraftOutcome.Drafted drafted(final DefectCase corpusCase) {
        return new DraftOutcome.Drafted(
                PromptEvalRunner.segment(corpusCase.source()),
                corpusCase.source(),
                List.of(),
                corpusCase.candidate(),
                corpusCase.candidate(),
                corpusCase.candidate(),
                null);
    }

    private static SegmentOutcome decide(final DefectCase corpusCase, final String reviewerReply) {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(ua.bookloom.api.Result.ok(new ChatResponse(reviewerReply, FinishReason.STOP)));
        final ChunkDecider decider = Objects.requireNonNull(loop().start(
                        List.of(drafted(corpusCase)),
                        settings(),
                        PASSTHROUGH,
                        (kind, segmentId, request) -> model.chat(request))
                .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }

    private static String reply(final List<ReviewEdit> edits) {
        final ObjectNode item = MAPPER.createObjectNode().put("id", "s1").put("status", "edits");
        final ArrayNode array = item.putArray("edits");
        edits.forEach(edit -> array.addObject()
                .put("criterion", edit.criterion().wire())
                .put("quote", edit.quote())
                .put("replacement", edit.replacement()));
        return MAPPER.createObjectNode()
                .set("results", MAPPER.createArrayNode().add(item))
                .toString();
    }

    // The old judge refused every one of these faithful candidates; with nothing to say the reviewer accepts them all.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "garbled-word-clean",
                "mixed-script-clean",
                "balanced-quotes",
                "translated-clean",
                "idiom-clean",
                "name-clean",
                "gender-clean",
                "lexical-clean",
                "meaning-clean",
                "short-line-clean",
                "yes-clean",
                "mixed-script-look-alike-clean",
                "quotes-stray-close-clean",
                "leftover-english-paragraph-clean",
                "duplicate-word-clean",
                "spacing-clean",
                "heading-clean"
            })
    void replay_cleanCandidateTheJudgeRefused_isAcceptedUnchangedWhenTheReviewerFindsNothing(final String id) {
        final DefectCase clean = corpusCase(id);

        final SegmentOutcome decided = decide(clean, NO_RESULTS);

        assertThat(decided.status().name()).isEqualTo("ACCEPTED");
        assertThat(decided.machineTarget()).isEqualTo(clean.candidate());
        assertThat(decided.repairRounds()).isZero();
    }

    // The idiom the judge nitpicked: a style remark is a note, never an edit and never a reason to refuse.
    @Test
    void replay_idiomWithAStyleNitpick_isAcceptedUnchangedWithALowNote() {
        final DefectCase idiom = corpusCase("idiom-clean");

        final SegmentOutcome decided = decide(
                idiom, reply(List.of(new ReviewEdit(ReviewCriterion.STYLE, "ллє як із відра", "ллє як із цебра"))));

        assertThat(decided.status().name()).isEqualTo("ACCEPTED");
        assertThat(decided.machineTarget()).isEqualTo("Дощ ллє як із відра.");
        assertThat(decided.findings()).extracting(finding -> finding.raisedBy()).contains("reviewer");
    }

    // The checks alone refuse these defects, whatever any model says.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "mixed-script",
                "unbalanced-quotes",
                "english-accepted",
                "quotes-missing-close",
                "quotes-stray-close",
                "leftover-english-paragraph",
                "mixed-script-look-alike",
                "omission",
                "duplicate-word"
            })
    void replay_defectTheChecksDecide_isNeverAccepted(final String id) {
        final DefectCase defect = corpusCase(id);

        final boolean accepted = AcceptanceRule.accepts(DraftEvaluation.evaluate(drafted(defect), settings()), 0);

        assertThat(accepted).isFalse();
    }

    // A stray space before a full stop is a soft finding: the checks note it and the typography normaliser, not the
    // reviewer, is what removes it.
    @Test
    void replay_spaceBeforeAFullStop_isOnlyALowFindingTheRuleLetsThrough() {
        final DefectCase spacing = corpusCase("spacing-before-stop");

        final boolean accepted = AcceptanceRule.accepts(DraftEvaluation.evaluate(drafted(spacing), settings()), 0);

        assertThat(accepted).isTrue();
    }

    static Stream<Arguments> editedDefects() {
        return Stream.of(
                Arguments.of(
                        "garbled-word",
                        List.of(new ReviewEdit(ReviewCriterion.INVENTED_WORD, "лісфвуту", "лісу")),
                        "Він швидко побіг до лісу."),
                Arguments.of(
                        "gender-slip",
                        List.of(
                                new ReviewEdit(ReviewCriterion.GENDER, "Вона втомився", "Вона втомилася"),
                                new ReviewEdit(ReviewCriterion.GENDER, "йшов далі", "йшла далі")),
                        "Вона втомилася, але йшла далі."),
                Arguments.of(
                        "lexical-drift",
                        List.of(new ReviewEdit(ReviewCriterion.TERMINOLOGY, "Фокусник", "Чарівник")),
                        "Чарівник підняв свій посох. Чарівник заговорив."),
                Arguments.of(
                        "meaning-error",
                        List.of(new ReviewEdit(
                                ReviewCriterion.MEANING,
                                "Король народився, а його син втратив трон.",
                                "Король помер, і його син посів трон.")),
                        "Король помер, і його син посів трон."));
    }

    // Each defect only a model can see ends as the clean text once the edits are verified and applied.
    @ParameterizedTest
    @MethodSource("editedDefects")
    void replay_defectOnlyAModelSees_endsAsTheCleanTextOnceTheReviewersEditsAreVerified(
            final String id, final List<ReviewEdit> edits, final String expected) {
        final SegmentOutcome decided = decide(corpusCase(id), reply(edits));

        assertThat(decided.status().name()).isEqualTo("ACCEPTED");
        assertThat(decided.machineTarget()).isEqualTo(expected);
    }

    @Test
    void replay_editedDefect_recordsEachAppliedEditForTheReviewList() {
        final SegmentOutcome decided = decide(
                corpusCase("garbled-word"),
                reply(List.of(new ReviewEdit(ReviewCriterion.INVENTED_WORD, "лісфвуту", "лісу"))));

        assertThat(decided.findings())
                .flatMap(finding -> AppliedEdit.from(finding).stream().toList())
                .containsExactly(new AppliedEdit("invented-word", "лісфвуту", "лісу"));
    }
}
