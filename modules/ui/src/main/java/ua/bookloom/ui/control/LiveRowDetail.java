package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.RowMarker;
import ua.bookloom.ui.state.SectionMemory;

/**
 * The detail of the row selected in the live card. Simple shows its marker and location, why a flagged one waits, its
 * source beside its translation, and what the model works on now. Detailed shows the same head and texts, then the
 * call the row was sent in (its prompt and reply), or that it has not been sent yet.
 *
 * <p>It is rebuilt only when what it shows changed — the row, its marker, its call, the call in flight or the view —
 * so a tick that only moves a clock changes nothing here. Showing logs nothing, as it runs on every publication.
 */
public final class LiveRowDetail extends VBox {

    private static final double SPACING = 10;
    private static final String PLACEHOLDER = "live-placeholder";
    private static final double[] HALVES = {1, 1};

    private final Messages messages;
    private final HBox head = new HBox(SPACING);
    private final Label locator = new Label();
    private final Label why = new Label();
    private final Label source = new Label();
    private final Label target = new Label();
    private final VBox inFlight = new VBox(4);
    private final Label notSent = new Label();
    private final LiveCallView call;
    private @Nullable Shown shown;

    /** What the detail shows; equal values need no redraw. */
    private record Shown(
            @Nullable LiveRow row,
            @Nullable RowMarker marker,
            @Nullable CallSnapshot call,
            @Nullable CallSnapshot inFlight,
            boolean detailed) {}

    /**
     * Builds an empty detail.
     *
     * @param sourceName the heading of the source column, already translated
     * @param targetName the heading of the translation column, already translated
     * @param memory the session's memory of open sections, which keeps the call's prompt open across visits
     * @param messages the catalogue the detail is worded from
     */
    public LiveRowDetail(
            final ObservableValue<String> sourceName,
            final ObservableValue<String> targetName,
            final SectionMemory memory,
            final Messages messages) {
        super(SPACING);
        this.messages = Objects.requireNonNull(messages, "messages");
        call = new LiveCallView("translating-live-call", messages);
        call.rememberSectionsIn(Objects.requireNonNull(memory, "memory"));
        setId("translating-live-detail");
        getStyleClass().add("live-detail");
        locator.getStyleClass().add("live-detail-locator");
        why.setId("translating-live-detail-why");
        why.setWrapText(true);
        head.setAlignment(Pos.CENTER_LEFT);
        notSent.setText(messages.get(MessageKey.LIVE_DETAIL_NOT_SENT));
        notSent.getStyleClass().add("muted");
        notSent.setWrapText(true);
        inFlight.setId("translating-live-inflight");
        inFlight.getStyleClass().add("live-inflight");
        getChildren().addAll(head, texts(sourceName, targetName), inFlight, notSent, call);
        show(null, null, LiveCalls.EMPTY, false);
    }

    private GridPane texts(final ObservableValue<String> sourceName, final ObservableValue<String> targetName) {
        final GridPane grid = ReadOnlyGrid.of("translating-live-detail-texts", HALVES);
        final Label sourceHead = new Label();
        sourceHead.textProperty().bind(Objects.requireNonNull(sourceName, "sourceName"));
        final Label targetHead = new Label();
        targetHead.textProperty().bind(Objects.requireNonNull(targetName, "targetName"));
        for (final Label label : List.of(sourceHead, targetHead)) {
            label.getStyleClass().add("stat-caption");
        }
        for (final Label label : List.of(source, target)) {
            label.setWrapText(true);
            label.getStyleClass().add("live-detail-text");
        }
        grid.addRow(0, sourceHead, targetHead);
        grid.addRow(1, source, target);
        grid.setHgap(SPACING);
        grid.setVgap(SPACING / 2);
        return grid;
    }

