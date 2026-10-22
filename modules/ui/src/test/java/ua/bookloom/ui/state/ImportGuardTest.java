package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.dialog.RecordingOpenAnotherBookPrompt.Asked;

/**
 * Opening another book while a project is open asks "Open another book?" first and changes nothing of the open project
 * until the person chooses; no run is going here, so the answers only decide whether the first project is kept.
 */
class ImportGuardTest extends ImportGuardTestBase {

    @BeforeEach
    void openFirst() {
        openFirstBook();
    }

    // IF the book opened before the question was answered, THEN a stray drop would already have replaced the project.
    @Test
    void requestImport_projectOpen_asksOnceAndChangesNothing() {
        requestImport(DRACULA);

        assertThat(prompt.asked()).containsExactly(new Asked(OPEN_NAME, "Dracula.epub", RunState.IDLE));
        assertThat(projects.events()).containsExactly("import Frankenstein.epub");
        assertThat(projects.removedProjects()).isEmpty();
    }

    @Test
    void cancel_questionLeftUnanswered_changesNothingAndKeepsTheOpenBook() {
        requestImport(DRACULA);

        assertThat(projects.imports()).containsExactly(FRANKENSTEIN);
        assertThat(shown()).isInstanceOf(ImportState.Detected.class);
        assertThat(projects.closedProjects()).isEmpty();
    }

    // IF "Open as a new project" deleted the first project, THEN the safe primary choice would not be safe.
    @Test
    void openNew_answered_opensTheBookOnceAndNeverRemovesTheOpenProject() {
        requestImport(DRACULA);

        openNew();

        assertThat(projects.imports()).containsExactly(FRANKENSTEIN, DRACULA);
        assertThat(projects.removedProjects()).isEmpty();
        assertThat(shown())
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.card().fileName()).isEqualTo("Dracula.epub"));
    }

    @Test
    void replace_answered_removesTheOpenProjectThenOpensTheBook() {
        requestImport(DRACULA);

        replace();

        assertThat(projects.removedProjects()).containsExactly("p1");
        assertThat(projects.events()).containsSubsequence("remove p1", "import Dracula.epub");
        assertThat(shown())
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.card().fileName()).isEqualTo("Dracula.epub"));
    }

    // IF the answer were remembered, THEN the next drop would skip the question.
    @Test
    void requestImport_askedTwice_asksTwice() {
        requestImport(DRACULA);
        openNew();

        requestImport(FRANKENSTEIN);

        assertThat(prompt.asked()).hasSize(2);
    }
}
