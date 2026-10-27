package ua.bookloom.ui.control;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.project.RollingSummary;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.LiveCalls;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.SectionMemory;
import ua.bookloom.ui.state.WaitingCall;

/**
 * The live card of the Translating screen, as tall as the window leaves it: a Live tab with the chunk's rows beside
 * the selected row's detail and the scene summary under it, and an Activity log tab.
 *
 * <p>The Live tab chooses Simple or Detailed (Detailed at first when technical details are on), follows each newly
 * translated segment unless the person picks one, and lists every kept call on "All calls…". Its call line reads as the
 * run-state row does, a slow call's wait and a stuck call's one clock included. The mode and the follow switch are kept
 * for the session in {@link SectionMemory}, so a visit to another screen keeps them.
 *
 * <p>The mirror is observed through weak listeners that this card's fields keep alive, so a card replaced on the next
 * visit is not kept alive by the mirror. Showing logs nothing, as it runs on every publication; a person's actions log.
 */
@Slf4j
public final class LiveCallPanel extends VBox {

    /** The live card is never shorter than this, so its list shows a chunk and its detail a pair of texts. */
    public static final double MIN_HEIGHT = 460;

    private static final double SPACING = 8;
    private static final double LIST_MIN = 240;
    private static final double LIST_SHARE = 0.34;
    private static final String MODE_CHOSEN = "translating-live-mode-chosen";
    private static final String DETAILED_KEY = "translating-live-detailed";
    private static final String NO_FOLLOW_KEY = "translating-live-no-follow";

    /**
     * What the card follows.
     *
     * @param calls the mirror's live calls
     * @param runs the run-state row
     * @param waiting the call that waits, or {@code null}
     * @param log the mirror's activity log
     * @param technical whether technical details are on
     * @param summary the scene summary's text, holding {@code null} while there is none
     */
    public record Sources(
            ReadOnlyObjectProperty<LiveCalls> calls,
            ReadOnlyObjectProperty<RunStateView> runs,
            ReadOnlyObjectProperty<@Nullable WaitingCall> waiting,
            ObservableList<LogEntry> log,
            ObservableBooleanValue technical,
            ObservableValue<@Nullable String> summary) {}

    private final Sources sources;
    private final Messages messages;
    private final SectionMemory memory;
    private final LiveRowList list;
    private final LiveRowDetail detail;
    private final Label line = new Label();
    private final Label clock = new FigureLabel();
    private final BooleanProperty detailed = new SimpleBooleanProperty();
    private final TabPane tabs = new TabPane();
    private final InvalidationListener onChange = observed -> show();
    // True while show() runs: the list's own selection by code must not start a second, nested show.
    private boolean showing;

    /**
     * Builds the card and shows what the mirror already holds.
     *
     * @param sources what the card follows
     * @param names the headings of the source and translation columns, already translated
     * @param memory the session's memory of the open sections, the mode and the follow switch
     * @param allCalls opens "All calls…" on the kept calls, oldest first
     * @param messages the catalogue the card is worded from
     */
    public LiveCallPanel(
            final Sources sources,
            final List<ObservableValue<String>> names,
            final SectionMemory memory,
            final Consumer<List<CallSnapshot>> allCalls,
            final Messages messages) {
        super(SPACING);
        this.sources = Objects.requireNonNull(sources, "sources");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.memory = Objects.requireNonNull(memory, "memory");
        Objects.requireNonNull(allCalls, "allCalls");
        list = new LiveRowList(messages);
        detail = new LiveRowDetail(names.get(0), names.get(1), memory, messages);
        detailed.set(
                memory.isOpen(MODE_CHOSEN)
                        ? memory.isOpen(DETAILED_KEY)
                        : sources.technical().get());
        list.followSwitchProperty().set(!memory.isOpen(NO_FOLLOW_KEY));
        setId("translating-live-card");
        getStyleClass().addAll("card", "live-card");
        setMinHeight(MIN_HEIGHT);
        VBox.setVgrow(this, Priority.ALWAYS);
        addTabs(allCalls);
        listen();
        show();
    }

