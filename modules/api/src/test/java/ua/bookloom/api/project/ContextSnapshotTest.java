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
        glossary.add(new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, true, false, null));
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of("Previous target."), glossary, List.of(), "Hale arrived in Milton.", "style");

        glossary.add(new SnapshotTerm("Milton", "Мілтон", TermType.PLACE, Gender.NEUTER, false, false, null));

        assertThat(snapshot.glossary()).hasSize(1);
        assertThatThrownBy(() -> snapshot.glossary().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_termAndSummary_areKept() {
        final SnapshotTerm term = new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, true, false, null);
        final ContextSnapshot snapshot =
                new ContextSnapshot(List.of(), List.of(term), List.of(), "Hale arrived in Milton.", "style");

        assertThat(snapshot.glossary()).containsExactly(term);
        assertThat(snapshot.summary()).isEqualTo("Hale arrived in Milton.");
    }

    @Test
    void constructor_delegatingForms_leaveSentSectionsNull() {
        final ContextSnapshot snapshot = new ContextSnapshot(List.of(), List.of(), List.of(), null, "style");

        assertThat(snapshot.sentSections()).isNull();
    }

    @Test
    void constructor_sentSections_areKeptAndCopied() {
        final List<ua.bookloom.api.pipeline.PromptSection> sections = new ArrayList<>();
        sections.add(new ua.bookloom.api.pipeline.PromptSection(
                "items", "Items:", ua.bookloom.api.pipeline.PromptSection.Origin.USER, List.of("a")));
        final ContextSnapshot snapshot =
                new ContextSnapshot(List.of(), List.of(), List.of(), null, "style", List.of(), List.of(), sections);

        sections.clear();

        assertThat(snapshot.sentSections()).hasSize(1);
    }
}
