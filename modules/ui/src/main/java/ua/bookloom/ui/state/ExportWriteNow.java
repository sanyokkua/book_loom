package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.dialog.ConfirmDialog;

/**
 * "Write the file now" on the export's progress dialog: asks first with what the last check has reread so far, and on
 * yes ends the check and writes the file with the fixes made. FX thread only.
 */
@Slf4j
@Singleton
public final class ExportWriteNow {

    private final ExportViewModel exports;
    private final ConfirmDialog confirm;

    /**
     * Creates the action.
     *
     * @param exports the export whose last check is ended
     * @param confirm the question asked first
     */
    @Inject
    public ExportWriteNow(final ExportViewModel exports, final ConfirmDialog confirm) {
        this.exports = Objects.requireNonNull(exports, "exports");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
    }

    /** Asks whether to write the file now, with the tally so far. */
    public void ask() {
        final ExportActivity.RereadTally tally = exports.rereadTally();
        log.debug("write now pressed: {} of {} reread, {} fixed", tally.reread(), tally.total(), tally.fixed());
        if (tally.total() == 0) {
            confirm.ask(ConfirmDialog.Question.WRITE_NOW_EARLY, exports::writeNow);
            return;
        }
        final int rest = Math.max(0, tally.total() - tally.reread());
        confirm.ask(
                ConfirmDialog.Question.WRITE_NOW,
                List.of(tally.reread(), tally.total(), tally.fixed(), rest),
                exports::writeNow);
    }
}
