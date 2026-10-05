package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.dial.DialParameters;
import ua.bookloom.pipeline.heal.ChunkDecider;
import ua.bookloom.pipeline.heal.DirectedFix;
import ua.bookloom.pipeline.heal.DraftOutcome;
import ua.bookloom.pipeline.heal.GateFunction;
import ua.bookloom.pipeline.heal.GateResult;
import ua.bookloom.pipeline.heal.LoopSettings;
import ua.bookloom.pipeline.heal.QualityLoop;
import ua.bookloom.pipeline.heal.SegmentOutcome;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftReplyParser;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.EditVerifier;
import ua.bookloom.pipeline.reviewer.ReviewReplyParser;
import ua.bookloom.pipeline.reviewer.ReviewerCall;

/**
 * Replays the 15d.1 defects the checks refuse through the repair path with a model whose every fix makes the text
 * worse (a Latin letter in a Cyrillic word and an unclosed guillemet): no segment ends with anything but its first
 * candidate's findings, because a step that does not lower the blocker set is discarded. Expected values are written
 * out ({@code specs/quality-gates/spec.md} "Keep the best candidate through the repair path").
 */
class RepairReplayTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GateFunction PASSTHROUGH =
            (segment, maskedReply) -> new GateResult.Restored(maskedReply, maskedReply);
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String WORSE_FIX = "{\"target\":\"Він відчиниw «двері\"}";

    static Stream<Arguments> defects() {
        return Stream.of(
                Arguments.of("mixed-script", List.of("script-purity"), false),
                Arguments.of("unbalanced-quotes", List.of("quote-balance"), false),
                Arguments.of("english-accepted", List.of("script", "echo"), true),
                Arguments.of("quotes-missing-close", List.of("quote-balance"), false),
                Arguments.of("quotes-stray-close", List.of("quote-balance"), false),
                Arguments.of("leftover-english-paragraph", List.of("language-identity", "script", "echo"), false),
                Arguments.of("mixed-script-look-alike", List.of("script-purity"), false),
                Arguments.of("omission", List.of("length"), true),
                Arguments.of("duplicate-word", List.of("duplicate-word", "length"), true));
    }

    @ParameterizedTest
    @MethodSource("defects")
    void replay_everyFixMakesTheTextWorse_endsWithTheFirstCandidateAndItsFindings(
            final String id, final List<String> expectedFindings, final boolean candidateStands) {
        final DefectCase defect = EvalCorpus.defects().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow();
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTimes(3, Result.ok(new ChatResponse(WORSE_FIX, FinishReason.STOP)));

        final SegmentOutcome decided = decide(defect, model);

        assertThat(decided.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(decided.findings())
                .extracting(QaFinding::raisedBy)
                .containsExactlyInAnyOrderElementsOf(expectedFindings);
        assertThat(decided.machineTarget()).isEqualTo(candidateStands ? defect.candidate() : null);
    }

    private static SegmentOutcome decide(final DefectCase defect, final ScriptedChatModel model) {
        final PromptTemplates templates = new PromptTemplates();
        final QualityLoop loop = new QualityLoop(
                new ReviewerCall(templates, new ReviewReplyParser(MAPPER)),
                new EditApplier(new EditVerifier()),
                new DirectedFix(templates, new DraftReplyParser(MAPPER)));
        final DraftOutcome.Drafted outcome = new DraftOutcome.Drafted(
                PromptEvalRunner.segment(defect.source()),
                defect.source(),
                List.of(),
                defect.candidate(),
                defect.candidate(),
                defect.candidate(),
                null);
        final LoopSettings settings = new LoopSettings(
                ReviewMode.UNATTENDED, DialParameters.of(QualityDial.MAX), FRAME, NamePolicy.TRANSLITERATE, List.of());
        final ChunkDecider decider = Objects.requireNonNull(
                loop.start(List.of(outcome), settings, PASSTHROUGH, (kind, segmentId, request) -> model.chat(request))
                        .data());
        return Objects.requireNonNull(decider.nextDecision().data());
    }
}
