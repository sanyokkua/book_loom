package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.Objects;
import java.util.Optional;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.StructureSegmentsViewModel;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The structure screen's content for an open book: the tree of the book's own structure with the segment total beneath
 * it, the statistics and the background checks beside it, the segment browser of the part picked in the tree under
 * them, and the Back and Continue actions. The tree and the browser are {@link StructureBrowser}'s, which the import
 * screen shows too until this screen goes (15i.U3).
 *
 * <p>The screen is read-only, and Continue is never held back by a check.
 */
@Slf4j
final class StructureView {

    private static final double CARD_SPACING = 10;
    private static final double SCREEN_SPACING = 14;
    private static final double COLUMN_SPACING = 14;
    private static final double SIDE_WIDTH = 420;

    private final Messages messages;
    private final Navigator navigator;
    private final WorkflowProgress progress;
    private final StructureChecksViewModel checks;
    private final BookBriefViewModel brief;
    private final StructureBrowser browser;
    private final NumberFormat numbers;

    StructureView(
            final Messages messages,
            final Navigator navigator,
            final WorkflowProgress progress,
            final StructureChecksViewModel checks,
            final StructureSegmentsViewModel segments,
            final BookBriefViewModel brief) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.browser = new StructureBrowser(messages, segments, brief);
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
    }

    Node build(final StructureListing listing, final BookStats stats, final String projectId) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(stats, "stats");
        log.debug(
                "showing {} top-level node(s), {} segments in total",
                listing.roots().size(),
                listing.totalSegments());
        checks.ensure(projectId, brief.brief().get());
        final StructureChecksRows checkRows = new StructureChecksRows(checks, messages, numbers);
        final VBox checksCard = new VBox(checkRows);
        checksCard.setId("structure-checks-card");
        checksCard.getStyleClass().add("card");
        final VBox side = new VBox(CARD_SPACING, new StructureStatsCard(messages, numbers).build(stats), checksCard);
        side.setPrefWidth(SIDE_WIDTH);
        side.setMinWidth(SIDE_WIDTH);
        final Node treeCard =
                browser.treeCard(listing, projectId, total(listing), checkRows.runTotalLine(listing.totalSegments()));
        HBox.setHgrow(treeCard, Priority.ALWAYS);
        final HBox columns = new HBox(COLUMN_SPACING, treeCard, side);
        return new VBox(SCREEN_SPACING, columns, browser.segmentsCard(), actions());
    }

    private Label total(final StructureListing listing) {
        final Label total = new Label(messages.get(MessageKey.STRUCTURE_TOTAL, listing.totalSegments()));
        total.setId("structure-total");
        total.getStyleClass().add("muted");
        return total;
    }

    private Node actions() {
        return StepFooter.of(
                new StepFooter.Action(
                        "structure-back",
                        messages.get(MessageKey.BRIEF_BACK),
                        messages.get(MessageKey.BRIEF_BACK_TIP),
                        "btn-ghost",
                        () -> navigator.navigate(ViewNames.BOOK_BRIEF)),
                new StepFooter.Action(
                        "structure-continue",
                        messages.get(MessageKey.STRUCTURE_CONTINUE),
                        messages.get(MessageKey.STRUCTURE_CONTINUE_TIP),
                        "btn-primary",
                        this::onContinue));
    }

    private void onContinue() {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.STRUCTURE);
        log.debug("continue pressed, the next step is {}", next);
        progress.markDone(ViewNames.STRUCTURE);
        next.ifPresent(navigator::navigate);
    }
}
