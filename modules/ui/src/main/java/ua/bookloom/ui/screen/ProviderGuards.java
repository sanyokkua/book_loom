package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.ProviderInUse;
import ua.bookloom.ui.state.ProviderSwitch;

/**
 * What guards the provider settings against a wrong move.
 *
 * @param providerSwitch what Use this one does
 * @param guard asks before a move would drop typing
 * @param tabEntries what entering a tab reads again
 * @param inUse whether the open provider is the one the translation run uses
 */
public record ProviderGuards(
        ProviderSwitch providerSwitch, SettingsTabGuard guard, SettingsTabEntries tabEntries, ProviderInUse inUse) {

    /** Rejects a missing part. */
    @Inject
    public ProviderGuards {
        Objects.requireNonNull(providerSwitch, "providerSwitch");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(tabEntries, "tabEntries");
        Objects.requireNonNull(inUse, "inUse");
    }
}
