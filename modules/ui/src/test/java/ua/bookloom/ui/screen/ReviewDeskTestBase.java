package ua.bookloom.ui.screen;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.ReviewDeskFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.CurrentProject;
import ua.bookloom.ui.state.FlaggedRow;
import ua.bookloom.ui.state.ReviewRow;
import ua.bookloom.ui.state.RunState;

/**
 * What the review desk tests share: scripting the recording desk for each filter, publishing the run so the desk's
 * count is read, opening the desk the way a person does, and reading its rows, its More menu and its keys.
 *
 * <p>Node ids the desk must carry: {@code review-desk} (the card), {@code review-list}, {@code review-filter},
 * {@code review-prev}/{@code review-next}, {@code review-main}, {@code review-more} (a button whose context menu holds
 * items {@code review-more-later|retry|note|undo}), {@code review-close}, {@code review-target}, {@code review-source},
 * {@code review-locator}, {@code review-desk-left}, {@code review-readonly}, {@code review-pause}.
 */
abstract class ReviewDeskTestBase extends TranslatingScreenTestBase {

    static final String REVIEW = "translating-review-flagged";
    static final String DESK = "review-desk";

    /** Scripts every filter's list, the segments each list holds and the counts the desk answers. */
    void script(final List<SegmentView> toReview, final List<SegmentView> secondLook) {
        desk.willAnswerQueue(ReviewFilter.ALL_FLAGGED, toReview);
        desk.willAnswerQueue(ReviewFilter.SUSPICIOUS, secondLook);
        toReview.forEach(desk::willAnswerSegment);
        secondLook.forEach(desk::willAnswerSegment);
        desk.willAnswerCounts(ReviewDeskFixtures.counts(toReview.size(), secondLook.size()));
    }

    void publishFlagged(final int count) {
        mirror().live()
                .publishFlaggedQueue(IntStream.range(0, count)
                        .mapToObj(i -> new FlaggedRow("s-" + i, "ch7 · p" + i, List.of(), null))
                        .toList());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Shows Translating on a paused run holding these segments to review, with the Review control already counted. */
    void pausedWith(final List<SegmentView> toReview, final List<SegmentView> secondLook) throws Exception {
        readyToStart();
        script(toReview, secondLook);
        showTranslating();
        publish(RunState.PAUSED);
        publishFlagged(toReview.size());
        mirror().live().publishSuspicious(secondLook.size());
        // Review (n) counts both lists, in the window's language.
        final String count = "(" + (toReview.size() + secondLook.size()) + ")";
        awaitFx(() -> button(REVIEW).getText().endsWith(count));
    }

    /** As {@link #pausedWith}, then presses Review (n) and waits for the desk and its first row. */
    void openDeskWith(final List<SegmentView> toReview, final List<SegmentView> secondLook) throws Exception {
        pausedWith(toReview, secondLook);
        onFx(() -> button(REVIEW).fire());
        awaitFx(() -> isShown(DESK) && !rowList().getItems().isEmpty());
        awaitFx(() -> !((TextArea) required("review-target")).getText().isEmpty());
    }

    void openDesk() throws Exception {
        openDeskWith(ReviewDeskFixtures.sixToReview(), List.of());
    }

    /** Runs the book to its end with these segments to review, the way a finished run reaches the window. */
    void finishRunWith(final List<SegmentView> toReview) throws Exception {
        readyToStart();
        script(toReview, List.of());
        showTranslating();
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
        publishFlagged(toReview.size());
        mirror().publishOutcome(RunState.COMPLETED, completedReport(), null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    static <T> T fx(final java.util.function.Supplier<T> read) {
        return ThemeTestSupport.onFx(read);
    }

    @SuppressWarnings("unchecked")
    ListView<ReviewRow> rowList() {
        return (ListView<ReviewRow>) required("review-list");
    }

    /** The text of every filled row of the list, in order, each a locator then its line. */
    List<String> rowTexts() {
        WaitForAsyncUtils.waitForFxEvents();
        return fx(() -> rowList().lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(cell -> cell.getItem() != null)
                .sorted(java.util.Comparator.comparingInt(ListCell::getIndex))
                .map(cell -> ((ReviewRow) cell.getItem()).locator())
                .toList());
    }

    /** The word of the state marker in every filled row, in row order. */
    List<String> markerWords() {
        WaitForAsyncUtils.waitForFxEvents();
        return fx(() -> rowList().lookupAll(".list-cell").stream()
                .map(node -> (ListCell<?>) node)
                .filter(cell -> cell.getItem() != null)
                .sorted(java.util.Comparator.comparingInt(ListCell::getIndex))
                .map(cell -> ((javafx.scene.control.Labeled) cell.lookup(".state-marker")).getText())
                .toList());
    }

    @SuppressWarnings("unchecked")
    ComboBox<ReviewFilter> filterBox() {
        return (ComboBox<ReviewFilter>) required("review-filter");
    }

    void chooseFilter(final ReviewFilter filter) throws TimeoutException {
        onFx(() -> filterBox().getSelectionModel().select(filter));
        WaitForAsyncUtils.waitForFxEvents();
        awaitFx(() -> filterBox().getValue() == filter);
    }

    String selectedLocator() {
        return labelText("review-locator");
    }

    TextArea target() {
        return (TextArea) required("review-target");
    }

    void type(final String text) {
        onFx(() -> target().setText(text));
        WaitForAsyncUtils.waitForFxEvents();
    }

    Button main() {
        return button("review-main");
    }

    MenuItem moreItem(final String id) {
        return fx(() -> button("review-more").getContextMenu().getItems().stream()
                .filter(item -> id.equals(item.getId()))
                .findFirst()
                .orElseThrow());
    }

    void chooseMore(final String id) {
        onFx(() -> moreItem(id).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** Sends a key press to {@code target}, the way the keyboard does, through the scene's filters. */
    void key(final Node target, final KeyCode code, final boolean ctrl, final boolean alt, final boolean meta) {
        onFx(() -> Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, ctrl, alt, meta)));
        WaitForAsyncUtils.waitForFxEvents();
    }

    String projectId() {
        return fx(() -> injector.getInstance(CurrentProject.class).book().get().projectId());
    }

    /** The desk calls that decide a segment, so a test can tell "nothing was sent" from "something was". */
    List<String> actionCalls() {
        return desk.calls().stream()
                .filter(call ->
                        !call.startsWith("queue(") && !call.startsWith("segment(") && !call.startsWith("counts("))
                .toList();
    }
}
