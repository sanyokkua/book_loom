package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.dialog.ProviderTestDialog;
import ua.bookloom.ui.state.ProviderEngine;
import ua.bookloom.ui.state.ProviderForm;

/**
 * What the open provider's form is built from.
 *
 * @param form what the open provider's form holds, saves and deletes
 * @param engine the engine found at the open provider's address, and the person's corrections of it
 * @param testDialog the card that shows what a test sent and got back
 */
public record ProviderParts(ProviderForm form, ProviderEngine engine, ProviderTestDialog testDialog) {

    /** Rejects a missing part. */
    @Inject
    public ProviderParts {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(testDialog, "testDialog");
    }
}
