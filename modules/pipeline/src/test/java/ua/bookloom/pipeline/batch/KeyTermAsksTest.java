package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeyTermAsksTest {

    @Test
    void isAsking_newRun_asks() {
        assertThat(new KeyTermAsks().isAsking()).isTrue();
    }

    @Test
    void isAsking_fewerSilentBatchesThanTheProbe_stillAsks() {
        final KeyTermAsks asks = new KeyTermAsks();

        for (int batch = 1; batch < KeyTermAsks.PROBE_BATCHES; batch++) {
            asks.record(true, false);
        }

        assertThat(asks.isAsking()).isTrue();
    }

    @Test
    void isAsking_probeBatchesAllSilent_stopsAsking() {
        final KeyTermAsks asks = new KeyTermAsks();

        for (int batch = 0; batch < KeyTermAsks.PROBE_BATCHES; batch++) {
            asks.record(true, false);
        }

        assertThat(asks.isAsking()).isFalse();
    }

    @Test
    void isAsking_oneAnswerAmongTheProbe_asksForTheWholeRun() {
        final KeyTermAsks asks = new KeyTermAsks();
        asks.record(true, false);
        asks.record(true, true);

        for (int batch = 0; batch < 3 * KeyTermAsks.PROBE_BATCHES; batch++) {
            asks.record(true, false);
        }

        assertThat(asks.isAsking()).isTrue();
    }

    @Test
    void record_batchThatAskedNothing_countsTowardNoStop() {
        final KeyTermAsks asks = new KeyTermAsks();

        for (int batch = 0; batch < 2 * KeyTermAsks.PROBE_BATCHES; batch++) {
            asks.record(false, false);
        }

        assertThat(asks.isAsking()).isTrue();
    }
}
