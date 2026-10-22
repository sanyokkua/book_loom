package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookCard;
import ua.bookloom.ui.state.WordCount;

/**
 * The "About this book" fold under the verdict line: closed, so the page keeps its room for the book's text, with a
 * one-line summary of the book in its header and the details and facts inside. Its word count comes from
 * {@link WordCount}, the same owner as the verdict's, so the page never gives two figures for one book.
 */
@Slf4j
final class AboutBookFold extends TitledPane {

    AboutBookFold(final Messages messages, final BookCard card, final BookStats stats, final Node content) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(stats, "stats");
        setId("import-about");
        getStyleClass().add("import-fold");
        setText(messages.get(MessageKey.IMPORT_ABOUT_TITLE));
        final Label summary = new Label(summaryOf(messages, card, stats));
        summary.setId("import-about-summary");
        summary.getStyleClass().add("muted");
        setGraphic(summary);
        // The title first, then the one-line summary, as the mockup reads.
        setContentDisplay(ContentDisplay.RIGHT);
        setContent(Objects.requireNonNull(content, "content"));
        setExpanded(false);
        setAnimated(false);
        // Without this a page shorter than its parts would squash an open fold down to its title bar.
        setMinHeight(Region.USE_PREF_SIZE);
        Tips.install(messages, this, MessageKey.IMPORT_ABOUT_TIP);
        expandedProperty().addListener((observed, was, now) -> log.debug("the About this book fold is open: {}", now));
    }

    private static String summaryOf(final Messages messages, final BookCard card, final BookStats stats) {
        return messages.get(
                MessageKey.IMPORT_ABOUT_SUMMARY,
                messages.get(ImportViews.formatName(card.format())),
                card.chapters(),
                WordCount.text(messages, card.words()),
                stats.images(),
                messages.get(card.cover() == null ? MessageKey.IMPORT_ABOUT_NO_COVER : MessageKey.IMPORT_ABOUT_COVER));
    }
}
