package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.KeepAwake;

class AwakeHoldersTest {

    private final List<String> calls = new ArrayList<>();
    private final KeepAwake keepAwake = new KeepAwake() {
        @Override
        public void start() {
            calls.add("start");
        }

        @Override
        public void stop() {
            calls.add("stop");
        }
    };
    private final KeepAwakeGate gate = new KeepAwakeGate();
    private final AwakeHolders holders = new AwakeHolders(keepAwake, gate);

    @Test
    void hold_gateOn_starts() {
        holders.hold(ActivityKind.TRANSLATION);

        assertThat(calls).containsExactly("start");
    }

    @Test
    void gateOff_whileHeld_stopsAtOnce() {
        holders.hold(ActivityKind.TRANSLATION);

        gate.set(false);

        assertThat(calls).containsExactly("start", "stop");
    }

    @Test
    void hold_gateOff_noStart() {
        gate.set(false);

        holders.hold(ActivityKind.TRANSLATION);

        assertThat(calls).isEmpty();
    }

    @Test
    void gateOnAgain_whileHeld_starts() {
        gate.set(false);
        holders.hold(ActivityKind.TRANSLATION);

        gate.set(true);

        assertThat(calls).containsExactly("start");
    }

    @Test
    void gateOnAgain_noHolders_noStart() {
        gate.set(false);

        gate.set(true);

        assertThat(calls).isEmpty();
    }

    @Test
    void release_lastHolder_stops() {
        holders.hold(ActivityKind.TRANSLATION);

        holders.release(ActivityKind.TRANSLATION);

        assertThat(calls).containsExactly("start", "stop");
    }
}
