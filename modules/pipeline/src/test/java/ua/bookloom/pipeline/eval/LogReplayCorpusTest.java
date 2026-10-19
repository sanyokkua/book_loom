package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.eval.LogReplayCorpus.Outcome;

/** The replay corpus reads a hand-made trace log (invented text, same line formats as the real one). */
class LogReplayCorpusTest {

    private static final int LOGGED_CALLS = 10;
    private static final int DRAFT_CALLS = 8;
    private static final int FLAGGED_CALLS = 2;

    private static Path fixture;
    private static LogReplayCorpus corpus;

    @BeforeAll
    static void load() throws URISyntaxException {
        fixture = Path.of(LogReplayCorpusTest.class
                .getResource("/eval/replay/sample-trace.txt")
                .toURI());
        corpus = LogReplayCorpus.load(fixture, 0);
    }

    private static LoggedCall call(final int order) {
        return corpus.calls().get(order);
    }

    @Test
    void load_fixture_findsEveryAnnouncedCallWithItsKindAndSegments() {
        assertThat(corpus.calls()).hasSize(LOGGED_CALLS);
        assertThat(corpus.calls().stream().map(LoggedCall::kind))
                .containsExactly(
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.DRAFT,
                        CallKind.REVIEW,
                        CallKind.REVISION,
                        CallKind.DRAFT);
        assertThat(call(2).segmentIds()).containsExactly("ch1.xhtml:2", "ch1.xhtml:3", "ch1.xhtml:4");
    }

    @Test
    void load_requestAndResponseOfTheSameThread_arePairedWithTheirCall() {
        assertThat(call(0).reply())
                .hasValueSatisfying(reply -> assertThat(reply).contains("Лампа згасла."));
        assertThat(call(7).reply()).hasValue("{\"issues\":[]}");
        assertThat(call(8).reply()).isEmpty();
        assertThat(call(9).reply()).isEmpty();
    }

    @Test
    void load_aLineOfAnotherThreadAndARetriedRequest_doNotBelongToTheCall() {
        assertThat(call(2).requestBody()).doesNotContain("retry").contains("Nobody came.");
        assertThat(call(3).requestBody()).doesNotContain("/v1/models");
    }

    @Test
    void load_responseBodyWithNewlineMarks_isReadableJson() {
        assertThat(call(1).responseBody()).contains("\n").doesNotContain("⏎");
        assertThat(call(1).reply()).isPresent();
    }

    @Test
    void sourceTexts_singleAndBatchCalls_areReadFromTheUserMessage() {
        assertThat(call(0).sourceTexts()).containsExactly("The lamp went out.");
        assertThat(call(0).isBatch()).isFalse();
        assertThat(call(2).sourceTexts()).containsExactly("The door was open.", "Nobody came.", "She waited there.");
        assertThat(call(2).isBatch()).isTrue();
        assertThat(call(7).sourceTexts()).containsExactly("The lamp went out.");
    }

    @Test
    void languages_namedInThePrompt_areReadAndEnglishToUkrainianIsTheDefault() {
        assertThat(call(0).sourceLanguage()).isEqualTo("en");
        assertThat(call(0).targetLanguage()).isEqualTo("uk");
        assertThat(call(9).sourceLanguage()).isEqualTo("ru");
    }

    @Test
    void decisions_lastLineOfASegmentWins_andOutcomesCombineWorstFirst() {
        assertThat(corpus.decisions().get("ch1.xhtml:6").outcome()).isEqualTo(Outcome.FLAGGED);
        assertThat(corpus.decisions().get("ch1.xhtml:1").outcome()).isEqualTo(Outcome.REPAIRED);
        assertThat(corpus.outcomeOf(call(3))).isEqualTo(Outcome.FLAGGED);
        assertThat(corpus.outcomeOf(call(0))).isEqualTo(Outcome.ACCEPTED);
    }

