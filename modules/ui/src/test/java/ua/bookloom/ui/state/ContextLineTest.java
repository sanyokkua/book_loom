package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.SnapshotRendering;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.TermType;

class ContextLineTest {

    private static SnapshotTerm term(final String name) {
        return new SnapshotTerm(name, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    @Test
    void of_styleSheetTwoNamesPrecedingTargetAndSummary_listsEveryPartInOrder() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of("Раніше."), List.of(term("Gale"), term("Anna")), List.of(), "Sum.", "Formal");

        assertThat(ContextLine.of(snapshot)).isEqualTo("brief · glossary(2) · previous paragraph · summary");
    }

    @Test
    void of_onlyAStyleSheet_readsBrief() {
        final ContextSnapshot snapshot = new ContextSnapshot(List.of(), List.of(), List.of(), null, "Formal");

        assertThat(ContextLine.of(snapshot)).isEqualTo("brief");
    }

    @Test
    void of_twoMemoryHits_addsMemoryWithTheCount() {
        final SnapshotTmHit hit = new SnapshotTmHit(SnapshotTmHit.TmHitKind.EXACT, "a", "б");
        final ContextSnapshot snapshot = new ContextSnapshot(List.of(), List.of(), List.of(hit, hit), null, "Formal");

        assertThat(ContextLine.of(snapshot)).isEqualTo("brief · memory(2)");
    }

    @Test
    void of_blankStyleSheetAndNothingElse_isEmpty() {
        final ContextSnapshot snapshot = new ContextSnapshot(List.of(), List.of(), List.of(), null, "  ");

        assertThat(ContextLine.of(snapshot)).isEmpty();
    }

    @Test
    void of_establishedRecurringRenderings_addsRecurringWithTheCount() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                List.of(),
                List.of(),
                List.of(),
                null,
                "Formal",
                List.of(new SnapshotRendering("master", "господар"), new SnapshotRendering("imp", "біс")));

        assertThat(ContextLine.of(snapshot)).isEqualTo("brief · recurring(2)");
    }
}
