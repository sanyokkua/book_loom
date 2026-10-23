package ua.bookloom.ui.state;

import java.util.List;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;

/** Keeps the advice for the open provider and its typed model up to date as either changes; FX thread only. */
@Slf4j
final class ModelNotesFollower {

    private final ReadOnlyStringProperty edited;
    private final StringProperty draftModel;
    private final ObservableList<ProviderRow> providers;
    private final ReadOnlyObjectWrapper<List<ModelAdvice.Note>> notes = new ReadOnlyObjectWrapper<>(List.of());

    ModelNotesFollower(
            final ReadOnlyStringProperty edited,
            final StringProperty draftModel,
            final ObservableList<ProviderRow> providers) {
        this.edited = edited;
        this.draftModel = draftModel;
        this.providers = providers;
        edited.addListener((observed, was, now) -> refresh());
        draftModel.addListener((observed, was, now) -> refresh());
        refresh();
    }

    ReadOnlyObjectProperty<List<ModelAdvice.Note>> notes() {
        return notes.getReadOnlyProperty();
    }

    private void refresh() {
        final String selected = edited.get();
        final List<ModelAdvice.Note> found = providers.stream()
                .filter(row -> row.id().equals(selected))
                .findFirst()
                .map(row -> ModelAdvice.notesFor(row.kind(), draftModel.get()))
                .orElse(List.of());
        log.debug("model notes for provider '{}': {}", selected, found.size());
        notes.set(found);
    }
}
