package ua.bookloom.ui.screen;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import org.controlsfx.control.SegmentedButton;
import org.controlsfx.control.ToggleSwitch;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.state.BookBriefViewModel;

/**
 * What the book-brief screen tests share, on top of the import screen's scripted port and lookups: showing the brief
 * (always from another view, because navigating to the current one is refused), opening a book and then showing it,
 * and typed access to the controls the brief is drawn from.
 */
abstract class BookBriefScreenTestBase extends ImportScreenTestBase {

    /** Shows the brief afresh, the way a person arrives from another screen. */
    void showBrief() {
        onFx(() -> shell.activate(ViewNames.IMPORT));
        onFx(() -> shell.activate(ViewNames.BOOK_BRIEF));
    }

    /** Opens {@code document} from {@code source} through the import screen's view model, then shows the brief. */
    void openBookThenShowBrief(final Path source, final Document document) throws TimeoutException {
        port.on(source, Result.ok(document));
        openImport();
        openBook(source);
        showBrief();
    }

    BookBriefViewModel briefModel() {
        return injector.getInstance(BookBriefViewModel.class);
    }

    /** The target-language picker, whose items are the language codes. */
    @SuppressWarnings("unchecked")
    ComboBox<String> targetPicker() {
        return (ComboBox<String>) required("brief-target");
    }

    ToggleSwitch toggle(final String id) {
        return (ToggleSwitch) required(id);
    }

    SegmentedButton segmented(final String id) {
        return (SegmentedButton) required(id);
    }

    /** Whether the node with this id is a checkbox, which the reference does not draw anywhere on this screen. */
    boolean isCheckBox(final String id) {
        return required(id) instanceof CheckBox;
    }

    /** The selected state of each button of the segmented control, in order. */
    List<Boolean> selectedStates(final String id) {
        return segmented(id).getButtons().stream().map(ToggleButton::isSelected).toList();
    }

    /** The lower-cased words shown by the picker with this id, split on anything that is not a letter or digit. */
    List<String> wordsOf(final String id) {
        final Object shown = ((ComboBox<?>) required(id)).getValue();
        return List.of(String.valueOf(shown).toLowerCase(Locale.ROOT).split("\\W+"));
    }

    ViewNames currentView() {
        return ThemeTestSupport.onFx(() -> navigator.currentView().get());
    }

    String chosenTarget() {
        return ThemeTestSupport.onFx(() -> briefModel().targetLanguage().get());
    }

    String shownDestination() {
        return ThemeTestSupport.onFx(() -> briefModel().destination().get());
    }
}
