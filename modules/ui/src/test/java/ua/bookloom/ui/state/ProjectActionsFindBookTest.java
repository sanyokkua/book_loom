package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookRelocation;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.ScriptedProjectEdits.RelocateCall;

/** What the Find-the-book row action asks the chooser, the service and the row opener. */
class ProjectActionsFindBookTest extends ProjectActionsTestBase {

    private void findBookOf(final ProjectOpening opening) {
        onFx(() -> actions().findBook(rowOf(UK), opening));
    }

    @Test
    void findBook_asked_showsTheCardForTheRowAndTheReason() {
        final ProjectOpening opening = new ProjectOpening.BookFileChanged(UK);

        findBookOf(opening);

        assertThat(findBook.asked()).hasSize(1);
        assertThat(findBook.asked().getFirst().row().summary().project().id()).isEqualTo("p-uk");
        assertThat(findBook.asked().getFirst().opening()).isEqualTo(opening);
        assertThat(findBook.asked().getFirst().error()).isNull();
    }

    // IF a file with another content were accepted, THEN the stored segments would no longer match the book.
    @Test
    void findBook_chosenFileIsADifferentBook_asksAgainWithTheErrorAndOpensNothing() throws Exception {
        service.edits().onRelocate(Result.ok(new BookRelocation.DifferentBook(UK)));
        chooser().answer(FOUND);
        findBookOf(new ProjectOpening.BookFileMissing(UK));

        onFx(findBook::choose);
        settle();

        assertThat(service.edits().relocations()).containsExactly(new RelocateCall("p-uk", FOUND));
        assertThat(findBook.asked()).hasSize(2);
        assertThat(findBook.asked().get(1).error()).isEqualTo("This is a different book");
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void findBook_chosenFileIsTheSameBook_opensTheRow() throws Exception {
        service.edits().onRelocate(Result.ok(new BookRelocation.Relocated(List.of(UK, DE))));
        chooser().answer(FOUND);
        findBookOf(new ProjectOpening.BookFileMissing(UK));

        onFx(findBook::choose);
        settle();

        assertThat(service.edits().relocations()).containsExactly(new RelocateCall("p-uk", FOUND));
        assertThat(opener.opened()).hasSize(1);
        assertThat(opener.opened().getFirst().summary().project().id()).isEqualTo("p-uk");
    }

    @Test
    void findBook_fileDialogCancelled_asksTheServiceNothingAndOpensNothing() throws Exception {
        chooser().answer(null);
        findBookOf(new ProjectOpening.BookFileMissing(UK));

        onFx(findBook::choose);
        settle();

        assertThat(service.edits().relocations()).isEmpty();
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void findBook_cardCancelled_leavesTheProjectClosed() throws Exception {
        findBookOf(new ProjectOpening.BookFileMissing(UK));

        onFx(findBook::cancel);
        settle();

        assertThat(service.edits().relocations()).isEmpty();
        assertThat(opener.opened()).isEmpty();
    }

    @Test
    void findBook_serviceFails_showsTheErrorAndOpensNothing() throws Exception {
        final AppError failure = AppError.of(ErrorCode.validation, "Unknown project", "No such project is stored.");
        service.edits().onRelocate(Result.err(failure));
        chooser().answer(FOUND);
        findBookOf(new ProjectOpening.BookFileMissing(UK));

        onFx(findBook::choose);
        settle();

        assertThat(errors.presented()).containsExactly(failure);
        assertThat(opener.opened()).isEmpty();
    }
}
