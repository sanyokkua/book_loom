package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.BookFixtures;

/** Auto as a source language the person can choose over a declared one: no tag, the model identifies it (15i.U4). */
class BookBriefViewModelAutoSourceTest extends BookBriefViewModelTestBase {

    // IF choosing Auto over a declared English left English in the brief, THEN the run would ignore the person.
    @Test
    void setSourceAuto_bookDeclaringEnglish_clearsTheSourceAndKeepsTheTarget() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setTargetLanguage("uk"));

        change(() -> brief.setSourceAuto());

        assertThat(current().sourceLanguage()).isNull();
        assertThat(current().targetLanguage()).isEqualTo("uk");
        assertThat(sourceUndeclared()).isTrue();
        assertThat(canContinue()).isTrue();
    }

    // IF Auto were not saved, THEN the project would hold English again after the next import of the brief.
    @Test
    void setSourceAuto_bookDeclaringEnglish_savesABriefWithNoSource() {
        openBook(BookFixtures.frankensteinImport());

        change(() -> brief.setSourceAuto());
        runSaves();

        assertThat(projects.briefs()).hasSize(1);
        assertThat(projects.briefs().getFirst().sourceLanguage()).isNull();
    }

    // IF choosing what the brief already holds saved it again, THEN every visit to the screen would write the project.
    @Test
    void setSourceAuto_sourceAlreadyAuto_savesNothing() {
        openBook(BookFixtures.declaringNoLanguage("notes", BookFormat.TXT, 1));

        change(() -> brief.setSourceAuto());
        runSaves();

        assertThat(projects.briefs()).isEmpty();
        assertThat(current().sourceLanguage()).isNull();
    }

    // IF a language chosen after Auto were refused, THEN the person could not go back to naming the source.
    @Test
    void setSourceLanguage_afterAuto_leavesAutoAndReportsTheSourceDeclared() {
        openBook(BookFixtures.declaringNoLanguage("notes", BookFormat.TXT, 1));
        assertThat(sourceUndeclared()).isTrue();

        change(() -> brief.setSourceLanguage("fr"));

        assertThat(current().sourceLanguage()).isEqualTo("fr");
        assertThat(sourceUndeclared()).isFalse();
    }

    // IF Auto and the same tag as target were allowed to match, THEN a book would be "translated" into itself; Auto
    // has no tag, so only a chosen source can equal the target.
    @Test
    void canContinue_autoSourceAndAnyTarget_isTrue() {
        openBook(BookFixtures.frankensteinImport());
        change(() -> brief.setSourceAuto());
        assertThat(canContinue()).isFalse();

        change(() -> brief.setTargetLanguage("en"));

        assertThat(sameLanguage()).isFalse();
        assertThat(canContinue()).isTrue();
    }
}
