package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.pipeline.batch.BatchDrafter;
import ua.bookloom.pipeline.eval.BatchEvalCases.Batch;
import ua.bookloom.pipeline.eval.BatchEvalReport.Cell;
import ua.bookloom.pipeline.prompt.ModelCalls;

/** The A/B machinery against scripted models: no real model, so its numbers can be checked by hand. */
class BatchEvalRunnerTest {

    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");

    /** A model that copies every item's source as its translation, except the ids {@code dropped} names. */
    private static ModelCalls copying(final Predicate<Integer> dropped) {
        return (kind, segmentId, request) -> {
            final String user = request.messages().get(1).content();
            final List<String> entries = new ArrayList<>();
            final Matcher matcher = ITEM.matcher(user.substring(user.indexOf("<Items>")));
            while (matcher.find()) {
                if (!dropped.test(Integer.parseInt(matcher.group(1)))) {
                    entries.add("{\"id\":\"" + matcher.group(1) + "\",\"target\":\""
                            + matcher.group(2).replace("\"", "\\\"") + "\"}");
                }
            }
            final String content = "{\"items\":[" + String.join(",", entries) + "]}";
            return Result.ok(new ChatResponse(content, FinishReason.STOP, new TokenUsage(500, 40, null)));
        };
    }

    private static Cell cell(final BatchEvalReport report, final int size) {
        return report.cells().stream()
                .filter(cell -> cell.size() == size)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void runAll_modelAnsweringEveryId_scoresFullIdValidityAndTokenGate() {
        final BatchEvalReport report =
                new BatchEvalReport("scripted", new BatchEvalRunner(copying(id -> false)).runAll(List.of(4)));

        assertThat(report.cells()).hasSize(1);
        assertThat(report.cells()).allSatisfy(cell -> {
            assertThat(cell.idValidity()).isEqualTo(1.0);
            assertThat(cell.tokenGate()).isEqualTo(1.0);
            assertThat(cell.omission()).isZero();
            assertThat(cell.outputTokensPerItem()).isEqualTo(10.0);
        });
    }

    @Test
    void runAll_modelDroppingEveryFourthItem_scoresAQuarterOmission() {
        final BatchEvalReport report =
                new BatchEvalReport("scripted", new BatchEvalRunner(copying(id -> id % 4 == 0)).runAll(List.of(4)));

        assertThat(cell(report, 4).omission()).isEqualTo(0.25);
        assertThat(cell(report, 4).idValidity()).isEqualTo(0.75);
        assertThat(cell(report, 4).tokenGate()).isEqualTo(0.75);
    }

    /** A model that answers every item with {@code answer(source)}. */
    private static ModelCalls answering(final java.util.function.UnaryOperator<String> answer) {
        return (kind, segmentId, request) -> {
            final String user = request.messages().get(1).content();
            final List<String> entries = new ArrayList<>();
            final Matcher matcher = ITEM.matcher(user.substring(user.indexOf("<Items>")));
            while (matcher.find()) {
                entries.add("{\"id\":\"" + matcher.group(1) + "\",\"target\":\""
                        + answer.apply(matcher.group(2)).replace("\"", "\\\"").replace("\n", "\\n") + "\"}");
            }
            return Result.ok(new ChatResponse("{\"items\":[" + String.join(",", entries) + "]}", FinishReason.STOP));
        };
    }

    @Test
    void runAll_modelAnsweringATokenText_scoresTheTooShortItems() {
        final BatchEvalReport report =
                new BatchEvalReport("scripted", new BatchEvalRunner(answering(source -> "Ok")).runAll(List.of(8)));

        assertThat(cell(report, 8).tooShort()).isGreaterThan(0.0);
        assertThat(cell(report, 8).leaked()).isZero();
    }

    @Test
    void runAll_modelLeakingACodeFenceIntoEveryTarget_scoresEveryItemLeaked() {
        final BatchEvalReport report = new BatchEvalReport(
                "scripted", new BatchEvalRunner(answering(source -> source + "\n```")).runAll(List.of(4)));

        assertThat(cell(report, 4).leaked()).isEqualTo(1.0);
    }

    @Test
    void run_callThatFails_failsEveryItemOfTheBatch() {
        final ModelCalls failing =
                (kind, segmentId, request) -> Result.err(new AppError(ErrorCode.internal, "t", "m", null, false, null));
        final Batch batch = BatchEvalCases.batches(4).getFirst();

        final BatchEvalRow row = new BatchEvalRunner(failing).run(batch);

        assertThat(row.callFailed()).isTrue();
        assertThat(row.missing()).isEqualTo(4);
        assertThat(row.idValid()).isZero();
    }

    @Test
    void run_request_asksForTheBatchSchema() {
        final List<ChatRequest> seen = new ArrayList<>();
        final ModelCalls recording = (kind, segmentId, request) -> {
            seen.add(request);
            return copying(id -> false).call(kind, segmentId, request);
        };

        new BatchEvalRunner(recording).run(BatchEvalCases.batches(4).getFirst());

        assertThat(seen)
                .singleElement()
                .satisfies(request -> assertThat(request.responseFormat()).isNotNull());
    }

    @Test
    void json_report_namesTheSuiteAndEveryCell() {
        final BatchEvalReport report =
                new BatchEvalReport("m:1", new BatchEvalRunner(copying(id -> false)).runAll(List.of(8)));

        assertThat(report.json())
                .startsWith("{\"suite\":\"batch\",\"model\":\"m:1\",\"cells\":[")
                .contains("\"size\":8")
                .contains("\"idValidity\":1.000")
                .contains("\"tooShort\":0.000", "\"leaked\":0.000");
        assertThat(report.table()).contains("idValid", "tokGate", "omit", "merge", "tooShort", "leaked");
    }

    @Test
    void runAdaptive_modelOmittingAnItem_startsAtTheJobsSizeThenHalves() {
        final List<BatchEvalRow> rows =
                new BatchEvalRunner(copying(id -> id == 1)).runAdaptive(BatchDrafter.DEFAULT_INITIAL_SIZE);

        assertThat(rows.getFirst().size()).isEqualTo(BatchDrafter.DEFAULT_INITIAL_SIZE);
        assertThat(rows.get(1).size()).isLessThan(BatchDrafter.DEFAULT_INITIAL_SIZE);
        assertThat(rows.stream().mapToInt(BatchEvalRow::size).sum())
                .isGreaterThanOrEqualTo(BatchEvalCases.drafts().size() - 1);
    }

    @Test
    void runAdaptive_cleanModel_growsTheBatchAfterACleanStreak() {
        final List<BatchEvalRow> rows =
                new BatchEvalRunner(copying(id -> false)).runAdaptive(BatchDrafter.MIN_BATCHING_SIZE);

        assertThat(rows.getFirst().size()).isEqualTo(BatchDrafter.MIN_BATCHING_SIZE);
        assertThat(rows.stream().mapToInt(BatchEvalRow::size)).anyMatch(size -> size > BatchDrafter.MIN_BATCHING_SIZE);
    }

    @Test
    void cells_adaptiveSizes_arePerSizeAndSorted() {
        final BatchEvalReport report = new BatchEvalReport(
                "scripted", new BatchEvalRunner(copying(id -> id == 1)).runAdaptive(BatchDrafter.DEFAULT_INITIAL_SIZE));

        assertThat(report.cells().stream().map(Cell::size))
                .isSorted()
                .doesNotHaveDuplicates()
                .contains(8);
    }
}
