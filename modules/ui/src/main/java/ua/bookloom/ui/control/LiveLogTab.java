package ua.bookloom.ui.control;

import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LogEntry;

/** The live card's Activity log tab: All or Problems only, Jump to latest while scrolled up, and the tagged log. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LiveLogTab {

    private static final double SPACING = 8;

    static VBox build(
            final ObservableList<LogEntry> entries, final ObservableBooleanValue technical, final Messages messages) {
        final TaggedLog logList = new TaggedLog(entries, technical, messages);
        logList.setId("translating-log");
        VBox.setVgrow(logList, Priority.ALWAYS);
        final Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        final HBox bar = new HBox(SPACING, spacer, jumpToLatest(logList, messages), filter(logList, messages));
        bar.setAlignment(Pos.CENTER_LEFT);
        final VBox tab = new VBox(SPACING, bar, logList);
        tab.getStyleClass().add("live-tab");
        return tab;
    }

    private static SegmentedButton filter(final TaggedLog logList, final Messages messages) {
        final ToggleButton all = Tips.install(
                messages, new ToggleButton(messages.get(MessageKey.LIVE_LOG_ALL)), MessageKey.LIVE_LOG_ALL_TIP);
        all.setId("translating-log-all");
        final ToggleButton problems = Tips.install(
                messages,
                new ToggleButton(messages.get(MessageKey.LIVE_LOG_PROBLEMS)),
                MessageKey.LIVE_LOG_PROBLEMS_TIP);
        problems.setId("translating-log-problems");
        final SegmentedButton filter = new SegmentedButton(all, problems);
        final ToggleGroup group = new ToggleGroup();
        filter.setToggleGroup(group);
        group.selectToggle(all);
        // The segmented button puts its buttons in the group only when its skin is made, which drops the choice.
        filter.skinProperty()
                .addListener((observed, was, now) ->
                        group.selectToggle(logList.problemsOnlyProperty().get() ? problems : all));
        group.selectedToggleProperty().addListener((observed, was, now) -> {
            if (now == null) {
                group.selectToggle(was);
                return;
            }
            final boolean onlyProblems = problems.isSelected();
            log.debug("activity log: {} chosen", onlyProblems ? "Problems only" : "All");
            logList.problemsOnlyProperty().set(onlyProblems);
        });
        return filter;
    }

    // Offered only while the person has scrolled the log up, which stops it following its newest line.
    private static Button jumpToLatest(final TaggedLog logList, final Messages messages) {
        final Button jump = Tips.install(
                messages,
                new Button(messages.get(MessageKey.TRANSLATING_LOG_JUMP)),
                MessageKey.TRANSLATING_LOG_JUMP_TIP);
        jump.setId("translating-log-jump");
        jump.getStyleClass().add("btn-ghost");
        jump.visibleProperty().bind(logList.followingProperty().not());
        jump.managedProperty().bind(jump.visibleProperty());
        jump.setOnAction(event -> logList.jumpToLatest());
        return jump;
    }
}
