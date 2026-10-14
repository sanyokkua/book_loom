package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/**
 * The fold stages of a {@link PriorityRow}: the first stage leaves when the elastic part would go below its comfort
 * width, the next only when it would go below its own minimum, so the elastic part is never squeezed to nothing.
 */
@SuppressWarnings("NullAway.Init")
class PriorityRowFoldTest extends FxTestBase {

    private static final double FLOOR = 120;
    private static final double HEIGHT = 40;

    private PriorityRow row;
    private Region search;
    private Region review;
    private Region translate;
    private Region importButton;
    private Region more;

    private static Region part(final double preferred, final double minimum) {
        final Region region = new Region();
        region.setPrefSize(preferred, HEIGHT);
        region.setMinSize(minimum, HEIGHT);
        return region;
    }

    // Parts 300 (min 120) + 80 + 100 + 100 + 90 + 90 and a 40 menu, 8 apart: 800 whole, 652 with Import and Export
    // folded, 472 with the search at its floor, 380 more with Review and Translate folded as well.
    private void build() {
        search = part(300, FLOOR);
        review = part(100, 100);
        translate = part(100, 100);
        importButton = part(90, 90);
        final Region export = part(90, 90);
        more = part(40, 40);
        row = new PriorityRow(8);
        row.getChildren().addAll(search, part(80, 80), review, translate, importButton, export, more);
        row.elastic(search, 300);
        row.fold(more, importButton, export);
        row.fold(more, review, translate);
    }

    private void layoutAt(final double width) {
        ThemeTestSupport.onFx(() -> {
            build();
            row.resize(width, HEIGHT);
            row.layout();
            return null;
        });
    }

    private boolean isShown(final Region node) {
        return ThemeTestSupport.onFx(() -> node.isVisible() && node.isManaged());
    }

    @Test
    void layout_roomForEverything_foldsNothing() {
        layoutAt(800);

        assertThat(isShown(importButton)).isTrue();
        assertThat(isShown(review)).isTrue();
        assertThat(isShown(more)).isFalse();
    }

    // IF Review went first, THEN the actions used most would vanish before the ones used least.
    @ParameterizedTest
    @CsvSource({"700", "500"})
    void layout_searchWouldGoBelowComfort_foldsOnlyTheFirstStage(final double width) {
        layoutAt(width);

        assertThat(isShown(importButton)).isFalse();
        assertThat(isShown(review)).isTrue();
        assertThat(isShown(translate)).isTrue();
        assertThat(isShown(more)).isTrue();
    }

    // IF the search kept shrinking, THEN it would reach nothing; below its floor the second stage leaves instead.
    @ParameterizedTest
    @CsvSource({"450", "420"})
    void layout_searchWouldGoBelowItsFloor_foldsTheSecondStageToo(final double width) {
        layoutAt(width);

        assertThat(isShown(review)).isFalse();
        assertThat(isShown(translate)).isFalse();
        assertThat(isShown(more)).isTrue();
        assertThat(ThemeTestSupport.onFx(() -> search.getWidth())).isGreaterThanOrEqualTo(FLOOR);
    }

    @Test
    void layout_widenedAgain_bringsEveryFoldedPartBack() {
        layoutAt(420);

        ThemeTestSupport.onFx(() -> {
            row.resize(800, HEIGHT);
            row.layout();
            return null;
        });

        assertThat(isShown(review)).isTrue();
        assertThat(isShown(importButton)).isTrue();
        assertThat(isShown(more)).isFalse();
    }
}
