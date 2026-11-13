package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.state.SectionMemory;

/**
 * What the translating screen remembers and asks.
 *
 * @param sections the session's memory of which sections the person left open
 * @param narratorDialog the question asked before a start when the book is told in the first person
 * @param confirm the question asked before the run is stopped
 */
public record TranslatingTools(SectionMemory sections, NarratorDialog narratorDialog, ConfirmPrompt confirm) {

    /** Rejects a missing part. */
    @Inject
    public TranslatingTools {
        Objects.requireNonNull(sections, "sections");
        Objects.requireNonNull(narratorDialog, "narratorDialog");
        Objects.requireNonNull(confirm, "confirm");
    }
}
