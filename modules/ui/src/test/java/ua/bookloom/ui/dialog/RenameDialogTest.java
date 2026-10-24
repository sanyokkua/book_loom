package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** The Rename card: when it complains, what its chips and "Use the automatic name again" do, and its way out. */
class RenameDialogTest extends ShellTestBase {

    private static final String CURRENT = "Earth Gravity · my run";
    private static final String AUTOMATIC = "Earth Gravity · EN → UK";
    private static final String USED_TEXT = "That name is already used";
    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");

    private final List<String> answers = new ArrayList<>();

    private static ProjectNameRules rules(final boolean pairFree) {
        return new ProjectNameRules(Set.of(Project.keyOf("Земне тяжіння")), AUTOMATIC, pairFree);
    }

    private static ProjectRow row(final NameOrigin origin) {
        return ProjectFixtures.row(
                ProjectFixtures.project(
                        "p-uk",
                        ProjectFixtures.BOOK_FILE,
                        ProjectFixtures.BOOK_HASH,
                        "en",
                        "uk",
                        origin,
                        CURRENT,
                        OPENED),
                0,
                null);
    }

    private void ask(final NameOrigin origin, final boolean pairFree, final @Nullable String error) {
        onFx(() -> injector.getInstance(RenameDialog.class)
                .ask(
                        row(origin),
                        rules(pairFree),
                        error,
                        null,
                        text -> answers.add("rename:" + text),
                        () -> answers.add("remove"),
                        () -> answers.add("cancelled")));
    }

    private void ask() {
        ask(NameOrigin.USER, true, null);
    }

    private TextField field() {
        return (TextField) required("rename-field");
    }

    private Button button(final String id) {
        return (Button) required(id);
    }

    private void type(final String text) {
        onFx(() -> field().setText(text));
    }

    private boolean isShown(final String id) {
        final Node node = scene.getRoot().lookup("#" + id);
        return node != null && node.isVisible() && node.getScene() != null;
    }

    private boolean isCardShowing() {
        return isShown("rename-card");
    }

