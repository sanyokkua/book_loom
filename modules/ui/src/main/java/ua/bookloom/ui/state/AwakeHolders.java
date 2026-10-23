package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.KeepAwake;

/**
 * Counts the activities that ask the model, so the computer sleeps again only when the last of them has ended and one
 * ending never releases another's hold. FX thread only. A hold starts the computer awake only while the person's choice is on.
 */
@Slf4j
final class AwakeHolders {

    private final KeepAwake keepAwake;
    private final KeepAwakeGate gate;
    private int holders;
    private boolean started;

    AwakeHolders(final KeepAwake keepAwake, final KeepAwakeGate gate) {
        this.keepAwake = Objects.requireNonNull(keepAwake, "keepAwake");
        this.gate = Objects.requireNonNull(gate, "gate");
        gate.onChange(this::gateChanged);
        log.debug("keep-awake holders ready gateOn={}", gate.isOn());
    }

    void hold(final ActivityKind kind) {
        if (!kind.isCallingModel()) {
            return;
        }
        holders++;
        log.debug("keep-awake holders now {} after {} began", holders, kind);
        if (holders == 1) {
            startIfAllowed();
        }
    }

    void release(final ActivityKind kind) {
        if (!kind.isCallingModel()) {
            return;
        }
        holders--;
        log.debug("keep-awake holders now {} after {} ended", holders, kind);
        if (holders == 0) {
            stopIfStarted();
        }
    }

    private void gateChanged() {
        log.debug("keep-awake choice changed on={} holders={}", gate.isOn(), holders);
        if (gate.isOn() && holders > 0) {
            startIfAllowed();
        } else if (!gate.isOn()) {
            stopIfStarted();
        }
    }

    private void startIfAllowed() {
        if (gate.isOn() && !started) {
            started = true;
            keepAwake.start();
        }
    }

    private void stopIfStarted() {
        if (started) {
            started = false;
            keepAwake.stop();
        }
    }
}
