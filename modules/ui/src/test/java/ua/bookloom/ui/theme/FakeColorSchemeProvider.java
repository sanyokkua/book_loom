package ua.bookloom.ui.theme;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Hand-written stand-in for "the operating system says X": the headless platform cannot be told to prefer dark, so
 * the operating-system boundary is the one thing the theme tests replace.
 */
final class FakeColorSchemeProvider implements ColorSchemeProvider {

    private @Nullable ThemeBlock reported;

    FakeColorSchemeProvider(final @Nullable ThemeBlock reported) {
        this.reported = reported;
    }

    /**
     * Changes what the pretend operating system reports from now on.
     *
     * @param reported the scheme to report, or {@code null} for "no preference"
     */
    void report(final @Nullable ThemeBlock reported) {
        this.reported = reported;
    }

    @Override
    public Optional<ThemeBlock> current() {
        return Optional.ofNullable(reported);
    }
}
