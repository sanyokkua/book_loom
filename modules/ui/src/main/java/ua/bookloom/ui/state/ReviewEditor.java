package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The review panel's editor text and whether it has been changed: what the person is typing against the text the
 * segment was opened with. Kept apart from {@link ReviewViewModel} so that class stays a readable size. FX thread only.
 */
final class ReviewEditor {

    private final StringProperty text = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> hint = new ReadOnlyObjectWrapper<>();
    private final Runnable onChange;
    private String baseline = "";

    ReviewEditor(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        text.addListener((observed, was, now) -> refresh());
    }

    StringProperty text() {
        return text;
    }

    ReadOnlyBooleanProperty dirty() {
        return dirty.getReadOnlyProperty();
    }

    ReadOnlyObjectProperty<@Nullable MessageKey> hint() {
        return hint.getReadOnlyProperty();
    }

    /** Opens a segment: its edit when there is one, else its machine target, else its masked source (design D9). */
    void load(final SegmentView view) {
        baseline = editableText(view);
        text.set(baseline);
        refresh();
    }

    private static String editableText(final SegmentView view) {
        if (view.maskedUserTarget() != null) {
            return view.maskedUserTarget();
        }
        return view.maskedMachineTarget() != null ? view.maskedMachineTarget() : view.maskedSource();
    }

    private void refresh() {
        dirty.set(!text.get().equals(baseline));
        hint.set(dirty.get() ? MessageKey.REVIEW_EDITING_HINT : null);
        onChange.run();
    }
}
