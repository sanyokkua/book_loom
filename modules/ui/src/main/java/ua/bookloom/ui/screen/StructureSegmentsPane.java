package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureSegmentRow;
import ua.bookloom.ui.state.StructureSegments;
import ua.bookloom.ui.state.StructureSegmentsViewModel;

/**
 * The structure screen's segment browser: a summary line, a text search and a kind filter, the list of the picked
 * part's segments with their planned chunks, and a reading pane with the picked segment in full.
 *
 * <p>The pane only follows the view model's state. The model outlives this node, so it is observed through a weak
 * listener held by the field below. The list is a virtualized {@link ListView} of fixed-height rows, so a chapter of
 * thousands of segments materialises only the rows the viewport shows.
 */
@Slf4j
final class StructureSegmentsPane extends VBox {

    private static final double SPACING = 8;
    private static final double ROW_HEIGHT = 34;
    private static final int MIN_ROWS = 6;
    private static final int PREFERRED_ROWS = 10;
    private static final double FRAME = 2;

    private final Messages messages;
    private final NumberFormat numbers;
    private final ObservableList<StructureSegmentRow> backing = FXCollections.observableArrayList();
    private final FilteredList<StructureSegmentRow> visible = new FilteredList<>(backing);
    private final ListView<StructureSegmentRow> list = new ListView<>(visible);
    private final Label header = new Label();
    private final Label shown = new Label();
    private final Label note = new Label();
    private final TextField search = new TextField();
    private final ComboBox<SegmentKind> kinds = new ComboBox<>();
    private final StructureTextPane reading;
    private final ChangeListener<StructureSegments> onState = (observed, was, now) -> show(now);
    private final ReadOnlyBooleanProperty technical;
    // The rows read the switch as they fill, so a change redraws them.
    private final ChangeListener<Boolean> onTechnical = (observed, was, now) -> list.refresh();
    private boolean isLoaded;
    private boolean isRelisting;
    private @Nullable String pickedId;

    StructureSegmentsPane(
            final StructureSegmentsViewModel model,
            final Messages messages,
            final StructureTextPane reading,
            final ReadOnlyBooleanProperty technical) {
        super(SPACING);
        this.technical = Objects.requireNonNull(technical, "technical");
        Objects.requireNonNull(model, "model");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.reading = Objects.requireNonNull(reading, "reading");
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
        header.setId("structure-segments-header");
        header.getStyleClass().add("muted");
        final StackPane stack = listStack();
        VBox.setVgrow(stack, Priority.ALWAYS);
        getChildren().addAll(header, filters(), stack);
        model.state().addListener(new WeakChangeListener<>(onState));
        technical.addListener(new WeakChangeListener<>(onTechnical));
        show(model.state().get());
    }

    private HBox filters() {
        search.setId("structure-segments-search");
        search.setPromptText(messages.get(MessageKey.STRUCTURE_SEARCH_PROMPT));
        Tips.install(messages, search, MessageKey.STRUCTURE_SEARCH_HINT);
        HBox.setHgrow(search, Priority.ALWAYS);
        kinds.setId("structure-segments-kind");
        kinds.setMinWidth(Region.USE_PREF_SIZE);
        kinds.setConverter(new StringConverter<>() {
            @Override
            public String toString(final @Nullable SegmentKind kind) {
                return messages.get(kind == null ? MessageKey.STRUCTURE_KIND_ALL : StructureKindLabels.of(kind));
            }

            @Override
            public @Nullable SegmentKind fromString(final @Nullable String label) {
                return null;
            }
        });
        Tips.install(messages, kinds, MessageKey.STRUCTURE_KIND_HINT);
        shown.setId("structure-segments-shown");
        shown.getStyleClass().add("muted");
        shown.managedProperty().bind(shown.visibleProperty());
        shown.setVisible(false);
        search.textProperty().addListener((observed, was, now) -> refilter());
        kinds.valueProperty().addListener((observed, was, now) -> refilter());
        final HBox row = new HBox(SPACING, search, kinds, shown);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private StackPane listStack() {
        list.setId("structure-segments");
        list.getStyleClass().add("structure-segments");
        list.setCellFactory(view -> new StructureSegmentCell(messages, numbers, technical));
        list.setMinHeight(MIN_ROWS * ROW_HEIGHT + FRAME);
        list.setPrefHeight(PREFERRED_ROWS * ROW_HEIGHT + FRAME);
        Tips.install(messages, list, MessageKey.STRUCTURE_SEGMENTS_HINT);
        list.getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> picked(now));
        note.setId("structure-segments-state");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMouseTransparent(true);
        note.managedProperty().bind(note.visibleProperty());
        return new StackPane(list, note);
    }

