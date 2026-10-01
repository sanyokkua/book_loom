package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The review panel's editor text and whether it has been changed: what the person is typing against the text the
 * segment was opened with, what that text is when the segment kept no translation, and the formatting tokens a refused
 * save still needs. Kept apart from {@link ReviewViewModel} so that class stays a readable size. FX thread only.
 */
@Slf4j
public final class ReviewEditor {

    private final StringProperty text = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper savable = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> hint = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> targetNote = new ReadOnlyObjectWrapper<>();
    private final ObservableList<String> neededTokens = FXCollections.observableArrayList();
    private final ObservableList<String> readOnlyNeeded = FXCollections.unmodifiableObservableList(neededTokens);
    private final ReadOnlyStringWrapper extraTokens = new ReadOnlyStringWrapper("");
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
     * The hint that editing has switched Accept off.
     *
     * @return a read-only property holding {@code null} while the editor is clean; FX thread only
     */
    public ReadOnlyObjectProperty<@Nullable MessageKey> hint() {
        return hint.getReadOnlyProperty();
    }

    /**
     * Whether Save edit has something to save: a change, or the model's refused reply, which a save repairs if it can.
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
     * The formatting tokens a refused save lacked, each once per missing occurrence, until the text holds them all.
     *
     * @return an unmodifiable list, empty when no save was refused for a missing token; FX thread only
     */
    public ObservableList<String> neededTokens() {
        return readOnlyNeeded;
    }

    /**
     * The formatting tokens a refused save held too often or that the source lacks, joined for display.
     *
     * @return a read-only property holding the empty string when there are none; FX thread only
     */
    public ReadOnlyStringProperty extraTokens() {
        return extraTokens.getReadOnlyProperty();
    }

    /** Opens a segment: its edit, else its machine target, else the model's refused reply, else its masked source. */
    void load(final SegmentView view) {
        loadedId = view.segmentId();
        source = view.maskedSource();
        noTarget = view.maskedUserTarget() == null && view.maskedMachineTarget() == null;
        fromRejected = noTarget && view.rejectedTarget() != null;
        targetNote.set(
                noTarget ? (fromRejected ? MessageKey.REVIEW_TARGET_REJECTED : MessageKey.REVIEW_TARGET_SOURCE) : null);
        log.debug("editor loads segment {} noTarget={} fromRejected={}", loadedId, noTarget, fromRejected);
        baseline = editableText(view);
        neededTokens.clear();
        extraTokens.set("");
        text.set(baseline);
        refresh();
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

    /** Names the tokens the refused text lacks or holds too often against the segment's source. */
    void saveRefused() {
        final List<String> missing = ReviewTokens.missing(source, text.get());
        final List<String> extra = ReviewTokens.missing(text.get(), source);
        log.debug("save refused for segment {}: tokens missing={} extra={}", loadedId, missing, extra);
        neededTokens.setAll(missing);
        extraTokens.set(String.join(" ", extra));
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
        savable.set((dirty.get() || fromRejected) && !(noTarget && text.get().equals(source)));
        hint.set(dirty.get() ? MessageKey.REVIEW_EDITING_HINT : null);
        if (!neededTokens.isEmpty() || !extraTokens.get().isEmpty()) {
            neededTokens.setAll(ReviewTokens.missing(source, text.get()));
            extraTokens.set(String.join(" ", ReviewTokens.missing(text.get(), source)));
        }
        onChange.run();
    }
}
