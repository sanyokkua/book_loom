package ua.bookloom.ui.state;

import java.util.List;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The observable state of {@link ModelForm}, apart from what changes it: the wrappers the form writes and the read-only
 * views everything else observes. FX thread only.
 */
abstract class ModelFormProperties {

    final ReadOnlyObjectWrapper<@Nullable ModelEntry> selected = new ReadOnlyObjectWrapper<>();
    final ReadOnlyObjectWrapper<ModelDraft> draft =
            new ReadOnlyObjectWrapper<>(ModelDraft.of(ModelSettings.defaults("", "")));
    final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper errorsShown = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper saveFailed = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper justSaved = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper reading = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper sendingAgain = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyBooleanWrapper refusedFailed = new ReadOnlyBooleanWrapper(false);
    final ReadOnlyObjectWrapper<List<MessageKey>> findings = new ReadOnlyObjectWrapper<>(List.of());
    final ReadOnlyObjectWrapper<ModelFacts> facts = new ReadOnlyObjectWrapper<>(ModelFacts.NONE);
    final ObservableList<RefusedSetting> refused = FXCollections.observableArrayList();

    /** The model whose settings are shown; null until models are listed. */
    public ReadOnlyObjectProperty<@Nullable ModelEntry> selected() {
        return selected.getReadOnlyProperty();
    }

    /** What the form holds now. */
    public ReadOnlyObjectProperty<ModelDraft> draft() {
        return draft.getReadOnlyProperty();
    }

    /** Whether the draft differs from what is saved. */
    public ReadOnlyBooleanProperty dirty() {
        return dirty.getReadOnlyProperty();
    }

    /** Whether a refused save asked for the findings to be shown. */
    public ReadOnlyBooleanProperty errorsShown() {
        return errorsShown.getReadOnlyProperty();
    }

    /** Whether a save is under way. */
    public ReadOnlyBooleanProperty saving() {
        return saving.getReadOnlyProperty();
    }

    /** Whether the last save failed in storage; the draft is untouched. */
    public ReadOnlyBooleanProperty saveFailed() {
        return saveFailed.getReadOnlyProperty();
    }

    /** Whether the last thing that happened to the form was a successful save, until the next change. */
    public ReadOnlyBooleanProperty justSaved() {
        return justSaved.getReadOnlyProperty();
    }

    /** Whether the chosen model's stored settings are still being read; the form shows defaults until they arrive. */
    public ReadOnlyBooleanProperty reading() {
        return reading.getReadOnlyProperty();
    }

    /** Whether a Send again is under way. */
    public ReadOnlyBooleanProperty sendingAgain() {
        return sendingAgain.getReadOnlyProperty();
    }

    /** Whether the last Send again failed in storage; the list is as it was. */
    public ReadOnlyBooleanProperty refusedFailed() {
        return refusedFailed.getReadOnlyProperty();
    }

    /** What stops a save; empty when the draft can be saved. */
    public ReadOnlyObjectProperty<List<MessageKey>> findings() {
        return findings.getReadOnlyProperty();
    }

    /** What the program says about the chosen model. */
    public ReadOnlyObjectProperty<ModelFacts> facts() {
        return facts.getReadOnlyProperty();
    }

    /** The settings the chosen model refused, as stored. */
    public ObservableList<RefusedSetting> refused() {
        return refused;
    }
}
