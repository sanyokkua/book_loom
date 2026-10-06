package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.eval.BatchEvalCases.Batch;
import ua.bookloom.pipeline.eval.EvalCase.Draft;
import ua.bookloom.pipeline.run.PromptRequests.PreparedBatch;

class BatchEvalCasesTest {

    private static EvalProject projectOf(final Batch batch) {
        return EvalProject.of(
                new EvalProject.Setup(
                        PromptEvalCases.SOURCE_LANGUAGE,
                        PromptEvalCases.TARGET_LANGUAGE,
                        null,
                        batch.cases().stream()
                                .flatMap(draft -> draft.glossary().stream())
                                .distinct()
                                .toList(),
                        new EvalContext(batch.earlier(), null, List.of(), null),
                        batch.cases().stream().map(Draft::masked).toList(),
                        batch.next() == null ? List.of() : List.of(batch.next().masked())),
                (kind, segmentId, request) ->
                        Result.err(new AppError(ErrorCode.internal, "t", "m", null, false, null)));
    }

    private static PreparedBatch preparedFirst(final int size) {
        return projectOf(BatchEvalCases.batches(size).getFirst()).batch().orElseThrow();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 12, 16})
    void batches_everySize_holdsThatManyCasesAndTheRunNumbersTheItemsFromOne(final int size) {
        final List<Batch> batches = BatchEvalCases.batches(size);

        assertThat(batches)
                .isNotEmpty()
                .allSatisfy(batch -> assertThat(batch.cases()).hasSize(size));
        final PreparedBatch prepared = preparedFirst(size);
        assertThat(prepared.items()).hasSize(size);
        assertThat(prepared.items().getFirst().id()).isEqualTo("1");
        assertThat(prepared.items().getLast().id()).isEqualTo(String.valueOf(size));
    }

    @Test
    void batches_sizeFour_hasABatchWithANumberOnlyLine() {
        final List<String> shown = BatchEvalCases.batches(4).stream()
                .flatMap(batch -> projectOf(batch).batch().orElseThrow().items().stream())
                .map(BatchItem::masked)
                .toList();

        assertThat(shown).contains("1881", "* * *", "XIV");
    }

    @Test
    void batches_firstBatch_hasNoPrecedingPairsAndLaterOnesDo() {
        final List<Batch> batches = BatchEvalCases.batches(4);

        assertThat(projectOf(batches.getFirst()).batch().orElseThrow().context().precedingPairs())
                .isEmpty();
        assertThat(projectOf(batches.get(1)).batch().orElseThrow().context().precedingPairs())
                .hasSize(2);
        assertThat(projectOf(batches.getFirst()).batch().orElseThrow().context().nextSource())
                .isEqualTo(batches.get(1).cases().getFirst().masked());
    }

    @Test
    void batches_lockedName_isListedByTheRunsOwnLinePrefixedWithItsItemId() {
        final PreparedBatch first = preparedFirst(4);

        assertThat(first.context().draft().glossaryLines())
                .singleElement()
                .satisfies(line -> assertThat(line).matches("1: ⟦g\\d+⟧ → Бартімеус, character, male"));
    }
}
