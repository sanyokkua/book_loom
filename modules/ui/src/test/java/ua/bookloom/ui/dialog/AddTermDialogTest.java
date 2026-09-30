package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.controlsfx.control.ToggleSwitch;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.ui.ModalHost;
import ua.bookloom.ui.ScriptedGlossaryService;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.NamesStyleViewModel;

/** The Add term card: it adds a valid term and stays open, with the reason, for a duplicate or a lock with no target. */
class AddTermDialogTest extends ShellTestBase {

    private static final String PROJECT = "p1";

    private final ScriptedGlossaryService glossary = new ScriptedGlossaryService();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale).glossary(glossary).build();
    }

    private void openDialogOver(final List<GlossaryEntry> held) {
        final NamesStyleViewModel vm = injector.getInstance(NamesStyleViewModel.class);
        final ModalHost host = injector.getInstance(ModalHost.class);
        glossary.willAnswer(Result.ok(held));
        onFx(() -> vm.show(PROJECT));
        WaitForAsyncUtils.waitForFxEvents();
        final AddTermDialog dialog = new AddTermDialog(injector.getInstance(Messages.class), vm, host::hide);
        onFx(() -> host.show(dialog.card(), false));
    }

    @SuppressWarnings("unchecked")
    private void fill(
            final String source, final String target, final TermType type, final Gender gender, final boolean locked) {
        onFx(() -> {
            ((TextField) required("add-term-source")).setText(source);
            ((TextField) required("add-term-target")).setText(target);
            ((ComboBox<TermType>) required("add-term-type")).setValue(type);
            ((ComboBox<Gender>) required("add-term-gender")).setValue(gender);
            ((ToggleSwitch) required("add-term-lock")).setSelected(locked);
        });
    }

    private void confirm() {
        onFx(() -> ((Button) required("add-term-confirm")).fire());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private boolean isShowing() {
        final Node card = scene.getRoot().lookup("#add-term-card");
        return card != null && card.isVisible() && card.getScene() != null;
    }

    private String message() {
        return ((Label) required("add-term-message")).getText();
    }

    private static GlossaryEntry justine() {
        return new GlossaryEntry("e3", PROJECT, "Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true);
    }

    // IF a valid term were not added, THEN the dialog's one purpose would fail.
    @Test
    void confirm_validLockedTerm_addsItAndCloses() throws TimeoutException {
        openDialogOver(List.of());
        glossary.willAnswer(Result.ok(justine()));
        fill("Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, true);

        confirm();

        assertThat(glossary.added())
                .extracting(GlossaryEntry::term, GlossaryEntry::target, GlossaryEntry::type, GlossaryEntry::locked)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Justine", "Жустіна", TermType.CHARACTER, true));
        assertThat(isShowing()).isFalse();
    }

    // IF a duplicate closed the dialog, THEN the person would think it was added.
    @Test
    void confirm_duplicateIgnoringCase_staysOpenWithTheReasonAndAddsNothing() {
        openDialogOver(List.of(justine()));
        fill("justine", "Юстина", TermType.CHARACTER, Gender.FEMALE, false);

        confirm();

        assertThat(isShowing()).isTrue();
        assertThat(message()).isEqualTo("justine is already in the glossary");
        assertThat(glossary.added()).isEmpty();
    }

    // IF a lock with no target were accepted, THEN a locked name with nothing to put back would vanish.
    @Test
    void confirm_lockedWithNoTarget_staysOpenWithTheReasonAndAddsNothing() {
        openDialogOver(List.of());
        fill("Justine", "", TermType.CHARACTER, Gender.FEMALE, true);

        confirm();

        assertThat(isShowing()).isTrue();
        assertThat(message()).isEqualTo("A locked term needs a target.");
        assertThat(glossary.added()).isEmpty();
    }

    // IF Cancel added the term, THEN backing out would not be safe.
    @Test
    void cancel_filledForm_closesAndAddsNothing() {
        openDialogOver(List.of());
        fill("Justine", "Жустіна", TermType.CHARACTER, Gender.FEMALE, false);

        onFx(() -> ((Button) required("add-term-cancel")).fire());

        assertThat(isShowing()).isFalse();
        assertThat(glossary.added()).isEmpty();
    }
}
