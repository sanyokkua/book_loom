package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KeyTermAsksTest {

    @Test
    void isAsking_newRun_asks() {
        assertThat(new KeyTermAsks().isAsking()).isTrue();
    }

    @Test
    void isAsking_fewerSilentBatchesThanTheProbe_stillAsks() {
        final KeyTermAsks asks = new KeyTermAsks();

        recordSilent(asks, KeyTermAsks.PROBE_BATCHES - 1, true);

        assertThat(asks.isAsking()).isTrue();
    }

    @Test
    void isAsking_probeBatchesAllSilent_stopsAsking() {
        final KeyTermAsks asks = new KeyTermAsks();

        recordSilent(asks, KeyTermAsks.PROBE_BATCHES, true);

        assertThat(asks.isAsking()).isFalse();
    }

    @Test
    void isAsking_oneAnswerAmongTheProbe_asksForTheWholeRun() {
        final KeyTermAsks asks = new KeyTermAsks();
        asks.record(true, false);
        asks.record(true, true);

        recordSilent(asks, 3 * KeyTermAsks.PROBE_BATCHES, true);

        assertThat(asks.isAsking()).isTrue();
    }

    @Test
    void record_batchThatAskedNothing_countsTowardNoStop() {
        final KeyTermAsks asks = new KeyTermAsks();

        recordSilent(asks, 2 * KeyTermAsks.PROBE_BATCHES, false);

        assertThat(asks.isAsking()).isTrue();
    }

    private static void recordSilent(final KeyTermAsks asks, final int batches, final boolean asked) {
        IntStream.range(0, batches).forEach(batch -> asks.record(asked, false));
    }
}
