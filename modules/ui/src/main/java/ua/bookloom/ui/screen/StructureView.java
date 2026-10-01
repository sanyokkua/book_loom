package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.StepFooter;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureChecks;
import ua.bookloom.ui.state.StructureChecksViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.WorkflowProgress;

/**
 * The structure screen's content for an open book: the tree of the book's own structure with the segment total beneath
 * it, the statistics and the background checks beside it, and the Back and Continue actions under them.
 *
 * <p>The tree is a {@link TreeView} because it virtualizes its rows: a book with thousands of units materialises only
 * the cells the viewport shows. The screen is read-only, and Continue is never held back by a check.
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
    private final NumberFormat numbers;

    StructureView(
            final Messages messages,
            final Navigator navigator,
            final WorkflowProgress progress,
            final StructureChecksViewModel checks) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.checks = Objects.requireNonNull(checks, "checks");
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
    }

    Node build(final StructureListing listing, final BookStats stats, final String projectId) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(stats, "stats");
        log.debug(
                "showing {} top-level node(s), {} segments in total",
                listing.roots().size(),
                listing.totalSegments());
        checks.run(projectId);
        final VBox side = new VBox(
                CARD_SPACING,
                new StructureStatsCard(messages, numbers).build(stats),
                new StructureChecksCard(checks, messages, numbers));
        side.setPrefWidth(SIDE_WIDTH);
        side.setMinWidth(SIDE_WIDTH);
        final Node treeCard = card(listing);
        HBox.setHgrow(treeCard, Priority.ALWAYS);
        final HBox columns = new HBox(COLUMN_SPACING, treeCard, side);
        VBox.setVgrow(columns, Priority.ALWAYS);
        return new VBox(SCREEN_SPACING, columns, actions());
    }

    private Node card(final StructureListing listing) {
        final Label heading = new Label(messages.get(MessageKey.STRUCTURE_READING_ORDER));
        heading.getStyleClass().add("card-title");
        Tips.install(messages, heading, MessageKey.STRUCTURE_READING_ORDER_TIP);
        final Label total = new Label(messages.get(MessageKey.STRUCTURE_TOTAL, listing.totalSegments()));
        total.setId("structure-total");
        total.getStyleClass().add("muted");
        final VBox card = new VBox(CARD_SPACING, heading, tree(listing.roots()), total, runTotal(listing));
        card.setId("structure-card");
        card.getStyleClass().add("card");
        return card;
    }

    // Translating starts its remaining count from every segment a run translates, which is more than the tree's book
    // text whenever the brief also translates titles, image descriptions, contents or book details; this line says so.
    private Label runTotal(final StructureListing listing) {
        final int bookText = listing.totalSegments();
        final Label line = new Label();
        line.setId("structure-run-total");
        line.getStyleClass().add("muted");
        line.setWrapText(true);
        line.setMinHeight(Region.USE_PREF_SIZE);
        line.textProperty()
                .bind(Bindings.createStringBinding(
                        () -> checks.state().get() instanceof StructureChecks.Finished finished
                                        && finished.runSegments() > bookText
                                ? messages.get(
                                        MessageKey.STRUCTURE_RUN_TOTAL,
                                        bookText,
                                        finished.runSegments() - bookText,
                                        finished.runSegments())
                                : "",
                        checks.state()));
        line.visibleProperty().bind(line.textProperty().isNotEmpty());
        line.managedProperty().bind(line.visibleProperty());
        return line;
    }

    private TreeView<StructureNode> tree(final List<StructureNode> roots) {
        final TreeItem<StructureNode> root = new TreeItem<>();
        roots.forEach(node -> root.getChildren().add(itemOf(node)));
        final TreeView<StructureNode> tree = new TreeView<>(root);
        tree.setId("structure-tree");
        tree.getStyleClass().add("structure-tree");
        tree.setShowRoot(false);
        tree.setEditable(false);
        tree.setCellFactory(view -> new StructureNodeCell(messages, numbers));
        VBox.setVgrow(tree, Priority.ALWAYS);
        return tree;
    }

    // Expanded so that the chapters a person recognises are visible without opening each part.
    private static TreeItem<StructureNode> itemOf(final StructureNode node) {
        final TreeItem<StructureNode> item = new TreeItem<>(node);
        node.children().forEach(child -> item.getChildren().add(itemOf(child)));
        item.setExpanded(true);
        return item;
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
