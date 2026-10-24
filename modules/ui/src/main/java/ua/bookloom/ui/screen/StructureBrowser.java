package ua.bookloom.ui.screen;

import java.text.NumberFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookProfile;
import ua.bookloom.api.document.BookStats;
import ua.bookloom.api.document.StructureNode;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.StructureSegmentsViewModel;
import ua.bookloom.ui.state.UnitPart;

/**
 * The reading-order tree and the segment browser of the picked part, as two cards that every screen showing the book's
 * structure builds the same way: picking a part in the tree lists its segments, and a finished brief save lists them
 * again because the chunk plan depends on the brief.
 *
 * <p>One instance serves one screen; the weak listener it registers on the long-lived brief is kept alive by the field
 * below, so it ends with the screen.
 */
@Slf4j
final class StructureBrowser {

    private static final double CARD_SPACING = 10;
    private static final double TREE_MIN_HEIGHT = 204;
    private static final double TREE_MIN_WIDTH = 160;
    private static final double SEGMENTS_MIN_WIDTH = 240;
    private static final double TEXT_MIN_WIDTH = 220;
    private static final BookStats EMPTY_STATS = new BookStats(0, 0, 0, 0, 0, 0, 0, 0, Set.of());

    private final Messages messages;
    private final StructureSegmentsViewModel segments;
    private final BookBriefViewModel brief;
    private final NumberFormat numbers;
    private final StructureTextPane reading;
    private final ChangeListener<Boolean> onBriefSaved;
    private final ReadOnlyBooleanProperty technical;

    StructureBrowser(
            final Messages messages,
            final StructureSegmentsViewModel segments,
            final BookBriefViewModel brief,
            final ReadOnlyBooleanProperty technical) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.segments = Objects.requireNonNull(segments, "segments");
        this.brief = Objects.requireNonNull(brief, "brief");
        this.numbers = NumberFormat.getIntegerInstance(messages.locale());
        this.technical = Objects.requireNonNull(technical, "technical");
        this.reading = new StructureTextPane(messages, technical);
        this.onBriefSaved = (observed, was, now) -> {
            if (was && !now) {
                segments.refresh();
            }
        };
        brief.saving().addListener(new WeakChangeListener<>(onBriefSaved));
    }

    /** The listing of a book's structure; empty when the import answered without a profile. */
    static StructureListing listingOf(final @Nullable BookProfile profile) {
        return profile == null ? new StructureListing(List.of()) : StructureListing.of(profile);
    }

    /** The statistics of a book; all zero when the import answered without a profile. */
    static BookStats statsOf(final @Nullable BookProfile profile) {
        return profile == null ? EMPTY_STATS : profile.stats();
    }

    /**
     * The card holding the reading-order tree.
     *
     * @param listing the structure to draw
     * @param projectId the project whose segments a picked part lists
     * @return the card, id {@code structure-card}
     */
    VBox treeCard(final StructureListing listing, final String projectId) {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(projectId, "projectId");
        log.debug(
                "building the tree card of project {}: {} top-level node(s)",
                projectId,
                listing.roots().size());
        final Label heading = new Label(messages.get(MessageKey.STRUCTURE_READING_ORDER));
        heading.getStyleClass().add("card-title");
        Tips.install(messages, heading, MessageKey.STRUCTURE_READING_ORDER_TIP);
        final VBox card = new VBox(CARD_SPACING, heading, tree(listing.roots(), projectId));
        card.setId("structure-card");
        card.getStyleClass().add("card");
        card.setMinWidth(TREE_MIN_WIDTH);
        return card;
    }

    /**
     * The card holding the segment list of the picked part and the picked segment's text under it.
     *
     * @return the card, id {@code structure-segments-card}
     */
    Node segmentsCard() {
        log.debug("building the segments card");
        final Label heading = new Label(messages.get(MessageKey.STRUCTURE_SEGMENTS_TITLE));
        heading.getStyleClass().add("card-title");
        Tips.install(messages, heading, MessageKey.STRUCTURE_SEGMENTS_TITLE_TIP);
        final VBox card =
                new VBox(CARD_SPACING, heading, new StructureSegmentsPane(segments, messages, reading, technical));
        card.setId("structure-segments-card");
        card.getStyleClass().add("card");
        card.setMinWidth(SEGMENTS_MIN_WIDTH);
        return card;
    }

    /**
     * The card holding the picked segment's text in full, which follows what the segment list picks.
     *
     * @return the card, id {@code structure-text-card}
     */
    Node textCard() {
        log.debug("building the text card");
        final Label heading = new Label(messages.get(MessageKey.IMPORT_BROWSER_TEXT));
        heading.getStyleClass().add("card-title");
        Tips.install(messages, heading, MessageKey.STRUCTURE_PREVIEW_HINT);
        final VBox card = new VBox(CARD_SPACING, heading, reading);
        VBox.setVgrow(reading, Priority.ALWAYS);
        card.setId("structure-text-card");
        card.getStyleClass().add("card");
        card.setMinWidth(TEXT_MIN_WIDTH);
        return card;
    }

    private TreeView<StructureNode> tree(final List<StructureNode> roots, final String projectId) {
        final TreeItem<StructureNode> root = new TreeItem<>();
        roots.forEach(node -> root.getChildren().add(itemOf(node)));
        final TreeView<StructureNode> tree = new TreeView<>(root);
        tree.setId("structure-tree");
        tree.getStyleClass().add("structure-tree");
        tree.setShowRoot(false);
        tree.setEditable(false);
        tree.setCellFactory(view -> new StructureNodeCell(messages, numbers));
        VBox.setVgrow(tree, Priority.ALWAYS);
        tree.setMinHeight(TREE_MIN_HEIGHT);
        // The first part is listed unless it already is: a screen built again for the same book keeps the listing, and
        // the pick is made before the listener below exists so that it does not ask for the same units twice.
        firstWithUnits(root)
                .ifPresentOrElse(
                        first -> {
                            tree.getSelectionModel().select(first);
                            segments.showUnlessShown(
                                    projectId,
                                    StructureListing.partsOf(first.getValue()),
                                    brief.brief().get());
                        },
                        segments::clear);
        tree.getSelectionModel().selectedItemProperty().addListener((observed, was, now) -> picked(projectId, now));
        return tree;
    }

    private void picked(final String projectId, final @Nullable TreeItem<StructureNode> item) {
        final List<UnitPart> parts = item == null ? List.of() : StructureListing.partsOf(item.getValue());
        log.debug("structure node picked, {} unit part(s) to list", parts.size());
        segments.show(projectId, parts);
    }

    private static Optional<TreeItem<StructureNode>> firstWithUnits(final TreeItem<StructureNode> parent) {
        for (final TreeItem<StructureNode> child : parent.getChildren()) {
            if (!StructureListing.partsOf(child.getValue()).isEmpty()) {
                return Optional.of(child);
            }
            final Optional<TreeItem<StructureNode>> deeper = firstWithUnits(child);
            if (deeper.isPresent()) {
                return deeper;
            }
        }
        return Optional.empty();
    }

    // Expanded so that the chapters a person recognises are visible without opening each part.
    private static TreeItem<StructureNode> itemOf(final StructureNode node) {
        final TreeItem<StructureNode> item = new TreeItem<>(node);
        node.children().forEach(child -> item.getChildren().add(itemOf(child)));
        item.setExpanded(true);
        return item;
    }
}
