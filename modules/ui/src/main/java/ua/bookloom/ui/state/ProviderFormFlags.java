package ua.bookloom.ui.state;

import javafx.beans.property.ReadOnlyBooleanWrapper;

/** The yes/no facts the Providers form reports about its last action; FX thread only. */
final class ProviderFormFlags {

    final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper justSaved = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper errorsShown = new ReadOnlyBooleanWrapper(false);

    /** Forgets the outcome of the last action, as any change to what the form shows does. */
    void clearFeedback() {
        errorsShown.set(false);
        saveFailed.set(false);
        justSaved.set(false);
    }
}
