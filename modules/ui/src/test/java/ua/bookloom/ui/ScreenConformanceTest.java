package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Background;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.ConformanceCases.Kind;
import ua.bookloom.ui.ConformanceCases.Part;
import ua.bookloom.ui.ConformanceCases.Screen;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * UI-matches-mockup conformance: for each screen, under each value block, the parts named below paint with the role
 * the reference rendering gives them, and that role resolves to the published catalogue value.
 *
 * <p>One {@link ConformanceCases.Screen} entry per screen keeps a later screen task to a single added case: it names what to show,
 * and for each part the selector that finds it, whether the role fills its background or draws its border, the role,
 * and the light and dark values published for that role. The values are written out by hand from {@code theme.css}'s
 * published catalogue, never read back from the code under test. Every (screen, part, block) is its own test case, so
 * a failure names exactly one of them. Pixel placement is deliberately not asserted.
 */
class ScreenConformanceTest extends ConformanceTestBase {

    static Stream<Arguments> partsInEachBlock() {
        return ConformanceCases.SCREENS.stream()
                .flatMap(screen -> screen.parts().stream()
                        .flatMap(part -> Stream.of(ThemeMode.LIGHT, ThemeMode.DARK)
                                .map(block -> Arguments.of(
                                        Named.of(screen.name(), screen),
                                        Named.of(
                                                part.kind() + " " + part.selector() + " (-color-" + part.role() + ")",
                                                part),
                                        block))));
    }

    private static Paint paintOf(final Node node, final Kind kind) {
        return switch (kind) {
            case BACKGROUND -> {
                final Background background = ((Region) node).getBackground();
                assertThat(background).as("%s must paint a background", node).isNotNull();
                yield background.getFills().get(0).getFill();
            }
            case BORDER -> {
                final Border border = ((Region) node).getBorder();
                assertThat(border).as("%s must draw a border", node).isNotNull();
                yield border.getStrokes().get(0).getBottomStroke();
            }
            case TEXT -> ((Labeled) node).getTextFill();
        };
    }

    // IF a part of the screen painted with another role, or a role resolved differently, in either value block, THEN
    // the screen would not match the reference rendering in that block.
    @ParameterizedTest(name = "{0}: {1} under {2}")
    @MethodSource("partsInEachBlock")
    void screen_part_underEachBlock_paintsWithThePublishedRoleValue(
            final Screen screen, final Part part, final ThemeMode block) throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final Node node = scene.getRoot().lookup(part.selector());

        assertThat(node)
                .as("%s must exist on %s", part.selector(), screen.name())
                .isNotNull();
        ThemeTestSupport.assertSameColour(
                paintOf(node, part.kind()),
                part.expected(block),
                "%s %s (-color-%s) under %s".formatted(part.kind(), part.selector(), part.role(), block));
    }
}
