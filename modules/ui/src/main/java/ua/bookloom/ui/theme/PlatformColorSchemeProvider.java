package ua.bookloom.ui.theme;

import java.util.Optional;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the JavaFX platform preference. Must be called on the JavaFX Application Thread.
 *
 * <p>A platform that cannot report a preference must not stop the window from opening, so any failure here degrades
 * to "no preference" rather than crossing the boundary.
 */
@Slf4j
public final class PlatformColorSchemeProvider implements ColorSchemeProvider {

    /** Guice constructs this by its public no-argument constructor. */
    public PlatformColorSchemeProvider() {
        // Stateless.
    }

    @Override
    public Optional<ThemeBlock> current() {
        try {
            final ColorScheme scheme = Platform.getPreferences().getColorScheme();
            log.debug("platform colour scheme reports {}", scheme);
            return Optional.ofNullable(scheme).map(PlatformColorSchemeProvider::toBlock);
        } catch (RuntimeException e) {
            log.warn("platform colour scheme unavailable, treating as no preference", e);
            return Optional.empty();
        }
    }

    private static ThemeBlock toBlock(final ColorScheme scheme) {
        return switch (scheme) {
            case DARK -> ThemeBlock.DARK;
            case LIGHT -> ThemeBlock.LIGHT;
        };
    }
}
