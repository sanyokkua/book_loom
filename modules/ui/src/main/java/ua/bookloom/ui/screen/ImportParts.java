package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.ImportGuard;
import ua.bookloom.ui.state.ImportViewModel;

/**
 * What the Import screen shows and goes through.
 *
 * @param viewModel the state this screen shows
 * @param guard what a chosen file goes through before it is opened, so a translation that can continue is not replaced unasked
 * @param bookView the fold and the text browser shown under the verdict of an open book
 */
public record ImportParts(ImportViewModel viewModel, ImportGuard guard, ImportBookView bookView) {

    /** Rejects a missing part. */
    @Inject
    public ImportParts {
        Objects.requireNonNull(viewModel, "viewModel");
        Objects.requireNonNull(guard, "guard");
        Objects.requireNonNull(bookView, "bookView");
    }
}
