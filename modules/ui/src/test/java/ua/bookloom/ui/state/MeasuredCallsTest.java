package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.MeasuredFrom;
import ua.bookloom.api.llm.MeasuredSpeed;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.CallKind;

/** The seconds per call around a names pass or a run: counted from the mark, stored on the chosen model only. */
class MeasuredCallsTest {

    private static final ModelSelection MODEL = new ModelSelection("ollama", "gemma");

    private final List<CallRecord> records = new ArrayList<>();
    private final List<Map<CallKind, Double>> written = new ArrayList<>();
    private final CallLedger ledger = new CallLedger() {
        @Override
        public void record(final CallRecord record) {
            records.add(record);
        }

        @Override
        public long mark() {
            return records.size();
        }

        @Override
        public List<CallRecord> since(final long mark) {
            return List.copyOf(records.subList((int) mark, records.size()));
        }
    };
    private final MeasuredCalls measured = new MeasuredCalls(
            ledger,
            model -> Map.of(CallKind.TERM_READING, new MeasuredSpeed(5.0, MeasuredFrom.RUN)),
            (model, seconds) -> written.add(seconds));

    // The spec's names pass: 40 name-reading calls in 200 s store 5.0 s per name-reading call.
    @Test
    void keep_namesPassOfFortyReadings_storesFiveSecondsPerReading() {
        ledger.record(new CallRecord(CallKind.SUMMARY, null, Duration.ofSeconds(30), null, null));
        final long mark = measured.mark();
        Collections.nCopies(40, new CallRecord(CallKind.TERM_READING, null, Duration.ofSeconds(5), null, null))
                .forEach(ledger::record);

        measured.keep(MODEL, mark, "names pass");

        assertThat(written).containsExactly(Map.of(CallKind.TERM_READING, 5.0));
    }

    @Test
    void keep_noModelChosen_storesNothing() {
        ledger.record(new CallRecord(CallKind.TERM_READING, null, Duration.ofSeconds(5), null, null));

        measured.keep(null, 0, "names pass");

        assertThat(written).isEmpty();
    }

    @Test
    void stored_noModelChosen_isEmptyAndAChosenModelReadsItsRow() {
        assertThat(measured.stored(null)).isEmpty();
        assertThat(measured.stored(MODEL))
                .isEqualTo(Map.of(CallKind.TERM_READING, new MeasuredSpeed(5.0, MeasuredFrom.RUN)));
    }
}
