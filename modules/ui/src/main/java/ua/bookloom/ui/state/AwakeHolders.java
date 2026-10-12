package ua.bookloom.ui.state;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.KeepAwake;

/**
 * Counts the activities that ask the model, so the computer sleeps again only when the last of them has ended and one
 * ending never releases another's hold. FX thread only.
 */
@Slf4j
final class AwakeHolders {

    private final KeepAwake keepAwake;
    private int holders;

    AwakeHolders(final KeepAwake keepAwake) {
        this.keepAwake = Objects.requireNonNull(keepAwake, "keepAwake");
    }

    void hold(final ActivityKind kind) {
        if (!kind.isCallingModel()) {
            return;
        }
        holders++;
        log.debug("keep-awake holders now {} after {} began", holders, kind);
        if (holders == 1) {
            keepAwake.start();
        }
    }

    void release(final ActivityKind kind) {
        if (!kind.isCallingModel()) {
            return;
        }
        holders--;
        log.debug("keep-awake holders now {} after {} ended", holders, kind);
        if (holders == 0) {
            keepAwake.stop();
        }
    }
}
