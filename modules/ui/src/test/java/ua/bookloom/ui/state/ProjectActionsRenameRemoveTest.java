package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NameOrigin;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ProjectFixtures;
import ua.bookloom.ui.ScriptedProjectEdits.RenameCall;
import ua.bookloom.ui.ViewNames;

/** What the Rename and Remove row actions ask the service; the confirmation of Remove is the real card. */
class ProjectActionsRenameRemoveTest extends ProjectActionsTestBase {

    // IF another book's names counted as taken, THEN two books could never share a project name; IF the own name
    // counted, THEN the first edit would clash with itself.
    @Test
    void rename_siblingsLoadedFresh_rulesCountOnlyTheOtherProjectsOfTheSameBook() throws Exception {
        listStored(UK, DE, OTHER);

        renameRowOfUk();

        assertThat(rename.asked()).hasSize(1);
        final ProjectNameRules rules = rename.asked().getFirst().rules();
        assertThat(rules.check("Земне тяжіння")).isEqualTo(ProjectNameRules.Check.USED);
        assertThat(rules.check("Shared name")).isEqualTo(ProjectNameRules.Check.OK);
        assertThat(rules.check("Earth Gravity · EN → UK")).isEqualTo(ProjectNameRules.Check.OK);
        assertThat(rules.automaticName()).isEqualTo("Earth Gravity · EN → UK");
        assertThat(rules.pairFree()).isTrue();
        assertThat(rename.asked().getFirst().error()).isNull();
        assertThat(rename.asked().getFirst().row().summary().project().id()).isEqualTo("p-uk");
    }

    @Test
    void rename_anotherProjectOfTheBookHoldsThePair_pairIsNotFree() throws Exception {
        listStored(UK, book("p-uk2", "uk", NameOrigin.USER, "Earth Gravity · 26b", ProjectFixtures.BOOK_HASH));

        renameRowOfUk();

        assertThat(rename.asked().getFirst().rules().pairFree()).isFalse();
    }

    @Test
    void rename_confirmed_callsTheServiceWithTheTypedNameAndRefreshesTheList() throws Exception {
        listStored(UK, DE);
        service.edits().onRename(Result.ok(new ProjectChange.Saved(UK.withName("New name", NameOrigin.USER))));
        renameRowOfUk();
        final int listedBefore = service.listCalls();

        onFx(() -> rename.rename("New name"));
        settle();

        assertThat(service.edits().renames()).containsExactly(new RenameCall("p-uk", "New name"));
        assertThat(service.listCalls()).isGreaterThan(listedBefore);
    }

    // IF "use the automatic name again" sent its text, THEN the name would become the person's own and stop following.
    @Test
    void rename_useAutomaticName_callsTheServiceWithNull() throws Exception {
        listStored(UK, DE);
        service.edits().onRename(Result.ok(new ProjectChange.Saved(UK)));
        renameRowOfUk();

        onFx(() -> rename.rename(null));
        settle();

        assertThat(service.edits().renames()).containsExactly(new RenameCall("p-uk", null));
    }

    @Test
    void rename_cancelled_makesNoServiceCall() throws Exception {
        listStored(UK, DE);
        renameRowOfUk();

        onFx(rename::cancel);
        settle();

        assertThat(service.edits().renames()).isEmpty();
        assertThat(service.removedProjects()).isEmpty();
    }

    // IF a clash found by the service were swallowed, THEN the person would see the card close and the name unchanged.
    @Test
    void rename_serviceSaysNameInUse_asksAgainWithTheError() throws Exception {
        listStored(UK, DE);
        service.edits().onRename(Result.ok(new ProjectChange.NameInUse(DE)));
        renameRowOfUk();

        onFx(() -> rename.rename("Земне тяжіння"));
        settle();

        assertThat(rename.asked()).hasSize(2);
        assertThat(rename.asked().get(1).error()).isEqualTo("That name is already used");
        assertThat(rename.asked().get(1).typed()).isEqualTo("Земне тяжіння");
    }

    @Test
    void rename_removeChosenOnTheCard_asksTheRemoveConfirmation() throws Exception {
        listStored(UK, DE);
        renameRowOfUk();

        onFx(rename::remove);

        assertThat(required("confirm-card")).isNotNull();
        assertThat(service.removedProjects()).isEmpty();
    }

    @Test
    void remove_confirmationShown_namesTheProjectAndWhatStays() {
        onFx(() -> actions().remove(rowOf(UK)));

        assertThat(textsUnder(required("confirm-card")))
                .contains(
                        "Remove “Earth Gravity · EN → UK”?",
                        "This project’s saved work — brief, glossary, translations — is deleted from BookLoom. "
                                + "The book file, exported files and the book’s other projects stay.",
                        "Remove project",
                        "Cancel");
    }

    @Test
    void remove_confirmed_removesTheProjectAndRefreshesTheList() throws Exception {
        listStored(UK, DE);
        onFx(() -> actions().remove(rowOf(UK)));
        final int listedBefore = service.listCalls();

        onFx(() -> ((Button) required("confirm-yes")).fire());
        settle();

        assertThat(service.removedProjects()).containsExactly("p-uk");
        assertThat(service.listCalls()).isGreaterThan(listedBefore);
    }

    // IF Cancel removed anyway, THEN a slip of the mouse would delete a translation.
    @Test
    void remove_cancelled_removesNothing() throws Exception {
        onFx(() -> actions().remove(rowOf(UK)));

        onFx(() -> ((Button) required("confirm-cancel")).fire());
        settle();

        assertThat(service.removedProjects()).isEmpty();
    }

    // IF the open project were removed and stayed open, THEN every step would work on a project that is gone.
    @Test
    void remove_confirmedForTheOpenProject_closesItInTheWindow() throws Exception {
        listStored(UK, DE);
        final CurrentProject current = injector.getInstance(CurrentProject.class);
        onFx(() -> current.open(openedBookOf("p-uk")));
        final ProjectRow open =
                new ProjectRow(summaryOf(UK), 0, ProjectStatus.NOT_STARTED, true, ViewNames.BOOK_BRIEF, "");

        onFx(() -> actions().remove(open));
        onFx(() -> ((Button) required("confirm-yes")).fire());
        settle();

        assertThat(service.removedProjects()).containsExactly("p-uk");
        assertThat(current.book().get()).isNull();
    }

    @Test
    void remove_confirmedForAnotherProject_keepsTheOpenOneOpen() throws Exception {
        listStored(UK, DE);
        final CurrentProject current = injector.getInstance(CurrentProject.class);
        onFx(() -> current.open(openedBookOf("p-de")));

        onFx(() -> actions().remove(rowOf(UK)));
        onFx(() -> ((Button) required("confirm-yes")).fire());
        settle();

        assertThat(service.removedProjects()).containsExactly("p-uk");
        assertThat(current.book().get()).isNotNull();
        assertThat(current.book().get().projectId()).isEqualTo("p-de");
    }

    private static OpenedBook openedBookOf(final String id) {
        return new OpenedBook(
                id,
                ProjectFixtures.source(ProjectFixtures.BOOK_FILE),
                BookFixtures.frankensteinImport().inspection(),
                null,
                BookBrief.defaults("en"));
    }
}
