package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.ui.control.TwoColumns;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.OpenedBook;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.StructureSegmentsViewModel;

/**
 * What the import screen holds under the drop zone for an open book: the details beside the facts, then the structure
 * beside its segments. The structure parts are the structure screen's own, built by the same classes, so that screen
 * can go (15i.U3) without this one changing.
 *
 * <p>Each pair is a {@link TwoColumns}, so a narrow window stacks the second card under the first with no change to
 * the order Tab walks. Showing a book starts its background checks, and Continue is never held back by them.
 */
@Slf4j
final class ImportBookView {

    // The content width, in px, below which a pair of cards would be squeezed until its rows are cut.
    private static final double STACK_BELOW = 760;
    private static final double CARD_SPACING = 10;

    private final Messages messages;
    private final StructureChecksViewModel checks;
    private final BookBriefViewModel brief;
    private final StructureBrowser browser;
    private final NumberFormat numbers;

    ImportBookView(
            final Messages messages,
            final StructureChecksViewModel checks,
            final StructureSegmentsViewModel segments,
            final BookBriefViewModel brief) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.browser = new StructureBrowser(messages, segments, brief);
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
    }

    /**
     * Builds the two rows of cards and starts the book's checks.
     *
     * @param details the book-details card
     * @param book the open book whose profile and stored project the structure parts show
     * @return the details-and-facts row, then the structure-and-segments row
     */
    List<Node> build(final Node details, final OpenedBook book) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(book, "book");
        final BookProfile profile = book.profile();
        final StructureListing listing = StructureBrowser.listingOf(profile);
        log.debug(
                "showing the structure of project {}: {} top-level node(s), {} segments",
                book.projectId(),
                listing.roots().size(),
                listing.totalSegments());
        checks.ensure(book.projectId(), brief.brief().get());
        final TwoColumns top = new TwoColumns(details, facts(StructureBrowser.statsOf(profile), listing), STACK_BELOW);
        top.setId("import-columns");
        final TwoColumns bottom =
                new TwoColumns(browser.treeCard(listing, book.projectId()), browser.segmentsCard(), STACK_BELOW);
        bottom.setId("import-structure-columns");
        return List.of(top, bottom);
    }

    // The words are on the details card with the chapters and the segment total is the tree's own count, so neither is
    // repeated; the run total stays here because it explains the segments fact.
    private Node facts(final BookStats stats, final StructureListing listing) {
        final Label title = new Label(messages.get(MessageKey.IMPORT_FACTS_TITLE));
        title.setId("import-facts-title");
        title.getStyleClass().add("card-title");
        final StructureChecksRows checkRows = new StructureChecksRows(checks, messages, numbers);
        final VBox card = new VBox(
                CARD_SPACING,
                title,
                new StructureStatsCard(messages, numbers).rows(stats, false),
                checkRows.runTotalLine(listing.totalSegments()),
                checkRows);
        card.setId("import-facts-card");
        card.getStyleClass().add("card");
        return card;
    }
}
