package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.Navigator;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.StructureListing;
import ua.bookloom.ui.state.StructureRow;

/**
 * The structure screen's content for an open book: the card listing the units the book was parsed into, in reading
 * order, with the segment total beneath, and the Back and Continue actions under it.
 *
 * <p>The card is a {@link TreeView} although the list is flat, because the tree virtualizes its rows: a book with
 * thousands of units materialises only the cells the viewport shows. Its cells log nothing, since they are refreshed
 * on every scroll, and each cell builds its nodes once and only changes their text as it is reused. A path too long
 * for the row is shortened with an ellipsis rather than cut off: the cell is given no width of its own, so the path
 * label, the only one allowed to shrink, gives way first.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StructureView {

    private static final double CARD_SPACING = 10;
    private static final double ROW_SPACING = 12;
    private static final double SCREEN_SPACING = 14;
    private static final double ACTION_SPACING = 10;

    static Node build(final Document document, final Messages messages, final Navigator navigator) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(navigator, "navigator");
        final StructureListing listing = StructureListing.of(document);
        log.debug("listing {} units, {} segments in total", listing.rows().size(), listing.totalSegments());
        final VBox screen = new VBox(SCREEN_SPACING, card(listing, messages), actions(messages, navigator));
        VBox.setVgrow(screen, Priority.ALWAYS);
        return screen;
    }

    private static Node card(final StructureListing listing, final Messages messages) {
        final TreeView<StructureRow> tree = tree(listing, messages);
        final Label total = new Label(messages.get(MessageKey.STRUCTURE_TOTAL, listing.totalSegments()));
        total.setId("structure-total");
        total.getStyleClass().add("muted");
        final VBox card = new VBox(CARD_SPACING, tree, total);
        card.setId("structure-card");
        card.getStyleClass().add("card");
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private static Node actions(final Messages messages, final Navigator navigator) {
        final Button back = new Button(messages.get(MessageKey.BRIEF_BACK));
        back.setId("structure-back");
        back.getStyleClass().add("btn-ghost");
        back.setOnAction(event -> navigator.navigate(ViewNames.BOOK_BRIEF));
        final Button next = new Button(messages.get(MessageKey.STRUCTURE_CONTINUE));
        next.setId("structure-continue");
        next.getStyleClass().add("btn-primary");
        next.setOnAction(event -> onContinue(navigator));
        final HBox row = new HBox(ACTION_SPACING, back, next);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static void onContinue(final Navigator navigator) {
        final Optional<ViewNames> next = navigator.nextAvailableStep(ViewNames.STRUCTURE);
        log.debug("continue pressed, the next step is {}", next);
        next.ifPresent(navigator::navigate);
    }

    private static TreeView<StructureRow> tree(final StructureListing listing, final Messages messages) {
        final TreeItem<StructureRow> root = new TreeItem<>();
        listing.rows().forEach(row -> root.getChildren().add(new TreeItem<>(row)));
        final TreeView<StructureRow> tree = new TreeView<>(root);
        tree.setId("structure-tree");
        tree.getStyleClass().add("structure-tree");
        tree.setShowRoot(false);
        tree.setEditable(false);
        tree.setCellFactory(view -> new RowCell(messages));
        VBox.setVgrow(tree, Priority.ALWAYS);
        return tree;
    }

    /** Draws one unit as its path, its muted position and its segment count, and does nothing else. */
    private static final class RowCell extends TreeCell<StructureRow> {

        private final Messages messages;
        private final Label href = label("structure-row-href");
        private final Label position = label("muted");
        private final Label count = label("muted");
        private final HBox box = new HBox(ROW_SPACING, href, position, count);

        RowCell(final Messages messages) {
            this.messages = messages;
            // A cell never narrows below its preferred width, so it is given none: it takes the tree's width, and
            // the path label alone may then shrink and show its ellipsis.
            setPrefWidth(0);
            position.setMinWidth(Region.USE_PREF_SIZE);
            count.setMinWidth(Region.USE_PREF_SIZE);
            href.setMinWidth(0);
            HBox.setHgrow(href, Priority.ALWAYS);
            box.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(final @Nullable StructureRow row, final boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setGraphic(null);
                return;
            }
            href.setText(row.href());
            position.setText(messages.get(MessageKey.STRUCTURE_POSITION, row.order()));
            count.setText(messages.get(MessageKey.IMPORT_COUNT_SEGMENTS, row.segmentCount()));
            setGraphic(box);
        }

        private static Label label(final String styleClass) {
            final Label label = new Label();
            label.getStyleClass().add(styleClass);
            label.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            return label;
        }
    }
}
