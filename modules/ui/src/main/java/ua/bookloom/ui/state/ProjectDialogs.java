package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.dialog.ConfirmPrompt;
import ua.bookloom.ui.dialog.FindBookPrompt;
import ua.bookloom.ui.dialog.RenamePrompt;
import ua.bookloom.ui.dialog.SamePairPrompt;
import ua.bookloom.ui.dialog.TranslateIntoPrompt;

/**
 * What the project actions ask the person.
 *
 * @param renamePrompt the Rename card
 * @param samePairPrompt the Same language pair card
 * @param translatePrompt the Translate into another language card
 * @param findBookPrompt the Find the book card
 * @param confirm the question asked before a removal
 * @param chooser the file dialog that finds a moved book
 */
public record ProjectDialogs(
        RenamePrompt renamePrompt,
        SamePairPrompt samePairPrompt,
        TranslateIntoPrompt translatePrompt,
        FindBookPrompt findBookPrompt,
        ConfirmPrompt confirm,
        FileChooserPort chooser) {

    /** Rejects a missing part. */
    @Inject
    public ProjectDialogs {
        Objects.requireNonNull(renamePrompt, "renamePrompt");
        Objects.requireNonNull(samePairPrompt, "samePairPrompt");
        Objects.requireNonNull(translatePrompt, "translatePrompt");
        Objects.requireNonNull(findBookPrompt, "findBookPrompt");
        Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(chooser, "chooser");
    }
}