    // The tab pane is only the tab strip: the two bodies are laid out by this card, which hands them their width, so a
    // wrapped text measures its real height and an opened prompt grows the card instead of being clipped.
    private void addTabs(final Consumer<List<CallSnapshot>> allCalls) {
        final VBox liveBody = liveTab(allCalls);
        final VBox logBody = LiveLogTab.build(sources.log(), sources.technical(), messages);
        tabs.getTabs()
                .setAll(
                        new Tab(messages.get(MessageKey.LIVE_TAB_LIVE)),
                        new Tab(messages.get(MessageKey.TRANSLATING_LOG_TITLE)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setId("translating-live-tabs");
        tabs.getStyleClass().add("tab-strip");
        tabs.setMinHeight(Region.USE_PREF_SIZE);
        tabs.setMaxHeight(Region.USE_PREF_SIZE);
        showTab(liveBody, 0);
        showTab(logBody, 1);
        tabs.getSelectionModel()
                .selectedIndexProperty()
                .addListener((observed, was, now) -> LiveCallPanel.log.debug("live card: tab {} chosen", now));
        getChildren().addAll(tabs, liveBody, logBody);
    }

    private void showTab(final VBox body, final int index) {
        VBox.setVgrow(body, Priority.ALWAYS);
        Visibility.bind(body, tabs.getSelectionModel().selectedIndexProperty().isEqualTo(index));
    }

    private void listen() {
        for (final Observable source : List.of(sources.calls(), sources.runs(), sources.waiting(), detailed)) {
            source.addListener(new WeakInvalidationListener(onChange));
        }
        list.getSelectionModel().selectedItemProperty().addListener(onChange);
    }

    private VBox liveTab(final Consumer<List<CallSnapshot>> allCalls) {
        line.setId("translating-live-line");
        line.getStyleClass().add("live-line");
        line.setWrapText(true);
        HBox.setHgrow(line, Priority.ALWAYS);
        clock.setId("translating-live-clock");
        clock.getStyleClass().add("muted");
        final HBox callLine = new HBox(SPACING, line, clock);
        callLine.setAlignment(Pos.CENTER_LEFT);
        list.setMinWidth(LIST_MIN);
        list.prefWidthProperty().bind(widthProperty().multiply(LIST_SHARE));
        list.maxWidthProperty().bind(widthProperty().multiply(LIST_SHARE));
        final VBox side = new VBox(SPACING, detail, summaryLine());
        side.setMinWidth(0);
        HBox.setHgrow(side, Priority.ALWAYS);
        // The list and the detail stay side by side at every width the window allows; the list keeps its 240 pixels.
        final HBox split = new HBox(SPACING * 2, list, side);
        split.setId("translating-live-split");
        VBox.setVgrow(split, Priority.ALWAYS);
        final VBox tab = new VBox(SPACING, toolbar(allCalls), callLine, split);
        tab.getStyleClass().add("live-tab");
        return tab;
    }

    private FlowPane toolbar(final Consumer<List<CallSnapshot>> allCalls) {
        // The controls wrap onto a second line in a narrow window instead of pushing the card past its edge.
        final FlowPane bar =
                new FlowPane(SPACING, SPACING, modes(), followSwitch(), followAgain(), allCallsButton(allCalls));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setRowValignment(VPos.CENTER);
        return bar;
    }

    private SegmentedButton modes() {
        final ToggleButton simple = modeButton("translating-live-mode-simple", MessageKey.PROGRESS_VIEW_SIMPLE);
        final ToggleButton detail = modeButton("translating-live-mode-detailed", MessageKey.PROGRESS_VIEW_DETAILED);
        final SegmentedButton mode = new SegmentedButton(simple, detail);
        mode.setMinWidth(Region.USE_PREF_SIZE);
        mode.setToggleGroup(new ToggleGroup());
        mode.getToggleGroup().selectToggle(detailed.get() ? detail : simple);
        // The segmented button puts its buttons in the group only when its skin is made, which drops a choice made
        // before; it is made again then.
        mode.skinProperty()
                .addListener(
                        (observed, was, now) -> mode.getToggleGroup().selectToggle(detailed.get() ? detail : simple));
        mode.getToggleGroup().selectedToggleProperty().addListener((observed, was, now) -> {
            if (now == null) {
                // A segmented control lets its chosen button be pressed off; the view always has one of the two.
                mode.getToggleGroup().selectToggle(was);
                return;
            }
            final boolean choseDetailed = detail.isSelected();
            log.debug("live card: {} view chosen", choseDetailed ? "Detailed" : "Simple");
            memory.remember(MODE_CHOSEN, true);
            memory.remember(DETAILED_KEY, choseDetailed);
            detailed.set(choseDetailed);
        });
        return mode;
    }

    private ToggleButton modeButton(final String id, final MessageKey label) {
        final ToggleButton button =
                Tips.install(messages, new ToggleButton(messages.get(label)), MessageKey.LIVE_MODE_TIP);
        button.setId(id);
        return button;
    }

    private ToggleSwitch followSwitch() {
        final ToggleSwitch follow = Tips.install(
                messages, new ToggleSwitch(messages.get(MessageKey.LIVE_FOLLOW)), MessageKey.LIVE_FOLLOW_TIP);
        follow.setId("translating-live-follow");
        follow.selectedProperty().bindBidirectional(list.followSwitchProperty());
        follow.selectedProperty().addListener((observed, was, on) -> memory.remember(NO_FOLLOW_KEY, !on));
        return follow;
    }

    private Button followAgain() {
        final Button again = Tips.install(
                messages, new Button(messages.get(MessageKey.LIVE_FOLLOW_AGAIN)), MessageKey.LIVE_FOLLOW_AGAIN_TIP);
        again.setId("translating-live-follow-again");
        again.getStyleClass().add("btn-ghost");
        Visibility.bind(again, list.followingProperty().not());
        again.setOnAction(event -> {
            log.debug("live card: Follow again pressed");
            list.followAgain();
        });
        return again;
    }

    private Button allCallsButton(final Consumer<List<CallSnapshot>> allCalls) {
        final Button button = Tips.install(
                messages, new Button(messages.get(MessageKey.LIVE_ALL_CALLS)), MessageKey.LIVE_ALL_CALLS_TIP);
        button.setId("translating-live-all-calls");
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.getStyleClass().add("btn-ghost");
        button.setOnAction(event -> allCalls.accept(sources.calls().get().recent()));
        return button;
    }

    private HBox summaryLine() {
        final Label icon = new Label("≡");
        icon.getStyleClass().add("banner-icon");
        final Label title = new Label(messages.get(MessageKey.LIVE_SUMMARY_TITLE));
        title.getStyleClass().add("banner-title");
        final Label text = new Label();
        text.setId("translating-live-summary-text");
        text.setWrapText(true);
        text.getStyleClass().add("banner-text");
        text.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> {
                            final String summary = sources.summary().getValue();
                            return summary == null ? messages.get(MessageKey.LIVE_SUMMARY_NONE) : summary;
                        },
                        sources.summary()));
        final Label source = new Label(messages.get(MessageKey.LIVE_SUMMARY_SOURCE, RollingSummary.REFRESH_EVERY));
        source.setId("translating-live-summary-source");
        source.setWrapText(true);
        source.getStyleClass().add("muted");
        final VBox words = new VBox(2, title, text, source);
        HBox.setHgrow(words, Priority.ALWAYS);
        final HBox box = new HBox(SPACING, icon, words);
        box.setId("translating-live-summary");
        box.getStyleClass().addAll("banner", "banner-info");
        box.setMinHeight(Region.USE_PREF_SIZE);
        return box;
    }

    private void show() {
        if (showing) {
            return;
        }
        showing = true;
        try {
            showNow();
        } finally {
            showing = false;
        }
    }

    private void showNow() {
        final LiveCalls calls = sources.calls().get();
        final RunStateView view = sources.runs().get();
        final WaitingCall waiting = sources.waiting().get();
        list.show(calls.rows(), view.row());
        line.setText(CallLine.of(view, waiting, calls.current(), calls.asOf(), detailed.get(), messages));
        final String stuck = CallLine.stuckClock(view, waiting, messages);
        clock.setText(stuck);
        clock.setVisible(!stuck.isEmpty());
        clock.setManaged(!stuck.isEmpty());
        final LiveRow row = list.getSelectionModel().getSelectedItem();
        detail.show(row, row == null ? null : list.markerOf(row), calls, detailed.get());
    }
}
