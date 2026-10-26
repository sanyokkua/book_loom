package ua.bookloom.app;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The window's place and size across "Restart now": the closing copy writes them into the next copy's environment, and
 * the next copy puts its window there. An ordinary launch has no such variable and opens at the default size.
 */
@Slf4j
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor generated below (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class WindowPlacement {

    /** The variable the next copy reads its window's place from. */
    static final String ENVIRONMENT = "BOOKLOOM_WINDOW";

    private static final Pattern SPACES = Pattern.compile(" ");
    private static final int PARTS = 5;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int WIDTH = 2;
    private static final int HEIGHT = 3;
    private static final int MAXIMIZED = 4;

    /**
     * A window's place and size.
     *
     * @param x the left edge
     * @param y the top edge
     * @param width the width, above zero
     * @param height the height, above zero
     * @param maximized whether the window filled its screen
     */
    record Placement(double x, double y, double width, double height, boolean maximized) {}

    /**
     * Writes a window's place and size.
     *
     * @param stage the window as it is now
     * @return the text {@link #parse} reads back
     */
    static String of(final Stage stage) {
        Objects.requireNonNull(stage, "stage");
        return String.format(
                Locale.ROOT,
                "%.0f %.0f %.0f %.0f %b",
                stage.getX(),
                stage.getY(),
                stage.getWidth(),
                stage.getHeight(),
                stage.isMaximized());
    }

    /**
     * Reads a written place and size.
     *
     * @param text the written text, or null when none was written
     * @return the placement, or empty when there is none or it cannot be read
     */
    static Optional<Placement> parse(final @Nullable String text) {
        if (text == null) {
            return Optional.empty();
        }
        final List<String> parts = SPACES.splitAsStream(text.strip()).toList();
        if (parts.size() != PARTS) {
            log.debug("window placement '{}' has {} parts; ignored", text, parts.size());
            return Optional.empty();
        }
        try {
            final Placement placement = new Placement(
                    Double.parseDouble(parts.get(X)),
                    Double.parseDouble(parts.get(Y)),
                    Double.parseDouble(parts.get(WIDTH)),
                    Double.parseDouble(parts.get(HEIGHT)),
                    Boolean.parseBoolean(parts.get(MAXIMIZED)));
            return placement.width() > 0 && placement.height() > 0 ? Optional.of(placement) : Optional.empty();
        } catch (NumberFormatException unreadable) {
            log.debug("window placement '{}' is not numbers; ignored", text);
            return Optional.empty();
        }
    }

    /**
     * Puts the window where the placement says, unless that place is on no screen now (a display was unplugged).
     *
     * @param stage the window, before it is shown
     * @param placement where it was
     */
    static void apply(final Stage stage, final Placement placement) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(placement, "placement");
        if (Screen.getScreensForRectangle(placement.x(), placement.y(), placement.width(), placement.height())
                .isEmpty()) {
            log.info("window placement is on no screen now; the window opens at its default place");
            return;
        }
        log.info(
                "window placed as before the restart: {}x{} maximized={}",
                placement.width(),
                placement.height(),
                placement.maximized());
        stage.setX(placement.x());
        stage.setY(placement.y());
        stage.setWidth(placement.width());
        stage.setHeight(placement.height());
        stage.setMaximized(placement.maximized());
    }
}
