package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.QualityDial;

class EvalProvenanceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T12:34:56.789Z"), ZoneOffset.UTC);

    @TempDir
    private Path dir;

    private EvalProvenance provenance(final Map<String, String> env) {
        return EvalProvenance.capture("realrun", "gemma4:e4b-mlx", "", env, CLOCK, dir, 16384);
    }

    @Test
    void promptHash_sameFilesAnyOrder_isTwelveHexAndStable() throws IOException {
        Files.writeString(dir.resolve("b.prompt"), "two", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("a.prompt"), "one", StandardCharsets.UTF_8);

        final String hash = EvalProvenance.promptHash(dir);

        assertThat(hash).matches("[0-9a-f]{12}").isEqualTo(EvalProvenance.promptHash(dir));
    }

    @Test
    void promptHash_oneByteChanged_differs() throws IOException {
        final Path file = Files.writeString(dir.resolve("a.prompt"), "one", StandardCharsets.UTF_8);
        final String before = EvalProvenance.promptHash(dir);

        Files.writeString(file, "ones", StandardCharsets.UTF_8);

        assertThat(EvalProvenance.promptHash(dir)).isNotEqualTo(before);
    }

    @Test
    void promptHash_missingDirectory_isUnknown() {
        assertThat(EvalProvenance.promptHash(dir.resolve("none"))).isEqualTo("unknown");
    }

    @Test
    void git_notARepository_isUnknownAndClean() {
        assertThat(EvalProvenance.git(dir)).containsExactly("unknown", "false");
    }

    @Test
    void capture_settings_areRecorded() {
        final EvalProvenance p = provenance(Map.of(
                "BOOKLOOM_EVAL_LABEL", "round 2",
                "BOOKLOOM_EVAL_PRESET", "glass-harbour",
                "BOOKLOOM_EVAL_PROVIDER", "lmstudio"));

        assertThat(p.timestamp()).isEqualTo("2026-10-08T12:34:56Z");
        assertThat(p.gitSha()).isEqualTo("unknown");
        assertThat(p)
                .extracting("label", "provider", "window", "dial", "brief", "suite")
                .containsExactly("round 2", "lmstudio", 16384, "BALANCED", "preset=glass-harbour", "realrun");
        assertThat(p.runName()).isEqualTo("20261008-123456-realrun-gemma4_e4b-mlx-round_2");
    }

    // The judgement suites read a book at Fast unless the env names a dial, so their report says the dial they used.
    @Test
    void capture_dialOfTheSuite_isRecordedInsteadOfTheBalancedDefault() {
        final EvalProvenance p = EvalProvenance.capture(
                "judgement", "gemma4:e4b-mlx", "", Map.of(), CLOCK, dir, 16384, QualityDial.FAST.name());

        assertThat(p.dial()).isEqualTo("FAST");
    }

    // The thinking plan changes what a report measures, so each report says which it ran with.
    @Test
    void capture_nothingSet_recordsThinkingOff() {
        assertThat(provenance(Map.of()).reasoning()).isEqualTo("off");
    }

    @Test
    void capture_reasoningPlan_isRecorded() {
        final EvalProvenance p = provenance(Map.of("BOOKLOOM_REASONING", "TERM_READING=on,DRAFT_BATCH_JSON=on"));

        assertThat(p.reasoning()).isEqualTo("DRAFT_BATCH_JSON=on,TERM_READING=on");
    }

    @Test
    void json_noJudge_saysModeNone() throws IOException {
        final JsonNode judge =
                new ObjectMapper().readTree(provenance(Map.of()).json()).get("judge");

        assertThat(judge.toString()).isEqualTo("{\"mode\":\"none\"}");
    }

    @Test
    void json_withJudge_writesTheJudgeBlock() throws IOException {
        final JudgeRunner.Block block = new JudgeRunner.Block(
                "gemma4:26b", "q4_K_M", "ollama 0.12.6", "bl-judge-1", "d7b8b8ded24e", "off", "pairwise", true, null);

        final JsonNode judge = new ObjectMapper()
                .readTree(provenance(Map.of()).withJudge(block).json())
                .get("judge");

        assertThat(judge.toString())
                .startsWith("{\"model\":\"gemma4:26b\",\"quant\":\"q4_K_M\",\"server\":\"ollama 0.12.6\","
                        + "\"rubricVersion\":\"bl-judge-1\",\"rubricHash\":\"d7b8b8ded24e\",\"reasoning\":\"off\","
                        + "\"mode\":\"pairwise\",\"sameFamily\":true,\"calibration\":{");
    }

    @Test
    void json_withJudge_carriesTheCalibrationResult() throws IOException {
        final JudgeRunner.Block block = new JudgeRunner.Block(
                "gemma4:26b-mlx", null, null, "bl-judge-1", "d7b8b8ded24e", "off", "pairwise", true, null);

        final JsonNode calibration = new ObjectMapper()
                .readTree(provenance(Map.of()).withJudge(block).json())
                .get("judge")
                .get("calibration");

        assertThat(calibration.get("rubricHash").asText()).isEqualTo("d7b8b8ded24e");
        assertThat(calibration.get("dataset").asText()).isEqualTo("cal-1");
        assertThat(calibration.get("tprWilsonLower").asDouble()).isEqualTo(0.510);
        assertThat(calibration.get("passed").asBoolean()).isFalse();
    }

    @Test
    void json_reasoning_isAFieldOfTheProvenance() throws IOException {
        final String json =
                provenance(Map.of("BOOKLOOM_REASONING", "REVIEWER=on")).json();

        final JsonNode node = new ObjectMapper().readTree(json);
        assertThat(node.get("reasoning").asText()).isEqualTo("REVIEWER=on");
    }

    @Test
    void within_report_putsProvenanceFirstAndStaysJson() throws IOException {
        final String json = provenance(Map.of()).within("{\"suite\":\"words\",\"recall\":1.0}");

        final JsonNode node = new ObjectMapper().readTree(json);
        assertThat(json).startsWith("{\"provenance\":{\"timestamp\"");
        assertThat(node.at("/provenance/model/id").asText()).isEqualTo("gemma4:e4b-mlx");
        assertThat(node.get("recall").asDouble()).isEqualTo(1.0);
    }

    @Test
    void write_historyOn_keepsBothFilesWithProvenance() throws IOException {
        final Path reports = dir.resolve("reports");
        final Path history = dir.resolve("hist");

        EvalOutput.write(provenance(Map.of()), "m-words", "table", "{\"a\":1}", reports, Optional.of(history));
        EvalOutput.write(provenance(Map.of()), "m-words", "table", "{\"a\":1}", reports, Optional.of(history));

        final Path run = history.resolve("20261008-123456-realrun-gemma4_e4b-mlx");
        assertThat(Files.readString(run.resolve("report.json"))).contains("\"provenance\"", "\"a\":1");
        assertThat(run.resolve("report.txt")).hasContent("table");
        assertThat(history.resolve("20261008-123456-realrun-gemma4_e4b-mlx-2")).isDirectory();
        assertThat(Files.readString(reports.resolve("m-words.json"))).contains("\"provenance\"");
    }

    @Test
    void historyRoot_offOrEmptyOrPath_follows() {
        assertThat(EvalOutput.historyRoot(Map.of("BOOKLOOM_EVAL_HISTORY_DIR", "OFF"), dir))
                .isEmpty();
        assertThat(EvalOutput.historyRoot(Map.of(), dir)).contains(dir.resolve(".temporary_context/eval-history"));
        assertThat(EvalOutput.historyRoot(Map.of("BOOKLOOM_EVAL_HISTORY_DIR", "/x/y"), dir))
                .contains(Path.of("/x/y"));
    }

    @Test
    void write_historyOff_writesOnlyTheReports() throws IOException {
        final Path reports = dir.resolve("reports");

        EvalOutput.write(provenance(Map.of()), "m-words", "table", "{\"a\":1}", reports, Optional.empty());

        assertThat(reports.resolve("m-words.json")).exists();
        assertThat(dir.resolve("hist")).doesNotExist();
    }

    @Test
    void within_versionTwo_writesDatasetModelRepeatsAndNoiseBand() throws IOException {
        final EvalProvenance p = EvalProvenance.capture(
                        MiniReport.SUITE,
                        "gemma4:e4b-mlx",
                        "",
                        Map.of("BOOKLOOM_EVAL_REPEATS", "3", "BOOKLOOM_EVAL_QUANT", "q4_K_M"),
                        CLOCK,
                        dir,
                        16384)
                .withAggregates(List.of(0.80, 0.84, 0.82));

        final JsonNode node = new ObjectMapper().readTree(p.within("{\"suite\":\"mini\"}"));

        final JsonNode provenance = node.get("provenance");
        assertThat(provenance.get("version").asInt()).isEqualTo(2);
        assertThat(provenance.at("/dataset/id").asText()).isEqualTo("mini-en");
        assertThat(provenance.at("/model/id").asText()).isEqualTo("gemma4:e4b-mlx");
        assertThat(provenance.at("/model/quantisation").asText()).isEqualTo("q4_K_M");
        assertThat(provenance.at("/model/seeds").toString()).isEqualTo("[1000,1001,1002]");
        assertThat(provenance.at("/repeats/k").asInt()).isEqualTo(3);
        assertThat(provenance.get("noiseBand").asDouble()).isCloseTo(0.04, within(1e-9));
        assertThat(provenance.get("baselineOf").isNull()).isTrue();
    }

    @Test
    void withDataset_russianMini_replacesTheSuiteDatasetAndKeepsAggregatesInTheJson() throws IOException {
        final EvalProvenance p =
                provenance(Map.of()).withDataset(MiniBook.MINI_RU).withAggregates(List.of(0.5, 0.6, 0.55));

        final JsonNode provenance = new ObjectMapper().readTree(p.within("{}")).get("provenance");

        assertThat(provenance.at("/dataset/id").asText()).isEqualTo("mini-ru");
        assertThat(provenance.at("/dataset/pair").asText()).isEqualTo("ru-uk");
        assertThat(provenance.at("/dataset/ownerData").asBoolean()).isFalse();
        assertThat(provenance.get("aggregates").toString()).isEqualTo("[0.5,0.6,0.55]");
    }

    @Test
    void capture_sequenceSuite_readsTheBundledInventedSequenceDataset() {
        final EvalProvenance p = EvalProvenance.capture("sequence", "m", "", Map.of(), CLOCK, dir, 16384);

        assertThat(p.dataset()).isNotNull();
        assertThat(p.dataset().id()).isEqualTo("sequence");
        assertThat(p.dataset().ownerData()).isFalse();
    }
}
