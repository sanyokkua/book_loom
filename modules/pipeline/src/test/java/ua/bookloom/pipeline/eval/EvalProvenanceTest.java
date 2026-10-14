package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    // The relevant-pairs gate (15i.T8) compares an `on` and an `off` report, so each says which it was.
    @Test
    void capture_relevantPairsSwitch_isRecordedInTheBrief() {
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_RELEVANT_PAIRS", "off")).brief())
                .isEqualTo("relevant_pairs=off");
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_PRESET", "glass-harbour", "BOOKLOOM_EVAL_RELEVANT_PAIRS", "on"))
                        .brief())
                .isEqualTo("preset=glass-harbour relevant_pairs=on");
    }

    // The batch-format gate (15i.X1) compares reports of three arms, so each says which format it measured.
    @Test
    void capture_batchFormatSwitch_isRecordedInTheBrief() {
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_BATCH_FORMAT", "markers")).brief())
                .isEqualTo("batch_format=markers");
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_PRESET", "glass-harbour", "BOOKLOOM_EVAL_BATCH_FORMAT", "dual"))
                        .brief())
                .isEqualTo("preset=glass-harbour batch_format=dual");
    }

    // The long-paragraph gate (15i.X3) compares the groups arm with today's, so each report says which it measured.
    @Test
    void capture_longParagraphsSwitch_isRecordedInTheBrief() {
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_LONG_PARAGRAPHS", "groups")).brief())
                .isEqualTo("long_paragraphs=groups");
        assertThat(provenance(Map.of("BOOKLOOM_EVAL_BATCH_FORMAT", "dual", "BOOKLOOM_EVAL_LONG_PARAGRAPHS", "groups"))
                        .brief())
                .isEqualTo("batch_format=dual long_paragraphs=groups");
    }

    // The thinking plan and the notes arm change what a report measures, so each report says which it ran with.
    @Test
    void capture_nothingSet_recordsThinkingAndNotesOff() {
        final EvalProvenance p = provenance(Map.of());

        assertThat(p.reasoning()).isEqualTo("off");
        assertThat(p.batchNotes()).isEqualTo("off");
    }

    @Test
    void capture_reasoningPlanAndNotesSwitch_areRecorded() {
        final EvalProvenance p = provenance(Map.of(
                "BOOKLOOM_REASONING", "TERM_READING=on,DRAFT_BATCH_JSON=on",
                "BOOKLOOM_BATCH_NOTES", "on"));

        assertThat(p.reasoning()).isEqualTo("DRAFT_BATCH_JSON=on,TERM_READING=on");
        assertThat(p.batchNotes()).isEqualTo("on");
    }

    @Test
    void json_reasoningAndNotes_areFieldsOfTheProvenance() throws IOException {
        final String json = provenance(Map.of("BOOKLOOM_REASONING", "REVIEWER=on", "BOOKLOOM_BATCH_NOTES", "on"))
                .json();

        final JsonNode node = new ObjectMapper().readTree(json);
        assertThat(node.get("reasoning").asText()).isEqualTo("REVIEWER=on");
        assertThat(node.get("batchNotes").asText()).isEqualTo("on");
    }

    @Test
    void within_report_putsProvenanceFirstAndStaysJson() throws IOException {
        final String json = provenance(Map.of()).within("{\"suite\":\"words\",\"recall\":1.0}");

        final JsonNode node = new ObjectMapper().readTree(json);
        assertThat(json).startsWith("{\"provenance\":{\"timestamp\"");
        assertThat(node.get("provenance").get("model").asText()).isEqualTo("gemma4:e4b-mlx");
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
        assertThat(EvalOutput.historyRoot(Map.of(), dir)).contains(dir.resolve("eval-history"));
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
}
