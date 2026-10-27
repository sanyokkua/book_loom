package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.control.LayoutClass;

/**
 * The book's text in one widget: the chapters, their segments and the picked segment's text, side by side in one
 * {@link SplitPane} so the person can drag one against another.
 *
 * <p>In a compact content area ({@link LayoutClass}) the three panes would be squeezed under their minimum widths, so
 * the text goes under the segments instead (L7). The three panes are kept as fields and only re-parented, so the
 * picked chapter, the search and the picked segment survive the change; only a change of arrangement is logged.
 */
@Slf4j
final class StructureTextBrowser extends SplitPane {

    static final String ID = "import-browser";
    private static final double MIN_HEIGHT = 360;
    private static final double[] REGULAR_DIVIDERS = {0.22, 0.62};
    private static final double TREE_SHARE = 0.3;
    private static final double SEGMENTS_SHARE = 0.55;

    private final Node chapters;
    private final Node segments;
    private final Node text;
    private final SplitPane stacked = new SplitPane();
    private boolean isCompact;

    StructureTextBrowser(final Node chapters, final Node segments, final Node text) {
        this.chapters = Objects.requireNonNull(chapters, "chapters");
        this.segments = Objects.requireNonNull(segments, "segments");
        this.text = Objects.requireNonNull(text, "text");
        setId(ID);
        getStyleClass().add("import-split");
        stacked.setOrientation(Orientation.VERTICAL);
        stacked.getStyleClass().add("import-split");
        arrange(false);
        LayoutClass.follow(this, this::followClass);
    }

    private void followClass() {
        final boolean compact = LayoutClass.of(this) == LayoutClass.Width.COMPACT;
        if (compact != isCompact) {
            arrange(compact);
        }
    }

    // The nodes leave their old parent before they join the new one, or the scene graph would hold one twice.
    private void arrange(final boolean compact) {
        log.debug("the text browser is {}", compact ? "stacked: the text sits under the segments" : "three panes wide");
        isCompact = compact;
        getItems().clear();
        stacked.getItems().clear();
        if (compact) {
            stacked.getItems().setAll(segments, text);
            stacked.setDividerPositions(SEGMENTS_SHARE);
            getItems().setAll(chapters, stacked);
            setDividerPositions(TREE_SHARE);
        } else {
            getItems().setAll(chapters, segments, text);
            setDividerPositions(REGULAR_DIVIDERS);
        }
    }

    @Override
    protected double computeMinHeight(final double width) {
        return Math.max(MIN_HEIGHT, super.computeMinHeight(width));
    }
}
