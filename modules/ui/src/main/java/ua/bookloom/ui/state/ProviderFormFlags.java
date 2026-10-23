package ua.bookloom.ui.state;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;

/** The yes/no facts the Providers form reports about its last action; FX thread only. */
@Slf4j
final class ProviderFormFlags {

    final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper justSaved = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper errorsShown = new ReadOnlyBooleanWrapper(false);

    /** Notes that storage refused what the form asked of it; the draft stays as typed. */
    void failed(final String what) {
        log.warn("provider storage failed: {}", what);
        saving.set(false);
        saveFailed.set(true);
    }

    /** Forgets the outcome of the last action, as any change to what the form shows does. */
    void clearFeedback() {
        errorsShown.set(false);
        saveFailed.set(false);
        justSaved.set(false);
    }
}
