package ua.bookloom.ui;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.SetChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.control.Motion;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.screen.ProviderNames;
import ua.bookloom.ui.state.SettingsViewModel;
import ua.bookloom.ui.state.StepLocks;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The navigation column, generated from {@link ViewNames} and {@link NavGroup} so that a screen added to the enum
 * appears here without anyone editing a second list.
 *
 * <p>An entry without a screen, or one whose step is locked, is greyed but deliberately not disabled: a disabled
 * button would swallow the click, and the refusal — with its reason — is the {@link Navigator}'s to log and to tell
 * the person.
 */
@Slf4j
final class NavColumn {

    static final String COLUMN_ID = "shell-nav";
    static final String CURRENT_STYLE_CLASS = "nav-item-current";
    /** The product's initials, drawn as a logo glyph rather than a word, so they are not a catalogue entry. */
    private static final String LOGO_TEXT = "BL";

    private static final String UNAVAILABLE_STYLE_CLASS = "nav-item-unavailable";
    private static final String LOCKED_STYLE_CLASS = "nav-item-locked";
    private static final String DONE_STYLE_CLASS = "nav-step-done";
    private static final String NO_TEXT = "";

    private final Messages messages;
    private final StepLocks locks;
    private final Consumer<ViewNames> activation;
    private final Map<ViewNames, Button> entries = new EnumMap<>(ViewNames.class);
    private final Map<ViewNames, Label> badges = new EnumMap<>(ViewNames.class);
    private final Label footer = new Label();
    private final VBox column = new VBox();
    private final ScrollPane scroll = new ScrollPane(column);

