package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** The top of a {@link TermTableCard}: the title with its row count, the note under it and the "How it works" link. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CardHeading {

    private static final double COUNT_GAP = 8;
    private static final double HELP_GAP = 2;

    static List<Node> of(
            final Messages messages, final String id, final TermTableCard.Texts texts, final ObservableList<?> items) {
        final Node heading = heading(messages, texts, items);
        return texts.note() == null ? List.of(heading) : List.of(heading, noteBlock(messages, id, texts));
    }

    // The title with how many rows the table holds, so an empty card reads "0" before its empty state does.
    private static HBox heading(
            final Messages messages, final TermTableCard.Texts texts, final ObservableList<?> items) {
        final Label count = new Label();
        count.setId(texts.titleId() + "-count");
        count.getStyleClass().addAll("chip", "chip-neutral");
        count.textProperty().bind(Bindings.size(items).asString());
        final HBox heading = new HBox(COUNT_GAP, title(messages, texts), count);
        heading.setAlignment(Pos.CENTER_LEFT);
        return heading;
    }

    private static Label title(final Messages messages, final TermTableCard.Texts texts) {
        final Label title = new Label(messages.get(texts.title()));
        title.setId(texts.titleId());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);
        title.setMinHeight(Region.USE_PREF_SIZE);
        return title;
    }

    private static VBox noteBlock(final Messages messages, final String id, final TermTableCard.Texts texts) {
        final VBox block = new VBox(HELP_GAP, note(messages, id, Objects.requireNonNull(texts.note())));
        if (texts.help() != null) {
            final Hyperlink link = new Hyperlink(messages.get(texts.help().link()));
            link.setId(id + "-help-link");
            Tips.install(messages, link, MessageKey.RECURRING_HELP_LINK_TIP);
            final Label help = new Label(messages.get(texts.help().text()));
            help.setId(id + "-help");
            help.getStyleClass().add("muted");
            help.setWrapText(true);
            help.setMinHeight(Region.USE_PREF_SIZE);
            help.setVisible(false);
            help.setManaged(false);
            link.setOnAction(event -> {
                help.setVisible(!help.isVisible());
                help.setManaged(help.isVisible());
            });
            block.getChildren().addAll(link, help);
        }
        return block;
    }

    private static Label note(final Messages messages, final String id, final MessageKey key) {
        final Label note = new Label(messages.get(key));
        note.setId(id + "-note");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        note.setMinHeight(Region.USE_PREF_SIZE);
        return note;
    }
}
