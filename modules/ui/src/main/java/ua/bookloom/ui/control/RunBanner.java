package ua.bookloom.ui.control;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.RunInterventions;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.RunStateView;
import ua.bookloom.ui.state.RunStateView.BannerView;
import ua.bookloom.ui.state.RunStateView.RunAction;
import ua.bookloom.ui.state.ShellBanners;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.StepLocks;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The run's banner, pinned above the content on every screen while its run-state row has one (README L3): one banner
 * node whose words, role and actions are replaced in place, so a change of state moves nothing on screen.
 *
 * <p>Off Translating it also offers Go to Translating while that step is open and the banner leads nowhere else; a
 * finished run's banner shows on Translating only, an action that opens the screen on show is left out, and a banner
 * whose every action does so is not shown there. Each action
 * runs its own command: Try again now resumes, Skip flags the segments in flight and goes on, Open settings and Choose
 * another model open the settings, Show details opens the failure, which never opens by itself. FX thread only; the
 * row is worked out again on every tick of the run's clocks, so only a change of banner is logged.
 */
@Slf4j
@Singleton
public final class RunBanner {

    /** The key the banner is pinned under in {@link ShellBanners}. */
    public static final String KEY = "run";

    private static final double ACTION_SPACING = 8;

    // The actions that only open a screen, with the screen each opens.
    private static final Map<String, ViewNames> DESTINATIONS = Map.of(
            RunStateView.OPEN_SETTINGS, ViewNames.SETTINGS,
            RunStateView.CHOOSE_MODEL, ViewNames.SETTINGS,
            RunStateView.GO_TRANSLATING, ViewNames.TRANSLATING,
            RunStateView.GO_BRIEF, ViewNames.BOOK_BRIEF);

    private final RunStateSource runs;
    private final Navigator navigator;
    private final ShellBanners banners;
    private final Messages messages;
    private final StepLocks locks;
    private final Map<String, Runnable> commands;
    private final Banner banner = new Banner("translating-banner", Banner.Role.INFO, "", "", "");
    private final HBox actionRow = new HBox(ACTION_SPACING);
    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<Button, String> shownTips = new HashMap<>();
    private List<String> shownIds = List.of();
    private @Nullable String shownKey;