    @Test
    void ask_shown_namesTheCardAndOffersRenamePrimaryCancelAndRemove() {
        ask();

        assertThat(textsUnder(required("rename-card")))
                .contains("Rename the project", "Rename", "Cancel", "Remove project…");
        assertThat(button("rename-confirm").getStyleClass()).contains("btn-primary");
        assertThat(field().getText()).isEqualTo(CURRENT);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "AUTO|Now the name is automatic: book title · from → into. It follows the languages until you change it.",
                "USER|Named by you."
            })
    void ask_nameOrigin_decidesWhichBodyTheCardShows(final NameOrigin origin, final String body) {
        ask(origin, true, null);

        assertThat(textsUnder(required("rename-card"))).contains(body);
    }

    // IF the error showed before the person typed, THEN a card opened on a clean name would already look wrong.
    @Test
    void field_beforeTheFirstEdit_showsNoErrorAndRenameIsDisabledBecauseNothingChanged() {
        ask();

        assertThat(isShown("rename-error")).isFalse();
        assertThat(button("rename-confirm").isDisable()).isTrue();
    }

    @Test
    void field_emptied_disablesRenameWithoutAnError() {
        ask();

        type("");

        assertThat(isShown("rename-error")).isFalse();
        assertThat(button("rename-confirm").isDisable()).isTrue();
    }

    // IF the uniqueness check ignored case, THEN two projects of a book could differ only by letter case.
    @ParameterizedTest
    @ValueSource(strings = {"Земне тяжіння", "ЗЕМНЕ ТЯЖІННЯ", "  земне тяжіння "})
    void field_nameOfAnotherProjectOfTheBookAfterAnEdit_showsTheErrorAndDisablesRename(final String typed) {
        ask();

        type(typed);

        assertThat(isShown("rename-error")).isTrue();
        assertThat(((Label) required("rename-error")).getText()).isEqualTo(USED_TEXT);
        assertThat(button("rename-confirm").isDisable()).isTrue();
    }

    // IF a slash were accepted, THEN a name meant for a list could not become part of a file name.
    @Test
    void field_nameWithASlash_showsWhyAndDisablesRename() {
        ask();

        type("Earth/Gravity");

        assertThat(((Label) required("rename-error")).getText())
                .isEqualTo("A name cannot hold / or \\ or invisible control characters");
        assertThat(button("rename-confirm").isDisable()).isTrue();
    }

    // IF a name used only in another book were refused, THEN two books could never share a project name.
    @Test
    void field_nameNotUsedInThisBook_enablesRenameAndSendsTheText() {
        ask();

        type("Tide Tables");
        onFx(() -> button("rename-confirm").fire());

        assertThat(isShown("rename-error")).isFalse();
        assertThat(answers).containsExactly("rename:Tide Tables");
        assertThat(isCardShowing()).isFalse();
    }

    @Test
    void ask_withAnErrorFromTheService_showsItBeforeAnyEdit() {
        ask(NameOrigin.USER, true, USED_TEXT);

        assertThat(isShown("rename-error")).isTrue();
        assertThat(((Label) required("rename-error")).getText()).isEqualTo(USED_TEXT);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "rename-chip-26b|Earth Gravity · my run · 26b",
                "rename-chip-maxQuality|Earth Gravity · my run · Max quality",
                "rename-chip-thinkingLow|Earth Gravity · my run · thinking low",
                "rename-chip-formal|Earth Gravity · my run · formal",
                "rename-chip-v2|Earth Gravity · my run · v2"
            })
    void chip_pressed_appendsItToTheTypedName(final String chipId, final String expected) {
        ask();

        onFx(() -> button(chipId).fire());

        assertThat(field().getText()).isEqualTo(expected);
        assertThat(button("rename-confirm").isDisable()).isFalse();
    }

    // IF an empty field plus a chip gave only the chip, THEN the name would be "· 26b" with nothing to say what it is.
    @Test
    void chip_pressedOnAnEmptyField_startsFromTheCurrentName() {
        ask();
        type("");

        onFx(() -> button("rename-chip-26b").fire());

        assertThat(field().getText()).isEqualTo("Earth Gravity · my run · 26b");
    }

    @ParameterizedTest
    @CsvSource({"USER, true, true", "AUTO, true, false", "USER, false, false"})
    void useAutomatic_nameOriginAndPairFreedom_decideWhetherItIsOffered(
            final NameOrigin origin, final boolean pairFree, final boolean offered) {
        ask(origin, pairFree, null);

        assertThat(isShown("rename-use-auto")).isEqualTo(offered);
    }

    // IF "use the automatic name" kept the origin USER, THEN the name would stop following the languages.
    @Test
    void useAutomatic_pressedThenRename_fillsTheAutomaticNameAndSendsNull() {
        ask();

        onFx(() -> button("rename-use-auto").fire());
        assertThat(field().getText()).isEqualTo(AUTOMATIC);
        onFx(() -> button("rename-confirm").fire());

        assertThat(answers).containsExactly("rename:null");
    }

    @Test
    void useAutomatic_thenTheNameIsEdited_sendsTheTypedTextAsTheUsersOwn() {
        ask();
        onFx(() -> button("rename-use-auto").fire());

        type(AUTOMATIC + " (mine)");
        onFx(() -> button("rename-confirm").fire());

        assertThat(answers).containsExactly("rename:" + AUTOMATIC + " (mine)");
    }

    @Test
    void removeButton_pressed_runsRemoveAndHidesTheCard() {
        ask();

        onFx(() -> button("rename-remove").fire());

        assertThat(answers).containsExactly("remove");
        assertThat(isCardShowing()).isFalse();
    }

    @Test
    void cancelButton_pressed_runsCancelledAndHidesTheCard() {
        ask();

        onFx(() -> button("rename-cancel").fire());

        assertThat(answers).containsExactly("cancelled");
        assertThat(isCardShowing()).isFalse();
    }

    // IF Escape only hid the card, THEN the caller would wait for an answer that never comes.
    @Test
    void escape_onTheCard_runsCancelled() {
        ask();

        onFx(() -> javafx.event.Event.fireEvent(
                scene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false)));

        assertThat(answers).containsExactly("cancelled");
        assertThat(isCardShowing()).isFalse();
    }

    @Test
    void ask_underUkrainian_speaksUkrainian() {
        useLocale(Locale.forLanguageTag("uk"));

        ask(NameOrigin.USER, true, null);

        assertThat(textsUnder(required("rename-card")))
                .contains("Перейменувати проєкт", "Перейменувати", "Видалити проєкт…", "Назву дали ви.");
    }
}
