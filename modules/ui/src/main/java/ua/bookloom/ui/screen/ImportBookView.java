package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.ui.control.LayoutClass;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.BookCard;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.StructureSegmentsViewModel;

/**
 * What the import page holds under its verdict line for an open book: the closed "About this book" fold, then the
 * book's text in one three-pane browser. The structure screen they came from is gone (15i.U3): the panes keep its ids,
 * so the tests and the conformance cases that named them still do.
 *
 * <p>Inside the fold the details sit beside the facts as a {@link TwoColumns}, which stacks them when the window is
 * narrow with no change to the order Tab walks. Showing a book starts its background checks, and Continue is never
 * held back by them.
 */
@Slf4j
final class ImportBookView {

    private static final double SECTION_SPACING = 10;

    private final Messages messages;
    private final LanguageNames names;
    private final StructureChecksViewModel checks;
    private final BookBriefViewModel brief;
    private final StructureBrowser browser;
    private final NumberFormat numbers;
    private final ReadOnlyBooleanProperty technical;

    ImportBookView(
            final Messages messages,
            final LanguageNames names,
            final StructureChecksViewModel checks,
            final StructureSegmentsViewModel segments,
            final BookBriefViewModel brief,
            final ReadOnlyBooleanProperty technical) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.names = Objects.requireNonNull(names, "names");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.technical = Objects.requireNonNull(technical, "technical");
        this.browser = new StructureBrowser(messages, segments, brief, technical);
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
    }

    /**
     * Builds the fold and the browser and starts the book's checks.
     *
     * @param card what the inspection found, shown in the fold
     * @param book the open book whose profile and stored project the browser shows, or {@code null} when none is open
     *     behind the card, which leaves only the fold
     * @return the fold, then the browser
     */
    List<Node> build(final BookCard card, final @Nullable OpenedBook book) {
        Objects.requireNonNull(card, "card");
        final Node details = ImportCardView.card(messages, names, card);
        if (book == null) {
            log.debug("no book is open behind the detected state; only its details are shown");
            return List.of(new AboutBookFold(messages, card, StructureBrowser.statsOf(null), details));
        }
        final BookProfile profile = book.profile();
        final StructureListing listing = StructureBrowser.listingOf(profile);
        log.debug(
                "showing the structure of project {}: {} top-level node(s), {} segments",
                book.projectId(),
                listing.roots().size(),
                listing.totalSegments());
        checks.ensure(book.projectId(), brief.brief().get());
        final BookStats stats = StructureBrowser.statsOf(profile);
        final TwoColumns columns = new TwoColumns(details, facts(stats, listing), LayoutClass.Width.REGULAR);
        columns.setId("import-columns");
        final StructureTextBrowser text = new StructureTextBrowser(
                browser.treeCard(listing, book.projectId()), browser.segmentsCard(), browser.textCard());
        VBox.setVgrow(text, Priority.ALWAYS);
        return List.of(new AboutBookFold(messages, card, stats, columns), text);
    }

    // The words are in the details with the chapters and the segment total is the tree's own count, so neither is
    // repeated; the run total stays here because it explains the segments fact.
    private Node facts(final BookStats stats, final StructureListing listing) {
        final Label title = new Label(messages.get(MessageKey.IMPORT_FACTS_TITLE));
        title.setId("import-facts-title");
        title.getStyleClass().add("card-title");
        final StructureChecksRows checkRows = new StructureChecksRows(checks, messages, numbers, technical);
        final VBox section = new VBox(
                SECTION_SPACING,
                title,
                new StructureStatsRows(messages, numbers).rows(stats),
                checkRows.runTotalLine(listing.totalSegments()),
                checkRows);
        section.setId("import-facts-card");
        return section;
    }
}