    /**
     * Builds the banner and starts following the run-state row and the screen shown.
     *
     * @param runs the row
     * @param navigator the screen shown, and where Go to Translating and the settings actions lead
     * @param banners the shell's slot the banner is pinned in
     * @param messages the catalogue the banner is worded from
     * @param viewModel what Try again now resumes
     * @param interventions what Skip asks to flag the segments in flight
     * @param mirror the failure Show details opens
     * @param errors where Show details opens it
     * @param locks whether Translating is open, so Go to Translating is never offered for a closed step
     */
    @Inject
    public RunBanner(
            final RunStateSource runs,
            final Navigator navigator,
            final ShellBanners banners,
            final Messages messages,
            final TranslatingViewModel viewModel,
            final RunInterventions interventions,
            final StateMirror mirror,
            final ErrorPresenter errors,
            final StepLocks locks) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.banners = Objects.requireNonNull(banners, "banners");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.locks = Objects.requireNonNull(locks, "locks");
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(interventions, "interventions");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(errors, "errors");
        this.commands = Map.of(
                RunStateView.RETRY, viewModel::resume,
                RunStateView.SKIP, interventions::skipSegment,
                RunStateView.OPEN_SETTINGS, () -> navigator.navigate(ViewNames.SETTINGS),
                RunStateView.CHOOSE_MODEL, () -> navigator.navigate(ViewNames.SETTINGS),
                RunStateView.SHOW_DETAILS, () -> showDetails(mirror.failure().get(), errors),
                RunStateView.REVIEW_SEGMENTS, this::openReview,
                RunStateView.GO_TRANSLATING, () -> navigator.navigate(ViewNames.TRANSLATING),
                RunStateView.GO_BRIEF, () -> navigator.navigate(ViewNames.BOOK_BRIEF));
        actionRow.setId("translating-banner-actions");
        actionRow.setAlignment(Pos.CENTER_LEFT);
        banner.addDetail(actionRow);
        final InvalidationListener refresh = observed -> refresh();
        runs.view().addListener(refresh);
        runs.projectOpen().addListener(refresh);
        navigator.currentView().addListener(refresh);
        locks.lock(ViewNames.TRANSLATING).addListener(refresh);
        refresh();
    }

    private void refresh() {
        final boolean onTranslating = navigator.currentView().get() == ViewNames.TRANSLATING;
        final @Nullable BannerView shown =
                runs.projectOpen().get() ? runs.view().get().banner() : null;
        if (shown == null || (shown.translatingOnly() && !onTranslating) || onlyLeadsHere(shown)) {
            withdraw();
            return;
        }
        if (!shown.key().equals(shownKey)) {
            log.debug("run banner {} pinned", shown.key());
            shownKey = shown.key();
        }
        banner.setRole(shown.role());
        banner.setGlyph(shown.glyph());
        banner.setTitle(shown.title().in(messages));
        final RunStateView.Text text = shown.text();
        banner.setText(text == null ? "" : text.in(messages));
        showActions(actionsOf(shown, onTranslating));
        banners.publish(KEY, banner);
    }

    private void withdraw() {
        if (shownKey != null) {
            log.debug("run banner {} withdrawn", shownKey);
            shownKey = null;
        }
        banners.withdraw(KEY);
    }

    // A banner whose every action leads to the screen on show (Go to Book Brief on the brief, Open settings on
    // Settings)
    // tells what to do there; on that screen it is only noise.
    private boolean onlyLeadsHere(final BannerView shown) {
        final boolean here =
                !shown.actions().isEmpty() && shown.actions().stream().allMatch(this::leadsHere);
        if (here && shownKey != null) {
            log.debug("run banner {} not shown on the screen it leads to", shownKey);
        }
        return here;
    }

    private boolean leadsHere(final RunAction action) {
        return navigator.currentView().get() == DESTINATIONS.get(action.id());
    }

    // A banner off Translating leads there, unless it leads to the brief instead or Translating is still closed.
    private List<RunAction> actionsOf(final BannerView shown, final boolean onTranslating) {
        final List<RunAction> actions = new ArrayList<>(shown.actions());
        actions.removeIf(this::leadsHere);
        final boolean leadsElsewhere =
                shown.actions().stream().anyMatch(action -> RunStateView.GO_BRIEF.equals(action.id()));
        if (!onTranslating
                && !leadsElsewhere
                && locks.lockReason(ViewNames.TRANSLATING).isEmpty()) {
            actions.add(new RunAction(
                    RunStateView.GO_TRANSLATING,
                    RunStateView.Text.of(MessageKey.RUN_ACTION_GO_TRANSLATING),
                    false,
                    true,
                    RunStateView.Text.of(MessageKey.RUN_ACTION_GO_TRANSLATING_TIP)));
        }
        return actions;
    }

    private void showActions(final List<RunAction> actions) {
        final List<String> ids = actions.stream().map(RunAction::id).toList();
        if (!ids.equals(shownIds)) {
            shownIds = ids;
            final List<Node> row = new ArrayList<>();
            for (int at = 0; at < actions.size(); at++) {
                row.add(buttonFor(actions.get(at).id(), at == 0));
            }
            actionRow.getChildren().setAll(row);
        }
        actionRow.setVisible(!actions.isEmpty());
        actionRow.setManaged(!actions.isEmpty());
        actions.forEach(this::word);
    }

    // The first action is the secondary button, the rest ghosts, as the README draws a banner's actions.
    private Button buttonFor(final String id, final boolean first) {
        final Button button = buttons.computeIfAbsent(id, this::newButton);
        button.getStyleClass().removeAll("btn-secondary", "btn-ghost");
        button.getStyleClass().add(first ? "btn-secondary" : "btn-ghost");
        return button;
    }

    private Button newButton(final String id) {
        final Runnable command = Objects.requireNonNull(commands.get(id), id);
        final Button button = new Button();
        button.setId(id);
        button.setOnAction(event -> {
            log.debug("run banner action {} pressed", id);
            command.run();
        });
        return button;
    }

    private void word(final RunAction action) {
        final Button button = Objects.requireNonNull(buttons.get(action.id()), action.id());
        button.setText(action.label().in(messages));
        button.setDisable(!action.enabled());
        final String tip = action.tip().in(messages);
        if (!tip.equals(shownTips.get(button))) {
            shownTips.put(button, tip);
            Tips.install(button, tip);
        }
    }

    // A finished run's banner shows on Translating only, where the toolbar's Review is; it does what that does.
    private void openReview() {
        final Scene scene = banner.getScene();
        final @Nullable Node review = scene == null ? null : scene.getRoot().lookup("#" + RunStateView.REVIEW);
        if (review instanceof Button button) {
            button.fire();
        } else {
            log.debug("review segments pressed with no review action in the window");
        }
    }

    private static void showDetails(final @Nullable AppError failure, final ErrorPresenter errors) {
        if (failure == null) {
            log.warn("show details pressed but no failure was published");
            return;
        }
        log.debug("show details pressed: opening the failure {}", failure.code());
        errors.presentRunFailure(failure);
    }
}