    // A row the list drops while it is filled again is no choice of the person's, so the picked segment and its text
    // stay until the new listing says whether it still holds that segment.
    private void picked(final @Nullable StructureSegmentRow row) {
        if (isRelisting && row == null) {
            return;
        }
        if (!isRelisting) {
            pickedId = row == null ? null : row.preview().segmentId();
        }
        reading.show(row);
    }

    private void show(final StructureSegments state) {
        log.debug("segment browser shows {}", state.getClass().getSimpleName());
        isLoaded = state instanceof StructureSegments.Loaded;
        isRelisting = true;
        try {
            switch (state) {
                case StructureSegments.Idle _ -> unlisted(MessageKey.STRUCTURE_SEGMENTS_PICK);
                case StructureSegments.Loading _ -> unlisted(MessageKey.STRUCTURE_SEGMENTS_LOADING);
                case StructureSegments.Failed _ -> unlisted(MessageKey.STRUCTURE_SEGMENTS_FAILED);
                case StructureSegments.Loaded loaded -> loaded(loaded);
            }
            if (!(state instanceof StructureSegments.Loading)) {
                restorePick();
            }
        } finally {
            isRelisting = false;
        }
    }

    // A listing asked again (a brief save, the checks) keeps the picked segment when it still lists it.
    private void restorePick() {
        final String wanted = pickedId;
        final StructureSegmentRow again = wanted == null
                ? null
                : visible.stream()
                        .filter(row -> row.preview().segmentId().equals(wanted))
                        .findFirst()
                        .orElse(null);
        log.debug("the picked segment {} is {}", wanted, again == null ? "no longer listed" : "listed again");
        if (again == null) {
            pickedId = null;
            list.getSelectionModel().clearSelection();
            reading.show(null);
            return;
        }
        list.getSelectionModel().select(again);
        list.scrollTo(again);
    }

    private void unlisted(final MessageKey why) {
        backing.clear();
        header.setText("");
        shown.setVisible(false);
        search.setDisable(true);
        kinds.setDisable(true);
        message(why);
    }

    private void loaded(final StructureSegments.Loaded loaded) {
        final SegmentKind chosen = kinds.getValue();
        backing.setAll(loaded.rows());
        final Set<SegmentKind> present = EnumSet.noneOf(SegmentKind.class);
        loaded.rows().forEach(row -> present.add(row.preview().kind()));
        final List<SegmentKind> items = new ArrayList<>();
        items.add(null);
        items.addAll(present);
        kinds.getItems().setAll(items);
        kinds.setValue(present.contains(chosen) ? chosen : null);
        header.setText(
                messages.get(MessageKey.STRUCTURE_SEGMENTS_HEADER, loaded.rows().size(), loaded.chunks()));
        Tips.install(messages, header, MessageKey.TRANSLATING_STRIP_DONE_TIP);
        search.setDisable(false);
        kinds.setDisable(false);
        refilter();
    }

    private void refilter() {
        if (!isLoaded) {
            return;
        }
        final String needle = search.getText() == null ? "" : search.getText();
        final SegmentKind kind = kinds.getValue();
        visible.setPredicate(row -> row.matches(needle, kind));
        shown.setVisible(!needle.isBlank() || kind != null);
        shown.setText(messages.get(MessageKey.STRUCTURE_SEGMENTS_SHOWN, visible.size(), backing.size()));
        if (backing.isEmpty()) {
            message(MessageKey.STRUCTURE_SEGMENTS_NONE);
        } else if (visible.isEmpty()) {
            final String wanted =
                    !needle.isBlank() || kind == null ? needle.strip() : messages.get(StructureKindLabels.of(kind));
            message(MessageKey.STRUCTURE_SEGMENTS_NO_MATCH, wanted);
        } else {
            message(null);
        }
    }

    private void message(final @Nullable MessageKey key, final Object... args) {
        note.setVisible(key != null);
        note.setText(key == null ? "" : messages.get(key, args));
    }
}
