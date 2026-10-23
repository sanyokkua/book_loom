package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.ui.ReviewDeskFixtures;

/** The desk fits the smallest window in both languages: its footer buttons stay inside the card. */
class ReviewDeskLayoutTest extends ReviewDeskTestBase {

    private Bounds sceneBounds(final String id) {
        return fx(() -> {
            final Node node = required(id);
            return node.localToScene(node.getBoundsInLocal());
        });
    }

    // IF a longer Ukrainian word pushed the footer out of the card at 960 by 640, THEN the person could not reach
    // Accept; Close, More and the main button all stay inside it.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void footer_smallestWindow_keepsCloseMoreAndMainInsideTheCard(final String language) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(960, 640);

        openDeskWith(ReviewDeskFixtures.sixToReview(), List.of());

        final Bounds card = sceneBounds("review-desk");
        assertThat(List.of("review-close", "review-more", "review-main")).allSatisfy(id -> {
            final Bounds bounds = sceneBounds(id);
            assertThat(bounds.getMaxX()).as(id).isLessThanOrEqualTo(card.getMaxX());
            assertThat(bounds.getMaxY()).as(id).isLessThanOrEqualTo(card.getMaxY());
            assertThat(bounds.getMinX()).as(id).isGreaterThanOrEqualTo(card.getMinX());
        });
    }

    // IF the strip pushed the translation editor out of the card at 960 by 640, THEN the person could not see what
    // they type or which mark is missing; both sit inside the card with a marked segment open.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void editorAndMarkStrip_smallestWindowMarkedSegment_bothStayInsideTheCard(final String language) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(960, 640);

        openDeskWith(ReviewDeskFixtures.sixToReview(), List.of());

        final Bounds card = sceneBounds("review-desk");
        assertThat(List.of("review-target", "review-mark-strip")).allSatisfy(id -> {
            final Bounds bounds = sceneBounds(id);
            assertThat(bounds.getHeight()).as(id).isGreaterThan(0);
            assertThat(bounds.getMaxY()).as(id).isLessThanOrEqualTo(card.getMaxY());
            assertThat(bounds.getMaxX()).as(id).isLessThanOrEqualTo(card.getMaxX());
        });
        assertThat(isShown("review-mark-strip")).isTrue();
        assertThat(sceneBounds("review-target").getHeight()).isGreaterThanOrEqualTo(60);
    }

    // IF the strip kept one line, THEN a long wording ended in an ellipsis and the person could not read which mark
    // is missing; its height holds every wrapped line.
    @ParameterizedTest
    @ValueSource(strings = {"en", "uk"})
    void markStrip_longWordingSmallestWindow_showsEveryLine(final String language) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        resizeScene(960, 640);
        openDeskWith(ReviewDeskFixtures.sixToReview(), List.of());

        final double[] heights = fx(() -> {
            final javafx.scene.control.Label strip = (javafx.scene.control.Label) required("review-mark-strip");
            return new double[] {strip.getHeight(), strip.prefHeight(strip.getWidth())};
        });

        assertThat(heights[0]).isGreaterThanOrEqualTo(heights[1]);
        assertThat(heights[1]).isGreaterThan(20);
    }
}
