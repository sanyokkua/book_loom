package ua.bookloom.ui.theme;

import java.util.Optional;

/**
 * The operating system's colour scheme, behind a seam because the headless test platform cannot be told to prefer
 * dark and a preference-dependent rule could otherwise only be tested on a machine configured to have it.
 */
public interface ColorSchemeProvider {

    /**
     * Reads the scheme at the moment of the call; it is not a live subscription.
     *
     * @return the scheme the operating system reports, or empty when it reports none or the platform cannot say
     */
    Optional<ThemeBlock> current();
}