    /**
     * Builds every group and entry.
     *
     * @param messages the catalogue the headings and labels come from
     * @param activation told which entry was activated, available or not
     * @param progress the steps to mark as done
     * @param settings the chosen provider and model the footer names
     * @param locks which steps are closed, and why, so a locked entry is drawn so and explains itself
     */
    NavColumn(
            final Messages messages,
            final Consumer<ViewNames> activation,
            final WorkflowProgress progress,
            final SettingsViewModel settings,
            final StepLocks locks) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activation = Objects.requireNonNull(activation, "activation");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(settings, "settings");
        this.locks = Objects.requireNonNull(locks, "locks");
        column.getChildren().add(brand());
        for (final NavGroup group : NavGroup.values()) {
            column.getChildren().add(group(group));
        }
        column.getChildren().addAll(spacer(), footer(settings));
        progress.done().addListener((SetChangeListener<ViewNames>) change -> {
            if (change.wasAdded()) {
                markDone(change.getElementAdded(), true);
            } else {
                markDone(change.getElementRemoved(), false);
            }
        });
        progress.done().forEach(step -> markDone(step, true));
        // The column is a scroll pane so a short window can still reach every entry; it keeps the column's id and
        // width and carries its background, and the entries sit in a transparent box inside it.
        scroll.setId(COLUMN_ID);
        scroll.getStyleClass().addAll("shell-nav", "nav-scroll");
        scroll.setFitToWidth(true);
        // Fitting the height lets the spacer push the footer to the bottom of a tall window.
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        log.debug("built navigation column with {} entries", entries.size());
    }

    /**
     * The id of the entry for a view, which the shell publishes so tests and accessibility tooling can find it.
     *
     * @param view the entry's screen
     * @return the id, such as {@code nav-book-brief} for {@link ViewNames#BOOK_BRIEF}
     */
    static String idOf(final ViewNames view) {
        return "nav-" + view.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * The node to place in the shell.
     *
     * @return the scrollable column
     */
    Node view() {
        return scroll;
    }

    /**
     * Moves the current mark, so at most one entry ever carries it.
     *
     * @param current the entry to mark, or {@code null} to clear the mark
     */
    void markCurrent(final @Nullable ViewNames current) {
        entries.forEach((view, button) -> {
            final boolean wasCurrent = button.getStyleClass().remove(CURRENT_STYLE_CLASS);
            if (view == current) {
                button.getStyleClass().add(CURRENT_STYLE_CLASS);
                if (!wasCurrent) {
                    Motion.settle(button);
                }
            }
        });
    }

    private Node brand() {
        final Label logo = new Label(LOGO_TEXT);
        logo.getStyleClass().add("nav-brand-logo");
        final Label name = new Label(messages.get(MessageKey.SHELL_TITLE));
        name.getStyleClass().add("nav-brand-title");
        final Label tagline = new Label(messages.get(MessageKey.SHELL_TAGLINE));
        tagline.getStyleClass().add("nav-brand-sub");
        final HBox brand = new HBox(logo, new VBox(name, tagline));
        brand.getStyleClass().add("nav-brand");
        brand.setAlignment(Pos.CENTER_LEFT);
        return brand;
    }

    private Node group(final NavGroup group) {
        final Label heading = new Label(messages.get(group.heading()).toUpperCase(messages.locale()));
        heading.getStyleClass().add("nav-label");
        final VBox box = new VBox(heading);
        box.getStyleClass().add("nav-group");
        for (final ViewNames view : ViewNames.values()) {
            if (view.group() == group) {
                box.getChildren().add(entry(view));
            }
        }
        return box;
    }

    private Button entry(final ViewNames view) {
        final Button button = new Button(messages.get(view.messageKey()));
        button.setId(idOf(view));
        Tips.install(messages, button, view.tipKey());
        button.getStyleClass().add("nav-item");
        if (!view.isAvailable()) {
            button.getStyleClass().add(UNAVAILABLE_STYLE_CLASS);
        }
        button.setGraphic(badge(view));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> activation.accept(view));
        entries.put(view, button);
        final ReadOnlyObjectProperty<@Nullable MessageKey> lock = locks.lock(view);
        lock.addListener((observed, old, reason) -> showLock(view, reason));
        showLock(view, lock.get());
        return button;
    }

    // The entry stays enabled so a click still reaches the navigator, which answers with the reason; a disabled
    // button would swallow it and, with it, its tooltip.
    private void showLock(final ViewNames view, final @Nullable MessageKey reason) {
        final Button button = entries.get(view);
        if (button == null) {
            return;
        }
        log.debug("navigation entry {} lock {}", view, reason);
        button.getStyleClass().remove(LOCKED_STYLE_CLASS);
        if (reason == null) {
            Tips.install(messages, button, view.tipKey());
        } else {
            button.getStyleClass().add(LOCKED_STYLE_CLASS);
            Tips.install(messages, button, reason);
        }
    }

    private @Nullable Node badge(final ViewNames view) {
        final OptionalInt step = view.step();
        if (step.isPresent()) {
            final Label number = new Label(Integer.toString(step.getAsInt()));
            number.getStyleClass().add("nav-step");
            badges.put(view, number);
            return number;
        }
        return view.icon().<Node>map(FontIcon::new).orElse(null);
    }

    private void markDone(final ViewNames step, final boolean done) {
        final Button button = entries.get(step);
        final Label badge = badges.get(step);
        if (button == null || badge == null) {
            return;
        }
        log.debug("navigation entry {} done mark {}", step, done);
        button.getStyleClass().remove(DONE_STYLE_CLASS);
        if (done) {
            button.getStyleClass().add(DONE_STYLE_CLASS);
            badge.setText(NO_TEXT);
            badge.setGraphic(new FontIcon(Feather.CHECK));
        } else {
            badge.setGraphic(null);
            badge.setText(Integer.toString(step.step().orElseThrow()));
        }
    }

    private static Node spacer() {
        final Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private Node footer(final SettingsViewModel settings) {
        footer.setId("nav-footer");
        footer.getStyleClass().add("nav-footer");
        footer.setWrapText(true);
        footer.setMaxWidth(Double.MAX_VALUE);
        final Runnable refresh = () -> showSelection(settings);
        settings.selectedProviderId().addListener((observed, old, current) -> refresh.run());
        settings.model().addListener((observed, old, current) -> refresh.run());
        refresh.run();
        return footer;
    }

    private void showSelection(final SettingsViewModel settings) {
        final String provider = settings.selectedProviderId().get();
        final String model = settings.model().get().strip();
        log.debug("navigation footer follows provider '{}' and model '{}'", provider, model);
        footer.setText(
                provider.isEmpty() || model.isEmpty()
                        ? messages.get(MessageKey.NAV_FOOTER_NO_MODEL)
                        : messages.get(
                                MessageKey.NAV_FOOTER_SELECTION, ProviderNames.displayName(messages, provider), model));
    }
}