    /**
     * Shows a row.
     *
     * @param row the selected row, or {@code null} when none is
     * @param marker the row's marker as the list draws it, or {@code null} with no row
     * @param calls the published calls, which hold the row's call and the call in flight
     * @param detailed whether the Detailed view is chosen
     */
    public void show(
            final @Nullable LiveRow row,
            final @Nullable RowMarker marker,
            final LiveCalls calls,
            final boolean detailed) {
        Objects.requireNonNull(calls, "calls");
        final CallSnapshot current = calls.current();
        final CallSnapshot waiting = current != null && current.state() == CallState.WAITING ? current : null;
        final Shown next = new Shown(row, marker, callOf(row, calls), waiting, detailed);
        if (next.equals(shown)) {
            return;
        }
        shown = next;
        showHead(row, marker);
        showTexts(row, marker);
        Visibility.show(inFlight, !detailed);
        showInFlight(waiting);
        final boolean sent = next.call() != null;
        Visibility.show(notSent, detailed && row != null && !sent);
        call.show(detailed ? next.call() : null);
    }

    private void showHead(final @Nullable LiveRow row, final @Nullable RowMarker marker) {
        if (row == null || marker == null) {
            head.getChildren().clear();
            return;
        }
        locator.setText(row.locator());
        final boolean toReview = marker == RowMarker.TO_REVIEW;
        why.setText(toReview ? messages.get(MessageKey.LIVE_DETAIL_WHY, reasonOf(row, marker)) : "");
        Visibility.show(why, toReview);
        head.getChildren().setAll(StateMarker.of(marker.meaning(), messages.get(marker.word())), locator, why);
    }

    private String reasonOf(final LiveRow row, final RowMarker marker) {
        return row.findingKinds().isEmpty() ? messages.get(marker.word()) : String.join(", ", row.findingKinds());
    }

    private void showTexts(final @Nullable LiveRow row, final @Nullable RowMarker marker) {
        final String shownSource = row == null ? "" : row.displaySource();
        // A segment of formatting only (an image, a formula, a rule) has no words once its marks are hidden.
        final boolean noWords = row != null && shownSource.isBlank();
        show(source, noWords ? messages.get(MessageKey.LIVE_DETAIL_NO_WORDS) : shownSource, noWords);
        final String translated = row == null ? null : row.target();
        if (marker == RowMarker.KEPT) {
            final String kept = translated == null || translated.isBlank() ? shownSource : translated;
            show(target, kept.isBlank() ? messages.get(MessageKey.LIVE_DETAIL_NO_WORDS) : kept, kept.isBlank());
            return;
        }
        if (translated != null && marker != RowMarker.TRANSLATING) {
            show(target, translated, false);
            return;
        }
        show(
                target,
                messages.get(
                        marker == RowMarker.TRANSLATING
                                ? MessageKey.LIVE_DETAIL_PENDING
                                : MessageKey.LIVE_DETAIL_NOT_YET),
                true);
    }

    private static void show(final Label label, final String text, final boolean placeholder) {
        label.getStyleClass().remove(PLACEHOLDER);
        if (placeholder) {
            label.getStyleClass().add(PLACEHOLDER);
        }
        label.setText(text);
    }

    private void showInFlight(final @Nullable CallSnapshot waiting) {
        final Label heading = new Label(messages.get(MessageKey.LIVE_DETAIL_IN_FLIGHT));
        heading.getStyleClass().add("stat-caption");
        inFlight.getChildren().setAll(heading);
        if (waiting == null || waiting.segments().isEmpty()) {
            final Label none = new Label(messages.get(MessageKey.LIVE_DETAIL_IN_FLIGHT_NONE));
            none.getStyleClass().add("muted");
            inFlight.getChildren().add(none);
            return;
        }
        for (final CallSegment segment : waiting.segments()) {
            final Label line = new Label(segment.locator() + "  " + segment.displaySource());
            line.setWrapText(true);
            inFlight.getChildren().add(line);
        }
    }

    // The row's own call, wherever the run keeps it: the call in flight, the one before, or the history.
    private static @Nullable CallSnapshot callOf(final @Nullable LiveRow row, final LiveCalls calls) {
        final Long id = row == null ? null : row.callId();
        if (id == null) {
            return null;
        }
        final CallSnapshot current = calls.current();
        if (current != null && current.callId() == id) {
            return current;
        }
        final CallSnapshot previous = calls.previous();
        if (previous != null && previous.callId() == id) {
            return previous;
        }
        return calls.recent().stream()
                .filter(held -> held.callId() == id)
                .findFirst()
                .orElse(null);
    }
}
