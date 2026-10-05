package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.eval.BatchEvalCases.Batch;

class BatchEvalCasesTest {

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 12, 16})
    void batches_everySize_holdsThatManyItemsNumberedFromOne(final int size) {
        final List<Batch> batches = BatchEvalCases.batches(size);

        assertThat(batches).isNotEmpty().allSatisfy(batch -> {
            assertThat(batch.items()).hasSize(size);
            assertThat(batch.items().getFirst().id()).isEqualTo("1");
            assertThat(batch.items().getLast().id()).isEqualTo(String.valueOf(size));
        });
    }

    @Test
    void batches_sizeFour_hasABatchWithANumberOnlyLine() {
        assertThat(BatchEvalCases.batches(4).stream()
                        .flatMap(batch -> batch.items().stream())
                        .map(BatchItem::masked))
                .contains("1881", "* * *", "XIV");
    }

    @Test
    void batches_firstBatch_hasNoPrecedingPairsAndLaterOnesDo() {
        final List<Batch> batches = BatchEvalCases.batches(4);

        assertThat(batches.getFirst().context().precedingPairs()).isEmpty();
        assertThat(batches.get(1).context().precedingPairs()).hasSize(2);
        assertThat(batches.getFirst().context().nextSource())
                .isEqualTo(batches.get(1).items().getFirst().masked());
    }

    @Test
    void batches_lockedNameLine_isPrefixedWithItsItemId() {
        final Batch first = BatchEvalCases.batches(4).getFirst();

        assertThat(first.context().draft().glossaryLines()).containsExactly("1: ⟦g0⟧ → Бартімеус, person, male");
    }
}
