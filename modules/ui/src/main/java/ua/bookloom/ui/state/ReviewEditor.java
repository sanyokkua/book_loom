package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The review desk's editor text and whether it has been changed: what the person is typing against the text the
 * segment was opened with, what that text is when the segment kept no translation, and which formatting marks the text
 * lacks or holds twice. The text is in the number form ({@code ⟦0⟧}); {@link #storedText()} maps it back. Kept apart from {@link ReviewViewModel} so that class stays a readable size. FX thread only.
 */
@Slf4j
public final class ReviewEditor {

    private final StringProperty text = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper savable = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> targetNote = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper changed = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper blank = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<MarkStatus> marks =
            new ReadOnlyObjectWrapper<>(new MarkStatus(List.of(), List.of()));
    private final ReadOnlyObjectWrapper<List<String>> sourceMarks = new ReadOnlyObjectWrapper<>(List.of());
    private final Runnable onChange;
    private String baseline = "";
    private String source = "";
    private @Nullable String loadedId;
    private boolean fromRejected;
    private boolean noTarget;

    ReviewEditor(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
        text.addListener((observed, was, now) -> refresh());
    }

    StringProperty text() {
        return text;
    }

    /**
     * Whether the editor differs from the text the segment was opened with.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty dirty() {
        return dirty.getReadOnlyProperty();
    }

    /**
     * Whether Save my edit has something to save and can: a change or the model's refused reply, not blank, with every
     * mark in place.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty savable() {
        return savable.getReadOnlyProperty();
    }

    /**
     * What the editor shows when the segment kept no translation: the model's refused reply, or the source.
     *
     * @return a read-only property holding {@code null} when the editor shows a kept translation; FX thread only
     */
    public ReadOnlyObjectProperty<@Nullable MessageKey> targetNote() {
        return targetNote.getReadOnlyProperty();
    }

    /**
     * Whether the text differs from what the segment was opened with, or is a refused reply only a save keeps.
     *
     * @return a read-only observable; FX thread only
     */
    public ObservableValue<Boolean> changed() {
        return changed.getReadOnlyProperty();
    }

    /**
     * Whether the text is empty or only spaces, which Save never stores: it would export an empty paragraph.
     *
     * @return a read-only observable; FX thread only
     */
    public ObservableValue<Boolean> blank() {
        return blank.getReadOnlyProperty();
    }

    /**
     * The marks the text lacks or holds twice against the segment's source.
     *
     * @return a read-only observable, complete while a segment has no marks; FX thread only
     */
    public ObservableValue<MarkStatus> marks() {
        return marks.getReadOnlyProperty();
    }

    /**
     * The marks the segment's source holds, in order; separate from {@link #marks()} because a segment with none and a
     * segment with every one in place are both complete.
     *
     * @return a read-only observable, empty when the source holds no marks; FX thread only
     */
    public ObservableValue<List<String>> sourceMarks() {
        return sourceMarks.getReadOnlyProperty();
    }

    /**
     * The text as it is stored, with the number form turned back into {@code ⟦gN⟧}.
     *
     * @return the editor's text in the stored form
     */
    public String storedText() {
        return MarkStatus.store(text.get());
    }

    /**
     * The segment's source in the number form, for the editor's filter.
     *
     * @return a supplier reading the source of the segment now open
     */
    public Supplier<String> sourceDisplay() {
        return () -> source;
    }

    /** Opens a segment: its edit, else its machine target, else the model's refused reply, else its masked source. */
    void load(final SegmentView view) {
        loadedId = view.segmentId();
        source = MarkStatus.show(view.maskedSource());
        noTarget = view.maskedUserTarget() == null && view.maskedMachineTarget() == null;
        fromRejected = noTarget && view.rejectedTarget() != null;
        targetNote.set(
                noTarget ? (fromRejected ? MessageKey.REVIEW_TARGET_REJECTED : MessageKey.REVIEW_TARGET_SOURCE) : null);
        log.debug(
                "editor loads segment {} noTarget={} fromRejected={} sourceMarks={}",
                loadedId,
                noTarget,
                fromRejected,
                MarkStatus.marksOf(source));
        sourceMarks.set(MarkStatus.marksOf(source));
        baseline = MarkStatus.show(editableText(view));
        text.set(baseline);
        refresh();
    }

    /** Drops what the person typed: the editor goes back to the text the segment was opened with. FX thread only. */
    public void discard() {
        log.debug("typing in segment {} discarded", loadedId);
        text.set(baseline);
    }

    /**
     * Whether re-reading {@code view} would throw away what the person typed into it: the same segment, changed.
     *
     * @param view the non-null segment about to be shown
     * @return {@code true} if the editor holds unsaved typing for this segment
     */
    boolean holdsTypingFor(final SegmentView view) {
        return dirty.get() && view.segmentId().equals(loadedId);
    }

    private static String editableText(final SegmentView view) {
        if (view.maskedUserTarget() != null) {
            return view.maskedUserTarget();
        }
        if (view.maskedMachineTarget() != null) {
            return view.maskedMachineTarget();
        }
        return view.rejectedTarget() != null ? view.rejectedTarget() : view.maskedSource();
    }

    // No line is logged here: it runs on every keystroke.
    private void refresh() {
        dirty.set(!text.get().equals(baseline));
        // A segment with no translation saved as its own source would read as reviewed by the person: never offered.
        changed.set(dirty.get() || fromRejected);
        marks.set(MarkStatus.of(source, text.get()));
        blank.set(text.get().isBlank());
        savable.set(changed.get()
                && !blank.get()
                && marks.get().isComplete()
                && !(noTarget && text.get().equals(source)));
        onChange.run();
    }
}
