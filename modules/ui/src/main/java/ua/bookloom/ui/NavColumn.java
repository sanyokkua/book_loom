package ua.bookloom.ui;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.kordamp.ikonli.javafx.FontIcon;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * The navigation column, generated from {@link ViewNames} and {@link NavGroup} so that a screen added to the enum
 * appears here without anyone editing a second list.
 *
 * <p>An entry without a screen is greyed but deliberately not disabled: a disabled button would swallow the click, and
 * the refusal — with its reason — is the {@link Navigator}'s to log.
 */
@Slf4j
final class NavColumn {

    static final String COLUMN_ID = "shell-nav";
    static final String CURRENT_STYLE_CLASS = "nav-item-current";
    /** The product's initials, drawn as a logo glyph rather than a word, so they are not a catalogue entry. */
    private static final String LOGO_TEXT = "BL";

    private static final String UNAVAILABLE_STYLE_CLASS = "nav-item-unavailable";

    private final Messages messages;
    private final Consumer<ViewNames> activation;
    private final Map<ViewNames, Button> entries = new EnumMap<>(ViewNames.class);
    private final VBox column = new VBox();
    private final ScrollPane scroll = new ScrollPane(column);

    /**
     * Builds every group and entry.
     *
     * @param messages the catalogue the headings and labels come from
     * @param activation told which entry was activated, available or not
     */
    NavColumn(final Messages messages, final Consumer<ViewNames> activation) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activation = Objects.requireNonNull(activation, "activation");
        column.getChildren().add(brand());
        for (final NavGroup group : NavGroup.values()) {
            column.getChildren().add(group(group));
        }
        // The column is a scroll pane so a short window can still reach every entry; it keeps the column's id and
        // width and carries its background, and the entries sit in a transparent box inside it.
        scroll.setId(COLUMN_ID);
        scroll.getStyleClass().addAll("shell-nav", "nav-scroll");
        scroll.setFitToWidth(true);
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
            button.getStyleClass().remove(CURRENT_STYLE_CLASS);
            if (view == current) {
                button.getStyleClass().add(CURRENT_STYLE_CLASS);
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
        final Label heading = new Label(messages.get(group.heading()));
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
        button.getStyleClass().add("nav-item");
        if (!view.isAvailable()) {
            button.getStyleClass().add(UNAVAILABLE_STYLE_CLASS);
        }
        button.setGraphic(badge(view));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> activation.accept(view));
        entries.put(view, button);
        return button;
    }

    private static @Nullable Node badge(final ViewNames view) {
        final OptionalInt step = view.step();
        if (step.isPresent()) {
            final Label number = new Label(Integer.toString(step.getAsInt()));
            number.getStyleClass().add("nav-step");
            return number;
        }
        return view.icon().<Node>map(FontIcon::new).orElse(null);
    }
}