    @Test
    void chatRequest_loggedBody_keepsMessagesAndSettingsAsSent() {
        final ChatRequest request = call(0).chatRequest();

        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().getFirst().role()).isEqualTo(ChatRole.SYSTEM);
        assertThat(request.temperature()).isEqualTo(0.2);
        assertThat(request.maxOutputTokens()).isEqualTo(141);
        assertThat(request.reasoningEnabled()).isFalse();
        assertThat(request.responseFormat().name()).isEqualTo("draft");
        assertThat(request.callKind()).isEqualTo(CallKind.DRAFT);
    }

    @Test
    void load_gzippedLog_givesTheSameCalls(@TempDir final Path dir) throws IOException {
        final Path gz = dir.resolve("trace.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write(Files.readAllBytes(fixture));
        }

        final LogReplayCorpus fromGz = LogReplayCorpus.load(gz, 0);

        assertThat(fromGz.calls()).hasSize(LOGGED_CALLS);
        assertThat(fromGz.calls().getFirst().requestBody()).isEqualTo(call(0).requestBody());
    }

    @Test
    void load_withAByteLimit_readsOnlyTheFirstLines() {
        final LogReplayCorpus first = LogReplayCorpus.load(fixture, 6000);

        assertThat(first.calls()).isNotEmpty().hasSizeLessThan(LOGGED_CALLS);
        assertThat(first.calls().getFirst().requestBody()).isEqualTo(call(0).requestBody());
    }

    @Test
    void fast_aSmallTarget_keepsEveryFlaggedCallFirst() {
        final List<LoggedCall> picked = ReplaySelection.fast(corpus, 1);

        assertThat(picked).containsExactly(call(3), call(4));
    }

    @Test
    void fast_target_dealsTheRestAcrossLengthShapeAndOutcome() {
        final List<LoggedCall> picked = ReplaySelection.fast(corpus, 6);

        assertThat(picked).hasSize(6).contains(call(3), call(4));
        assertThat(picked).extracting(LoggedCall::order).isSorted();
        assertThat(picked).allSatisfy(call -> assertThat(call.kind()).isEqualTo(CallKind.DRAFT));
        final Map<Boolean, Long> byShape = picked.stream()
                .collect(java.util.stream.Collectors.partitioningBy(
                        LoggedCall::isBatch, java.util.stream.Collectors.counting()));
        assertThat(byShape.get(true)).isGreaterThanOrEqualTo(2);
        assertThat(byShape.get(false)).isGreaterThanOrEqualTo(2);
        assertThat(picked.stream()
                        .map(call -> ReplaySelection.cellOf(corpus, call).isLong()))
                .contains(true, false);
        assertThat(picked.stream().map(corpus::outcomeOf)).contains(Outcome.REPAIRED, Outcome.ACCEPTED);
    }

    @Test
    void fast_targetAboveTheLog_isEveryDraftCallOnce() {
        assertThat(ReplaySelection.fast(corpus, 100)).hasSize(DRAFT_CALLS).doesNotHaveDuplicates();
        assertThat(ReplaySelection.fast(corpus, 1)).hasSize(FLAGGED_CALLS);
    }

    @Test
    void fromEnv_unsetLog_isNoReplay() {
        assertThat(ReplayConfig.fromEnv(Map.of())).isEmpty();
    }

    @Test
    void fromEnv_logAndSettings_areRead() {
        final ReplayConfig config = ReplayConfig.fromEnv(Map.of(
                        ReplayConfig.LOG_ENV, "/some/trace.log.gz",
                        ReplayConfig.MODE_ENV, "RAW",
                        ReplayConfig.MAX_BYTES_ENV, "2097152",
                        ReplayConfig.LIMIT_ENV, "5"))
                .orElseThrow();

        assertThat(config.log()).isEqualTo(Path.of("/some/trace.log.gz"));
        assertThat(config.mode()).isEqualTo(ReplayConfig.Mode.RAW);
        assertThat(config.maxBytes()).isEqualTo(2_097_152L);
        assertThat(config.limit()).isEqualTo(5);
        assertThat(config.target()).isEqualTo(ReplayConfig.DEFAULT_TARGET);
    }
}
