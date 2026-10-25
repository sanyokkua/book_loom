package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NarratorPerson;

/**
 * Follows the open book's narrator hint for the Book Brief: whether the notice that the source is told in the first
 * person is shown, and the one change the hint makes on its own, preselecting "first person" while the narrator is still
 * unspecified. The gender is never chosen here. FX thread only.
 */
@Slf4j
final class NarratorNotice {

    private final CurrentProject project;
    private final Consumer<NarratorPerson> setPerson;
    private final ReadOnlyBooleanWrapper shown = new ReadOnlyBooleanWrapper(false);

    NarratorNotice(final CurrentProject project, final Consumer<NarratorPerson> setPerson) {
        this.project = Objects.requireNonNull(project, "project");
        this.setPerson = Objects.requireNonNull(setPerson, "setPerson");
        project.book().addListener((observed, was, now) -> onHint());
        project.brief().addListener((observed, was, now) -> derive(now));
        // A book that declares no language is judged only at Start, once its source is chosen.
        project.narratorHint().addListener((observed, was, now) -> onHint());
        onHint();
    }

    ReadOnlyBooleanProperty shown() {
        return shown.getReadOnlyProperty();
    }

    private void onHint() {
        if (NarratorHints.preselectsFirstPerson(
                project.narratorHint().get(), project.brief().get())) {
            log.debug("narrator preselected as first person from the detected narration");
            setPerson.accept(NarratorPerson.FIRST);
        }
        derive(project.brief().get());
    }

    private void derive(final @Nullable BookBrief brief) {
        final boolean now = NarratorHints.needsGender(project.narratorHint().get(), brief);
        shown.set(now);
        log.debug("narrator notice shown: {}", now);
    }
}
