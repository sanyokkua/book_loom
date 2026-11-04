package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.ScriptedProjectService;

/** A saved brief change that left decided segments to look at is remembered for the Book Brief's banner. */
class BookBriefViewModelRecheckTest extends BookBriefViewModelTestBase {

    private void saveAnsweredWith(final int rechecked) {
        saveAnsweredWith(rechecked, "Gothic novel");
    }

    private void saveAnsweredWith(final int rechecked, final String genre) {
        projects.onUpdateBrief(
                Result.ok(new ProjectChange.Saved(ScriptedProjectService.storedProjectOf(BOOK), rechecked)));
        change(() -> brief.setGenre(genre));
        runSaves();
    }

    private int rechecked() {
        return onFx(() -> brief.rechecked().get());
    }

    @Test
    void rechecked_saveAnsweredWithTwelve_isTwelve() {
        openBook(BookFixtures.frankensteinImport());

        saveAnsweredWith(12);

        assertThat(rechecked()).isEqualTo(12);
    }

    @Test
    void rechecked_saveAnsweredWithNone_staysZero() {
        openBook(BookFixtures.frankensteinImport());

        saveAnsweredWith(0);

        assertThat(rechecked()).isZero();
    }

    // IF a save that re-checked nothing kept the earlier count, THEN reverting the key would leave the banner up.
    @Test
    void rechecked_laterSaveAnsweredWithNone_isZero() {
        openBook(BookFixtures.frankensteinImport());
        saveAnsweredWith(12);

        saveAnsweredWith(0, "Horror");

        assertThat(rechecked()).isZero();
    }

    // IF the count outlived the book, THEN another book would open with a banner about segments it never had.
    @Test
    void rechecked_projectCleared_resetsToZero() {
        openBook(BookFixtures.frankensteinImport());
        saveAnsweredWith(12);

        onFx(() -> {
            current.clear();
            return null;
        });

        assertThat(rechecked()).isZero();
    }
}
