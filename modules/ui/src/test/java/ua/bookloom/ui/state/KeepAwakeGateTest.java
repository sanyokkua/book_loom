package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeepAwakeGateTest {

    @Test
    void new_gate_isOn() {
        assertThat(new KeepAwakeGate().isOn()).isTrue();
    }

    @Test
    void seed_offBeforeUse_isOff() {
        final KeepAwakeGate gate = new KeepAwakeGate();

        gate.seed(false);

        assertThat(gate.isOn()).isFalse();
    }

    @Test
    void seed_off_tellsNobody() {
        final KeepAwakeGate gate = new KeepAwakeGate();
        final AtomicInteger told = new AtomicInteger();
        gate.onChange(told::incrementAndGet);

        gate.seed(false);

        assertThat(told).hasValue(0);
    }

    @Test
    void set_off_notifiesListeners() {
        final KeepAwakeGate gate = new KeepAwakeGate();
        final AtomicInteger told = new AtomicInteger();
        gate.onChange(told::incrementAndGet);

        gate.set(false);
        gate.set(false);

        assertThat(gate.isOn()).isFalse();
        assertThat(told).hasValue(1);
    }
}
