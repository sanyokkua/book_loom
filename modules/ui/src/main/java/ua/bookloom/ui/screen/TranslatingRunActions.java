package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Button;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.RunStateView.RunAction;

/**
 * Translating's run actions in the toolbar (README L2): one button per action id, shown, worded and enabled as the
 * run-state row says, each pressing its own command. The buttons are created once, in the order every row lists its
 * actions in, so a change of state moves nothing; the step footer carries them into the toolbar.
 *
 * <p>The row source outlives the screen, so it is followed through a weak listener whose strong reference the first
 * button holds. A tooltip is installed only when its words change, because the row is worked out again on every tick.
 */
@Slf4j
final class TranslatingRunActions {

    private static final List<String> ORDER = List.of(
            RunStateView.START, RunStateView.RESUME, RunStateView.PAUSE, RunStateView.STOP, RunStateView.REVIEW);
    private static final String PRIMARY = "btn-primary";
    private static final String SECONDARY = "btn-secondary";
    private static final String LISTENER_KEY = "translating-run-actions";
    // What a button explains before its row first words it, so a hidden one is never without a hover explanation.
    private static final Map<String, MessageKey> FIRST_TIPS = Map.of(
            RunStateView.START, MessageKey.TRANSLATING_START_TIP,
            RunStateView.RESUME, MessageKey.TRANSLATING_RESUME_TIP,
            RunStateView.PAUSE, MessageKey.TRANSLATING_PAUSE_TIP,
            RunStateView.STOP, MessageKey.TRANSLATING_STOP_TIP,
            RunStateView.REVIEW, MessageKey.RUN_ACTION_REVIEW_TIP);

    private final Messages messages;
    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private final Map<Button, String> shownTips = new LinkedHashMap<>();
    private final ChangeListener<RunStateView> onView = (observed, was, now) -> show(now);
    private List<String> shownIds = List.of();

    /**
     * Builds the buttons and starts following the row.
     *
     * @param runs the row source
     * @param messages the catalogue the actions are worded from
     * @param commands what each action id does when pressed; every id of the order above
     * @param forward the footer's forward button, which shows Continue to Export where the row offers it
     */
    TranslatingRunActions(
            final RunStateSource runs,
            final Messages messages,
            final Map<String, Runnable> commands,
            final Button forward) {
        Objects.requireNonNull(runs, "runs");
        this.messages = Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(commands, "commands");
        for (final String id : ORDER) {
            final Button button = button(id, Objects.requireNonNull(commands.get(id), id));
            Tips.install(messages, button, Objects.requireNonNull(FIRST_TIPS.get(id), id));
            buttons.put(id, button);
        }
        buttons.put(RunStateView.TO_EXPORT, Objects.requireNonNull(forward, "forward"));
        Objects.requireNonNull(buttons.get(RunStateView.START), "start")
                .getProperties()
                .put(LISTENER_KEY, onView);
        runs.view().addListener(new WeakChangeListener<>(onView));
        show(runs.view().get());
    }

    /**
     * The run buttons, without the forward one, in the order they sit in the toolbar.
     *
     * @return never empty
     */
    List<Button> buttons() {
        return ORDER.stream().map(buttons::get).toList();
    }

    private static Button button(final String id, final Runnable command) {
        final Button button = new Button();
        button.setId(id);
        button.getStyleClass().add(SECONDARY);
        button.setOnAction(event -> {
            log.debug("run action {} pressed", id);
            command.run();
        });
        return button;
    }

    private void show(final RunStateView view) {
        final List<RunAction> actions = view.toolbar();
        final List<String> ids = new ArrayList<>();
        buttons.forEach((id, button) -> {
            final @Nullable RunAction action = actions.stream()
                    .filter(offered -> offered.id().equals(id))
                    .findFirst()
                    .orElse(null);
            showOne(button, action);
            if (action != null) {
                ids.add(id);
            }
        });
        if (!ids.equals(shownIds)) {
            log.debug("toolbar run actions now {}", ids);
            shownIds = List.copyOf(ids);
        }
    }

    private void showOne(final Button button, final @Nullable RunAction action) {
        button.setVisible(action != null);
        button.setManaged(action != null);
        if (action == null) {
            return;
        }
        button.setText(action.label().in(messages));
        button.setDisable(!action.enabled());
        final String look = action.primary() ? PRIMARY : SECONDARY;
        if (!button.getStyleClass().contains(look)) {
            button.getStyleClass().removeAll(PRIMARY, SECONDARY);
            button.getStyleClass().add(look);
        }
        final String tip = action.tip().in(messages);
        if (!tip.equals(shownTips.get(button))) {
            shownTips.put(button, tip);
            Tips.install(button, tip);
        }
    }
}
