package ua.bookloom.ui.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.api.project.Project;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ShellTestBase;
import ua.bookloom.ui.control.SearchableCombo;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.ProjectNameRules;
import ua.bookloom.ui.state.ProjectRow;

/** The Translate into another language card: the language list, the optional or required name and Create. */
class TranslateIntoDialogTest extends ShellTestBase {

    private static final Instant OPENED = Instant.parse("2026-10-21T10:00:00Z");
    private static final ProjectNameRules RULES = new ProjectNameRules(
            Set.of(Project.keyOf("Earth Gravity · EN → UK"), Project.keyOf("Земне тяжіння")), null, true);

    private final List<String> answers = new ArrayList<>();

    private static BookGroup group(final @Nullable String sourceLanguage) {
        final Project project = ProjectFixtures.project(
                "p-uk",
                ProjectFixtures.BOOK_FILE,
                ProjectFixtures.BOOK_HASH,
                "en",
                "uk",
                NameOrigin.AUTO,
                "Earth Gravity · EN → UK",
                OPENED);
        final Project shown =
                sourceLanguage == null ? project.withBrief(project.brief().withLanguages(null, "uk")) : project;
        final ProjectRow row = ProjectFixtures.row(shown, 0, null);
        return new BookGroup(
                ProjectFixtures.BOOK_HASH,
                "Earth Gravity",
                ProjectFixtures.BOOK_FILE,
                shown.format(),
                ProjectFixtures.BOOK_SEGMENTS,
                List.of(row));
    }

    private void ask(final @Nullable String sourceLanguage) {
        onFx(() -> injector.getInstance(TranslateIntoDialog.class)
                .ask(
                        group(sourceLanguage),
                        RULES,
                        (target, name) -> answers.add("create:" + target + ":" + name),
                        () -> answers.add("cancelled")));
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> languages() {
        return (ComboBox<String>) required("translateinto-into");
    }

    private void choose(final String tag) {
        onFx(() -> languages().getSelectionModel().select(tag));
    }

    private TextField name() {
        return (TextField) required("translateinto-name");
    }

    private Button create() {
        return (Button) required("translateinto-create");
    }

    private String listed(final String tag) {
        return languages().getConverter().toString(tag);
    }

    @Test
    void ask_shown_titlesTheCardNamesTheBookAndStatesWhatIsCopied() {
        ask("en");

        assertThat(textsUnder(required("translateinto-card")))
                .contains(
                        "Translate into another language",
                        "Earth Gravity — a new project for the same book. Nothing is imported again.",
                        "Translate into",
                        "From",
                        "The brief — tone, how to translate, narrator, quality",
                        "Glossary and recurring terms — the entries, kinds and genders; translations left empty",
                        "Create and open the brief",
                        "Cancel");
        assertThat(scene.getRoot().lookupAll(".check-box")).isEmpty();
    }

    @Test
    void languages_bookSourceTag_isNotOfferedAsATarget() {
        ask("en");

        assertThat(languages().getItems()).doesNotContain("en").contains("uk", "de");
    }

    // IF a used language carried no mark, THEN the person would pick it blind and meet the name rule afterwards.
    @Test
    void languages_oneAlreadyTranslated_isMarkedWithItsProjectCount() {
        ask("en");

        assertThat(listed("uk")).isEqualTo("Ukrainian — already in 1 project");
        assertThat(listed("de")).isEqualTo("German");
    }

    // IF typing did not narrow the list, THEN Down and Return would pick the list's first language (Belarusian).
    @Test
    void languages_typedPolThenDownAndReturn_choosesPolish() {
        ask("en");

        clickOn(languages());
        write("Pol");
        type(KeyCode.DOWN);
        type(KeyCode.ENTER);

        assertThat(((SearchableCombo<String>) languages()).getCommitted()).isEqualTo("pl");
        assertThat(name().getPromptText()).isEqualTo("Earth Gravity · EN → PL");
    }

    @Test
    void from_sourceLanguageKnown_isTheReadOnlyLanguageName() {
        ask("en");

        assertThat(((Label) required("translateinto-from")).getText()).isEqualTo("English");
    }

    @Test
    void from_sourceLanguageNotDetected_saysItIsFoundOutFromTheText() {
        ask(null);

        assertThat(((Label) required("translateinto-from")).getText()).isEqualTo("Found out from the text");
    }

    @Test
    void create_beforeALanguageIsChosen_isDisabled() {
        ask("en");

        assertThat(create().isDisable()).isTrue();
    }

    // IF an unused pair demanded a name, THEN the common case would need a typed name for nothing.
    @Test
    void unusedLanguage_showsTheAutomaticNameAsPlaceholderAndCreateSendsNoName() {
        ask("en");

        choose("de");

        assertThat(name().getPromptText()).isEqualTo("Earth Gravity · EN → DE");
        assertThat(create().isDisable()).isFalse();
        onFx(() -> create().fire());
        assertThat(answers).containsExactly("create:de:null");
    }

    @Test
    void unusedLanguage_withATypedName_createSendsIt() {
        ask("en");
        choose("de");

        onFx(() -> name().setText("Earth Gravity for Mum"));
        onFx(() -> create().fire());

        assertThat(answers).containsExactly("create:de:Earth Gravity for Mum");
    }

    // IF a used pair could be created unnamed, THEN the book would hold two projects with one automatic name.
    @Test
    void usedLanguage_requiresAFreeNameBeforeCreateIsEnabled() {
        ask("en");

        choose("uk");

        assertThat(create().isDisable()).isTrue();
        onFx(() -> name().setText("Earth Gravity · 26b"));
        assertThat(create().isDisable()).isFalse();
        onFx(() -> create().fire());
        assertThat(answers).containsExactly("create:uk:Earth Gravity · 26b");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Земне тяжіння", "ЗЕМНЕ ТЯЖІННЯ", "earth gravity · en → uk"})
    void usedLanguage_nameTakenInTheBook_showsTheErrorAndKeepsCreateDisabled(final String typed) {
        ask("en");
        choose("uk");

        onFx(() -> name().setText(typed));

        assertThat(isVisibleInScene("translateinto-error")).isTrue();
        assertThat(((Label) required("translateinto-error")).getText()).isEqualTo("That name is already used");
        assertThat(create().isDisable()).isTrue();
    }

    @Test
    void cancelButton_pressed_runsCancelledAndHidesTheCard() {
        ask("en");

        onFx(() -> ((Button) required("translateinto-cancel")).fire());

        assertThat(answers).containsExactly("cancelled");
        assertThat(isVisibleInScene("translateinto-card")).isFalse();
    }
}
