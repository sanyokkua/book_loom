package ua.bookloom.ui.control;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.ThemeTestSupport;

/** A banner's glyph is never squeezed away by its words, however long they are and however narrow the banner. */
@SuppressWarnings("NullAway.Init")
class BannerTest extends FxTestBase {

    private static final double NARROW = 320;
    private static final double TOLERANCE = 0.5;

    private Banner banner;

    @Override
    public void start(final Stage stage) {
        final Scene scene = ThemeTestSupport.themedScene();
        banner = new Banner(
                "test-banner",
                Banner.Role.WARN,
                "⚠",
                "",
                "5 flagged segments have no translation and were written in the source language: ch5 · p12, ch8 · p04,"
                        + " ch8 · p05, ch8 · p06, ch10 · p03");
        final VBox column = new VBox(banner);
        column.setMaxWidth(NARROW);
        ((javafx.scene.layout.StackPane) scene.getRoot()).getChildren().add(column);
        stage.setScene(scene);
        stage.show();
    }

    // IF the glyph shrank with the words, THEN the export dialog's warning showed "…" where its ⚠ belonged.
    @Test
    void layout_longWordsInANarrowBanner_keepTheGlyphWhole() {
        final Label glyph = (Label) banner.lookup(".banner-icon");

        final double[] widths = ThemeTestSupport.onFx(() -> new double[] {glyph.getWidth(), glyph.prefWidth(-1)});

        assertThat(widths[0]).isGreaterThanOrEqualTo(widths[1] - TOLERANCE);
    }
}
