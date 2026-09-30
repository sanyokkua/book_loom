package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.state.BookBriefViewModel;
import ua.bookloom.ui.state.CurrentProject;

/**
 * What the book-brief screen tests share, on top of the import screen's scripted port and lookups: showing the brief
 * (always from another view, because navigating to the current one is refused), opening a book and then showing it,
 * and typed access to the controls the brief is drawn from.
 */
abstract class BookBriefScreenTestBase extends ImportScreenTestBase {

    static final Path FRANKENSTEIN = Path.of("Frankenstein.epub");

    /** Shows the brief afresh, the way a person arrives from another screen. */
    void showBrief() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
    }

    /** Opens {@code imported} from {@code source} through the import screen's view model, then shows the brief. */
    void openBookThenShowBrief(final Path source, final ImportedBook imported) throws TimeoutException {
        projects.on(source, Result.ok(imported));
        openImport();
        openBook(source);
        showBrief();
    }

    /** Shows the brief of the English EPUB the specification's first scenario opens. */
    void openFrankensteinThenShowBrief() throws TimeoutException {
        openBookThenShowBrief(FRANKENSTEIN, BookFixtures.frankensteinImport());
    }

    BookBriefViewModel briefModel() {
        return injector.getInstance(BookBriefViewModel.class);
    }

    BookBrief brief() {
        return ThemeTestSupport.onFx(
                () -> injector.getInstance(CurrentProject.class).brief().get());
    }

    @SuppressWarnings("unchecked")
    SearchableCombo<String> box(final String id) {
        return (SearchableCombo<String>) required(id);
    }

    ToggleSwitch toggle(final String id) {
        return (ToggleSwitch) required(id);
    }

    SegmentedButton segmented(final String id) {
        return (SegmentedButton) required(id);
    }

    /** The selected state of each button of the segmented control, in order. */
    List<Boolean> selectedStates(final String id) {
        return segmented(id).getButtons().stream().map(ToggleButton::isSelected).toList();
    }

    ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }
}
