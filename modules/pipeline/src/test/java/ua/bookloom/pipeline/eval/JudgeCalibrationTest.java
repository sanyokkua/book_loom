package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.pipeline.eval.CalItems.Item;
import ua.bookloom.pipeline.eval.CalItems.Kind;

/** The calibration run's report must be exactly what {@code scripts/eval-score.py calibrate} reads. */
class JudgeCalibrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String POINTWISE_ERROR = "{\"analysis\":\"The negation is lost.\",\"errors\":"
            + "[{\"span\":\"вже бачила\",\"severity\":\"critical\",\"class\":\"meaning\"}],"
            + "\"scores\":{\"meaning\":1,\"grammar\":5,\"terminology\":5,\"fluency\":4},"
            + "\"flags\":{\"meaningError\":true,\"omission\":false,\"addition\":false,"
            + "\"sourceLanguageResidue\":false,\"formattingBroken\":false},\"verdict\":\"unusable\"}";
    private static final String PAIRWISE_A = "{\"analysis\":\"A keeps the clause.\",\"winner\":\"A\"}";
    private static final Item SINGLE = new Item(
            "cal-x01",
            Kind.SINGLE,
            "dev",
            "en-uk",
            "She had never seen the sea.",
            List.of("Вона вже бачила море."),
            List.of(),
            List.of());
    private static final Item PAIR = new Item(
            "cal-x02",
            Kind.PAIR,
            "dev",
            "ru-uk",
            "Мы шли молча.",
            List.of("Ми йшли мовчки.", "Ми йшли."),
            List.of("Дорога шла через лес."),
            List.of());

    @TempDir
    private Path cache;

    /** Answers in order: the judge model's replies, recorded by hand. */
    private static final class ScriptedModel implements ChatModel {

        private final Deque<String> replies;

        ScriptedModel(final String... replies) {
            this.replies = new ArrayDeque<>(List.of(replies));
        }

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            return Result.ok(
                    new ChatResponse(Objects.requireNonNull(replies.poll(), "no reply left"), FinishReason.STOP));
        }
    }

    private JudgeCalibration calibration(final String... replies) {
        return new JudgeCalibration(new JudgeRunner(
                new ScriptedModel(replies),
                JudgeRunner.Setup.of("gemma4:26b-mlx", "q4_K_M", null),
                JudgeRubric.load(),
                cache));
    }

    @Test
    void json_singleAndPair_writeTheJudgeBlockCalibrateReads() throws Exception {
        final JudgeCalibration.Report report =
                calibration(POINTWISE_ERROR, PAIRWISE_A, PAIRWISE_A).run(List.of(SINGLE, PAIR));

        final JsonNode root = MAPPER.readTree(report.json("cal-1", Set.of("dev")));

        assertThat(root.get("cases")).hasSize(2);
        final JsonNode single = root.get("cases").get(0);
        assertThat(single.get("caseId").asText()).isEqualTo("cal-x01");
        assertThat(single.get("repeat").asInt()).isZero();
        assertThat(single.get("graders").isArray()).isTrue();
        assertThat(single.get("judge").get("meaningError").asBoolean()).isTrue();
        assertThat(single.get("judge").get("score").asDouble()).isEqualTo(3.75);
        final JsonNode pair = root.get("cases").get(1);
        assertThat(pair.get("caseId").asText()).isEqualTo("cal-x02");
        // A in place A both times: the forward answer names a, the swapped answer names b.
        assertThat(pair.get("judge").get("winner").asText()).isEqualTo("A");
        assertThat(pair.get("judge").get("winnerSwapped").asText()).isEqualTo("B");
        assertThat(root.get("unparsed")).isEmpty();
    }

    @Test
    void json_unreadableReply_leavesTheItemOutOfTheCasesAndNamesIt() throws Exception {
        final JudgeCalibration.Report report = calibration("").run(List.of(SINGLE));

        final JsonNode root = MAPPER.readTree(report.json("cal-1", Set.of("dev")));

        assertThat(root.get("cases")).isEmpty();
        assertThat(root.get("unparsed").get(0).asText()).isEqualTo("cal-x01");
    }

    @Test
    void within_provenance_isAVersionTwoReportOfTheCalSuiteWithTheCalDataset() throws Exception {
        final JudgeCalibration.Report report = calibration(POINTWISE_ERROR).run(List.of(SINGLE));
        final EvalProvenance provenance = EvalProvenance.capture(
                JudgeCalibration.SUITE,
                "gemma4:26b-mlx",
                "dev",
                Map.of(),
                Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC),
                EvalProvenance.repoRoot(),
                16_384);

        final JsonNode root = MAPPER.readTree(provenance.within(report.json("cal-1", Set.of("dev"))));

        assertThat(root.get("provenance").get("version").asInt()).isEqualTo(2);
        assertThat(root.get("provenance").get("suite").asText()).isEqualTo("cal");
        assertThat(root.get("provenance").get("dataset").get("id").asText()).isEqualTo("cal-1");
        assertThat(root.get("cases").get(0).get("judge").has("meaningError")).isTrue();
    }
}
