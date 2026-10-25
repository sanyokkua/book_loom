package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectEdits.RenameCall;
import ua.bookloom.ui.state.RunStateSource;

/**
 * The project name line of the languages card (book-brief "Show the project name on the brief and let the person
 * rename it"): the stored name, whether it follows the languages or was typed, and a Rename... link that opens the same
 * question as the Projects screen.
 */
class BookBriefProjectNameScreenTest extends BriefRunScreenTestBase {

    private static final String LONG_NAME =
            "A Very Long Name That Goes On And On Through Every Word A Title Could Hold Until No Line Can Fit It At All";

    // IF the line showed a name derived on the screen, THEN it could differ from the name the project is stored under.
    @Test
    void nameLine_automaticName_showsTheStoredNameAndSaysItFollowsTheLanguages() throws Exception {
        openAndShowBrief();

        assertThat(textOfLabel("brief-project-name")).isEqualTo(AUTOMATIC_NAME);
        assertThat(textOfLabel("brief-project-origin")).isEqualTo("automatic — follows the languages");
        assertThat(button("brief-rename").getText()).isEqualTo("Rename...");
    }

    @Test
    void nameLine_label_saysProjectName() throws Exception {
        openAndShowBrief();

        assertThat(textOf("brief-project-line")).startsWith("Project name");
    }

    // IF a typed name were shown as automatic, THEN a person would expect it to change with the languages.
    @Test
    void nameLine_nameTypedByThePerson_showsItWithNamedByYou() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.USER, "Земне тяжіння")));
        readyToStart();
        showBrief();

        assertThat(textOfLabel("brief-project-name")).isEqualTo("Земне тяжіння");
        assertThat(textOfLabel("brief-project-origin")).isEqualTo("named by you");
    }

    // IF a long name were cut with no tooltip, THEN the person could not read the name the project is stored by.
    @Test
    void nameLine_longName_isCutWithTheWholeNameInItsTooltip() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.USER, LONG_NAME)));
        readyToStart();
        showBrief();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(read(() -> ua.bookloom.ui.FitProbe.cutParts(scene.getRoot())))
                .doesNotContain(LONG_NAME);
        assertThat(read(() -> ua.bookloom.ui.TooltipProbe.tipText(required("brief-project-name"))))
                .isEqualTo(LONG_NAME);
    }

    // IF Rename gave way to a long name, THEN it would shrink to an ellipsis no one could read (the owner's report).
    @ParameterizedTest(name = "{0} px")
    @ValueSource(doubles = {CONTENT_AT_MINIMUM_WIDTH, 1000, 1040, 1100, 1250, 1440, 1700})
    void nameLine_longNameAtEachWidth_keepsRenameWholeAndOnlyTheNameGivesWay(final double width) throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.USER, LONG_NAME)));
        readyToStart();
        showBrief();
        resizeScene(width, CONTENT_AT_MINIMUM_HEIGHT);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(read(() -> ua.bookloom.ui.FitProbe.cutParts(scene.getRoot())))
                .isEmpty();
        assertThat(read(() -> ua.bookloom.ui.FitProbe.squeezed(scene.getRoot())))
                .isEmpty();
        assertThat(read(() -> ua.bookloom.ui.FitProbe.overflowing(scene.getRoot(), width)))
                .isEmpty();
    }

    // IF the title bar derived its own name, THEN it would show a different name from the one the project is stored by.
    @Test
    void titleBar_nameTypedByThePerson_showsTheStoredName() throws Exception {
        projects.willList(List.of(stored("uk", NameOrigin.USER, "Земне тяжіння")));
        readyToStart();
        showBrief();

        assertThat(read(() ->
                        injector.getInstance(RunStateSource.class).projectName().get()))
                .isEqualTo("Земне тяжіння");
    }

    // IF Rename... asked its own question, THEN the Brief and the Projects screen would rename by different rules.
    @Test
    void rename_pressed_asksTheRenameQuestionOfTheOpenProjectOnce() throws Exception {
        openAndShowBrief();

        onFx(() -> button("brief-rename").fire());
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(rename.asked()).hasSize(1);
        assertThat(rename.asked().getFirst().row().summary().project().id()).isEqualTo(PROJECT_ID);
        assertThat(rename.asked().getFirst().rules().automaticName()).isEqualTo(AUTOMATIC_NAME);
    }

    @Test
    void rename_confirmed_savesTheNameAndTheLineShowsItAsNamedByYou() throws Exception {
        openAndShowBrief();
        onFx(() -> button("brief-rename").fire());
        ProjectFixtures.awaitBackgroundIdle(injector);
        final var renamed = stored("uk", NameOrigin.USER, "Gothic Gravity");
        projects.edits().onRename(Result.ok(new ProjectChange.Saved(renamed.project())));
        projects.willList(List.of(renamed));

        onFx(() -> rename.rename("Gothic Gravity"));
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(projects.edits().renames()).containsExactly(new RenameCall(PROJECT_ID, "Gothic Gravity"));
        assertThat(textOfLabel("brief-project-name")).isEqualTo("Gothic Gravity");
        assertThat(textOfLabel("brief-project-origin")).isEqualTo("named by you");
    }

    @Test
    void rename_cancelled_callsNothingAndKeepsTheName() throws Exception {
        openAndShowBrief();
        onFx(() -> button("brief-rename").fire());
        ProjectFixtures.awaitBackgroundIdle(injector);

        onFx(rename::cancel);
        ProjectFixtures.awaitBackgroundIdle(injector);

        assertThat(projects.edits().renames()).isEmpty();
        assertThat(textOfLabel("brief-project-name")).isEqualTo(AUTOMATIC_NAME);
    }

    // IF the line were read once, THEN an automatic name would keep the old pair after the target changed.
    @Test
    void nameLine_targetChangedAndSaved_followsTheNameTheServiceNowLists() throws Exception {
        openAndShowBrief();
        final int listedBefore = projects.listCalls();
        projects.willList(List.of(stored("de", NameOrigin.AUTO, "Frankenstein · EN → DE")));

        onFx(() -> briefViewModel().setTargetLanguage("de"));
        ProjectFixtures.awaitBackgroundIdle(injector);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.listCalls()).isGreaterThan(listedBefore);
        assertThat(textOfLabel("brief-project-name")).isEqualTo("Frankenstein · EN → DE");
    }

    @Test
    void nameLine_underUkrainian_isWordedInUkrainian() throws Exception {
        useLocale(Locale.forLanguageTag("uk"));
        openAndShowBrief();

        assertThat(textOfLabel("brief-project-origin")).isEqualTo("автоматична — змінюється разом з мовами");
        assertThat(button("brief-rename").getText()).isEqualTo("Перейменувати...");
    }
}
