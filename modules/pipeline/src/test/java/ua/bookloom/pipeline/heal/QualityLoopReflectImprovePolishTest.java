package ua.bookloom.pipeline.heal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * {@link QualityLoop}/{@link SegmentHealer}: the vague-concern path — reflect then improve, with a polish pass only
 * for an improved target inside {@link Borderline}'s window — and a repaired target judged again on its own
 * ({@code specs/quality-gates/spec.md} "Repair a failing segment within the dial's repair budget before flagging
 * it" — "A near miss is polished", "A repaired segment is judged again on its own").
 *
 * <p>The improved-target strings below were chosen by hand so the real checks land on either side of the polish
 * window [τ-0.05, τ): both share the script margin 0.5 (7 of 10 letters Cyrillic) and echo/glossary/repetition at
 * full margin (1.0); only the length margin — hence confidence — differs. τ = Assisted's 0.75.
 */
class QualityLoopReflectImprovePolishTest {

    /** Confidence 0.7098086124401916 — inside [0.70, 0.75): {@link Borderline#isBorderline} holds, polish runs. */
    private static final String BORDERLINE_IMPROVED_TARGET =
            "Привіту ABC 111111111111111111111111111111111111111111111111111111111";

    /** Confidence 0.6858851674641149 — below 0.70: not borderline, no polish call. */
    private static final String BELOW_WINDOW_IMPROVED_TARGET =
            "Привіту ABC 11111111111111111111111111111111111111111111111111111111";

    private static final String DRAFT_SOURCE =
            "The lighthouse stood on the rocky shore, its beam sweeping across the still dark water tonight.";
    private static final String GOOD_DRAFT_TARGET =
            "Маяк стояв на прибережній скелі, його промінь ковзав по темній нерухомій воді цієї ночі.";

    private final QualityLoop loop = QualityLoopFixtures.loop();

    @Test
    void nextDecision_improvedTargetInsideThePolishWindow_polishIsCalled() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(BORDERLINE_IMPROVED_TARGET)))
                .answer(readable(targetReply(BORDERLINE_IMPROVED_TARGET)));

        decide(oneRoundJudgedDial(), model);

        assertThat(model.requests()).hasSize(4);
        assertThat(responseFormatNames(model)).containsExactly("judge", "reflect", "improve", "polish");
    }

    @Test
    void nextDecision_improvedTargetBelowThePolishWindow_noPolishCall() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(BELOW_WINDOW_IMPROVED_TARGET)));

        decide(oneRoundJudgedDial(), model);

        assertThat(model.requests()).hasSize(3);
        assertThat(responseFormatNames(model)).containsExactly("judge", "reflect", "improve");
    }

    // Judge off, repair budget 2: the draft echoes its source (fails outright), round 1's directed fix echoes it
    // again, round 2's directed fix returns a real translation — ACCEPTED as repaired after exactly 2 rounds, not
    // counted as auto-accepted.
    @Test
    void nextDecision_acceptedInItsSecondRound_isRepairedWithTwoRoundsUsed() {
        final String echo = DRAFT_SOURCE.toUpperCase(Locale.ROOT);
        // The first fix changes the echo but still answers in English, so a second round is needed.
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(targetReply(DRAFT_SOURCE)))
                .answer(readable(targetReply(GOOD_DRAFT_TARGET)));
        final DialParameters dial = new DialParameters(2, 2, false, false, false, 4);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED, dial, QualityLoopFixtures.FRAME, NamePolicy.TRANSLITERATE, List.of());

        final Result<SegmentOutcome> decision = decide(settings, echo, model);

        final SegmentOutcome outcome = Objects.requireNonNull(decision.data());
        assertThat(outcome.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(outcome.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(outcome.repairRounds()).isEqualTo(2);
        assertThat(responseFormatNames(model)).containsExactly("directed-fix", "directed-fix");
    }

    // A round's target that passes its hard gates, fails no check and reaches τ is judged again alone — one call
    // holding only that pair, labelled s1 — and that verdict, not the chunk's, decides it.
    @Test
    void nextDecision_roundReachingTau_isJudgedAgainAloneAndDecidedByThatVerdict() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(GOOD_DRAFT_TARGET)))
                .answer(readable("{\"score\":0.9,\"verdict\":\"accept\"}"));

        final Result<SegmentOutcome> decision = decide(oneRoundJudgedDial(), model);

        assertThat(model.requests()).hasSize(4);
        final String reJudgeUser = model.requests().get(3).messages().getLast().content();
        assertThat(reJudgeUser).contains("s1").doesNotContain("s2");
        final SegmentOutcome outcome = Objects.requireNonNull(decision.data());
        assertThat(outcome.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(outcome.judgeScore()).isEqualTo(0.9);
    }

    // Unattended (τ=0.60), Balanced: the directed fix for a segment returns its own English source — 26 code
    // points, echo+script fail outright, confidence 0.65 ≥ τ — but failedOutright still blocks eligibility, so no
    // re-judge call is made for that round and the next round begins.
    @Test
    void nextDecision_directedFixReturnsTheEnglishSource_noRejudgeAndTheNextRoundBegins() {
        final String source = "He left the house at dawn.";
        assertThat(source).hasSize(26);
        final Segment segment = segmentNamed("Book.md:3", source);
        final String echo = source.toUpperCase(Locale.ROOT);
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment, source, List.of(), echo, echo, echo, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"score\":0.5,\"verdict\":\"revise\"}"))
                .answer(readable(targetReply(source)))
                .answer(readable(targetReply(source)));
        final LoopSettings settings = QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.BALANCED);
        final GateFunction gate = QualityLoopFixtures.PASSTHROUGH_GATE;

        final Result<SegmentOutcome> decision = Objects.requireNonNull(
                        loop.start(List.of(outcome), settings, gate, calls(model))
                                .data())
                .nextDecision();

        // Three calls total: the chunk judge, and two directed fixes — never a re-judge for the failing round.
        assertThat(model.requests()).hasSize(3);
        assertThat(responseFormatNames(model)).containsExactly("judge", "directed-fix", "directed-fix");
        assertThat(Objects.requireNonNull(decision.data()).status()).isEqualTo(SegmentStatus.FLAGGED);
    }

    // Assisted, Balanced: a chunk judged 0.90 with a medium omission finding on the second segment. The first
    // segment is accepted immediately; the second's directed fix returns a full-margin translation, is judged
    // again alone — one further judge request holding only that pair, labelled s1 — and its {"score":0.9,...}
    // accepts the segment as repaired after exactly one round.
    @Test
    void nextDecision_directedFixForAMediumOmissionFinding_isJudgedAgainAloneAndAccepted() {
        final DraftOutcome.Drafted first =
                gatedDraft(segmentNamed("Book.md:0", "It was quiet."), "It was quiet.", "Було тихо.");
        final String secondSource = "She walked with Hale into the dark hall.";
        final DraftOutcome.Drafted second =
                gatedDraft(segmentNamed("Book.md:1", secondSource), secondSource, "Вона пройшла в темну залу.");
        final String fixed = "Вона пройшла з Гейлом до темної зали.";
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(
                        readable(
                                "{\"score\":0.90,\"verdict\":\"revise\",\"findings\":"
                                        + "[{\"segmentId\":\"s2\",\"type\":\"omission\",\"severity\":\"medium\",\"note\":\"drops Hale\"}]}"))
                .answer(readable(targetReply(fixed)))
                .answer(readable("{\"score\":0.9,\"verdict\":\"accept\"}"));
        final LoopSettings settings = QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED);
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(first, second), settings, QualityLoopFixtures.PASSTHROUGH_GATE, calls(model))
                        .data());

        final SegmentOutcome firstDecision =
                Objects.requireNonNull(decider.nextDecision().data());
        final SegmentOutcome secondDecision =
                Objects.requireNonNull(decider.nextDecision().data());

        assertMediumOmissionScenarioDecisions(model, firstDecision, secondDecision);
    }

    /** A draft whose reply passed the gate unchanged: the masked form and the restored target are the reply. */
    private static DraftOutcome.Drafted gatedDraft(final Segment segment, final String source, final String reply) {
        return new DraftOutcome.Drafted(segment, source, List.of(), reply, reply, reply, null);
    }

    private static void assertMediumOmissionScenarioDecisions(
            final ScriptedChatModel model, final SegmentOutcome firstDecision, final SegmentOutcome secondDecision) {
        assertThat(firstDecision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(firstDecision.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(secondDecision.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(secondDecision.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(secondDecision.repairRounds()).isEqualTo(1);
        assertThat(model.requests()).hasSize(3);
        assertThat(Objects.requireNonNull(model.requests().get(1).responseFormat())
                        .name())
                .isEqualTo("directed-fix");
        final String rejudgeUser = model.requests().get(2).messages().getLast().content();
        assertThat(rejudgeUser).contains("[s1]").doesNotContain("[s2]");
    }

    // Max's real dial (repair budget 3, judge on): an echoing draft's first directed fix still echoes, the second
    // returns a real translation eligible for re-judge — accepted as repaired after exactly 2 rounds, well inside
    // the budget of 3.
    @Test
    void nextDecision_maxDialAcceptedInItsSecondRound_isRepairedWithTwoRoundsUsed() {
        final String echo = DRAFT_SOURCE.toUpperCase(Locale.ROOT);
        final DraftOutcome.Drafted outcome =
                new DraftOutcome.Drafted(segment(), DRAFT_SOURCE, List.of(), echo, echo, echo, null);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable("{\"score\":0.5,\"verdict\":\"revise\"}"))
                .answer(readable(targetReply(DRAFT_SOURCE)))
                .answer(readable(targetReply(GOOD_DRAFT_TARGET)))
                .answer(readable("{\"score\":0.95,\"verdict\":\"accept\"}"));
        final LoopSettings settings = QualityLoopFixtures.settings(ReviewMode.UNATTENDED, QualityDial.MAX);
        final GateFunction gate = QualityLoopFixtures.PASSTHROUGH_GATE;

        final Result<SegmentOutcome> decision = Objects.requireNonNull(
                        loop.start(List.of(outcome), settings, gate, calls(model))
                                .data())
                .nextDecision();

        final SegmentOutcome result = Objects.requireNonNull(decision.data());
        assertThat(result.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(result.path()).isEqualTo(SegmentPath.REPAIRED);
        assertThat(result.repairRounds()).isEqualTo(2);
    }

    // The real Balanced dial (not a custom one-round stand-in): a low chunk score with no finding routes to
    // reflect then improve.
    @Test
    void nextDecision_realBalancedDial_lowScoreWithNoFindingGoesToReflectThenImprove() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(GOOD_DRAFT_TARGET)))
                .answer(readable("{\"score\":0.95,\"verdict\":\"accept\"}"));
        final LoopSettings settings = QualityLoopFixtures.settings(ReviewMode.ASSISTED, QualityDial.BALANCED);

        final Result<SegmentOutcome> decision = decide(settings, model);

        assertThat(responseFormatNames(model)).containsExactly("judge", "reflect", "improve", "judge");
        assertThat(Objects.requireNonNull(decision.data()).status()).isEqualTo(SegmentStatus.ACCEPTED);
    }

    private static Segment segmentNamed(final String id, final String source) {
        return new Segment(
                id,
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                source,
                source,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, source.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    // B1: a polish call that flags at once must not discard the improved target Y it was polishing — Y already
    // passed every hard gate (a precondition of being borderline), so it becomes the machine target and Y's QA the
    // last evaluation, not the state the round started from.
    @Test
    void nextDecision_polishAnswersFlagNow_flagsWithTheImprovedTargetAsMachineTarget() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(BORDERLINE_IMPROVED_TARGET)))
                .answer(Result.ok(new ChatResponse("   ", FinishReason.STOP)));

        final Result<SegmentOutcome> decision = decide(oneRoundJudgedDial(), model);

        final SegmentOutcome outcome = Objects.requireNonNull(decision.data());
        assertThat(outcome.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcome.machineTarget()).isEqualTo(BORDERLINE_IMPROVED_TARGET);
        assertThat(outcome.maskedMachineTarget()).isEqualTo(BORDERLINE_IMPROVED_TARGET);
        assertThat(Objects.requireNonNull(outcome.flagReason()).code()).isEqualTo(ErrorCode.emptyCompletion);
    }

    // B1: a malformed polish reply wastes only the polish attempt — the round's target stays the improved one, so
    // the segment is decided (here: flagged, budget exhausted) on Y, not on nothing.
    @Test
    void nextDecision_polishAnswersMalformed_keepsTheImprovedTargetAndDecidesOnIt() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(BORDERLINE_IMPROVED_TARGET)))
                .answer(readable("{\"segments\":[]}"));

        final Result<SegmentOutcome> decision = decide(oneRoundJudgedDial(), model);

        final SegmentOutcome outcome = Objects.requireNonNull(decision.data());
        assertThat(outcome.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcome.machineTarget()).isEqualTo(BORDERLINE_IMPROVED_TARGET);
        assertThat(outcome.flagReason()).isNull();
        assertThat(outcome.repairRounds()).isEqualTo(1);
    }

    // B1: a polish reply that itself fails a hard gate (here, a refusal) must not replace Y — the round decides on
    // the improved target, not on the failing polished one.
    @Test
    void nextDecision_polishReturnsARefusal_keepsTheImprovedTargetOnHardGateFailure() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(readable(judgeScoredLow()))
                .answer(readable("{\"issues\":[]}"))
                .answer(readable(targetReply(BORDERLINE_IMPROVED_TARGET)))
                .answer(readable("{\"target\":\"I'm sorry, but I can't translate this text.\"}"));

        final Result<SegmentOutcome> decision = decide(oneRoundJudgedDial(), model);

        final SegmentOutcome outcome = Objects.requireNonNull(decision.data());
        assertThat(outcome.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(outcome.machineTarget()).isEqualTo(BORDERLINE_IMPROVED_TARGET);
    }

    private Result<SegmentOutcome> decide(final LoopSettings settings, final ScriptedChatModel model) {
        return decide(settings, GOOD_DRAFT_TARGET, model);
    }

    private Result<SegmentOutcome> decide(
            final LoopSettings settings, final String draftReply, final ScriptedChatModel model) {
        final Segment segment = segment();
        final List<DraftOutcome> outcomes = List.of(
                new DraftOutcome.Drafted(segment, DRAFT_SOURCE, List.of(), draftReply, draftReply, draftReply, null));
        final GateFunction gate = QualityLoopFixtures.PASSTHROUGH_GATE;
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(outcomes, settings, gate, calls(model)).data());
        return decider.nextDecision();
    }

    private static LoopSettings oneRoundJudgedDial() {
        return new LoopSettings(
                ReviewMode.ASSISTED,
                new DialParameters(2, 1, true, false, false, 4),
                QualityLoopFixtures.FRAME,
                NamePolicy.TRANSLITERATE,
                List.of());
    }

    private static List<String> responseFormatNames(final ScriptedChatModel model) {
        return model.requests().stream()
                .map(request -> Objects.requireNonNull(request.responseFormat()).name())
                .toList();
    }

    private static Segment segment() {
        return new Segment(
                "Book.md:0",
                "Book.md",
                0,
                SegmentKind.PARAGRAPH,
                DRAFT_SOURCE,
                DRAFT_SOURCE,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, DRAFT_SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static String judgeScoredLow() {
        return "{\"score\":0.60,\"verdict\":\"revise\",\"findings\":[],\"deferrals\":[]}";
    }

    private static String targetReply(final String target) {
        return "{\"target\":\"" + target + "\"}";
    }

    private static ModelCalls calls(final ScriptedChatModel model) {
        return (kind, segmentId, request) -> model.chat(request);
    }

    private static Result<ChatResponse> readable(final String content) {
        return Result.ok(new ChatResponse(content, FinishReason.STOP));
    }
}
