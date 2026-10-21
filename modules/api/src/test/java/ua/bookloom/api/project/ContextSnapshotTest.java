package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.PromptSection;

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

    private static ContextSnapshot full() {
        return new ContextSnapshot(
                List.of("Previous target."),
                List.of(new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, true, false, null)),
                List.of(),
                "Hale arrived in Milton.",
                "style",
                List.of(),
                List.of("Hale: male"),
                null);
    }

    // IF sections are put on a snapshot, THEN only the sent sections change and the list is copied.
    @Test
    void withSent_sections_replacesOnlySentSectionsAndCopies() {
        final List<PromptSection> sections = new ArrayList<>();
        sections.add(new PromptSection("items", "Items:", PromptSection.Origin.USER, List.of("a")));
        final ContextSnapshot base = full();

        final ContextSnapshot sent = base.withSent(sections);
        sections.clear();

        assertThat(sent.sentSections()).hasSize(1);
        assertThat(sent)
                .usingRecursiveComparison()
                .ignoringFields("sentSections")
                .isEqualTo(base);
        assertThat(base.sentSections()).isNull();
    }

    // IF null is put on a snapshot that held sections, THEN it holds none again.
    @Test
    void withSent_null_clearsThem() {
        final ContextSnapshot sent =
                full().withSent(List.of(new PromptSection("items", "Items:", PromptSection.Origin.USER, List.of("a"))));

        assertThat(sent.withSent(null).sentSections()).isNull();
    }
}
