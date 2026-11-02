package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.dialog.NarratorDialog;
import ua.bookloom.ui.dialog.NoTargetDialog;

/**
 * What Names &amp; style asks the person.
 *
 * @param modalHost where the Add term card is shown
 * @param noTargetDialog the question asked before starting with entries that have no target
 * @param narratorDialog the question asked before starting when the book is told in the first person
 */
public record NamesDialogs(ModalHost modalHost, NoTargetDialog noTargetDialog, NarratorDialog narratorDialog) {

    /** Rejects a missing part. */
    @Inject
    public NamesDialogs {
        Objects.requireNonNull(modalHost, "modalHost");
        Objects.requireNonNull(noTargetDialog, "noTargetDialog");
        Objects.requireNonNull(narratorDialog, "narratorDialog");
    }
}
