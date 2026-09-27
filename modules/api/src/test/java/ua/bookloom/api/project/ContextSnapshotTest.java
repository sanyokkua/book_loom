package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@code ContextSnapshot}'s defensive list copy and its held texts.
 */
class ContextSnapshotTest {

    @Test
    void constructor_glossaryListMutatedAfterConstruction_doesNotChangeSnapshot() {
        final List<SnapshotTerm> glossary = new ArrayList<>();
        glossary.add(new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, true));
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of("Previous target."), glossary, List.of(), "Hale arrived in Milton.", "style");

        glossary.add(new SnapshotTerm("Milton", "Мілтон", TermType.PLACE, Gender.NEUTER, false));

        assertThat(snapshot.glossary()).hasSize(1);
        assertThatThrownBy(() -> snapshot.glossary().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_termAndSummary_areKept() {
        final SnapshotTerm term = new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, true);
        final ContextSnapshot snapshot =
                new ContextSnapshot(List.of(), List.of(term), List.of(), "Hale arrived in Milton.", "style");

        assertThat(snapshot.glossary()).containsExactly(term);
        assertThat(snapshot.summary()).isEqualTo("Hale arrived in Milton.");
    }
}
