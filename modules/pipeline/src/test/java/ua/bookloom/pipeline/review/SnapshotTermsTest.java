package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.TermType;

/** A retry rebuilds the glossary its first draft saw, with every gender still marked as who decided it. */
class SnapshotTermsTest {

    private static ContextSnapshot snapshotOf(final SnapshotTerm... terms) {
        return new ContextSnapshot(List.of(), List.of(terms), List.of(), null, "Neutral register.");
    }

    // IF the rebuilt entry lost the origin, THEN a retry would turn a suggested gender into the person's and enforce
    // it (15i.T1).
    @Test
    void entriesOf_snapshotWithEveryOrigin_keepsEachOne() {
        final ContextSnapshot snapshot = snapshotOf(
                new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, false, false, Origin.LIKELY),
                new SnapshotTerm("Lyra", "Ліра", TermType.CHARACTER, Gender.FEMALE, false, false, Origin.BOOK),
                new SnapshotTerm("Sam", "Сем", TermType.CHARACTER, Gender.MALE, false, false, Origin.USER),
                new SnapshotTerm("Quill", "Квіл", TermType.CHARACTER, Gender.UNKNOWN, false, false, null));

        final List<GlossaryEntry> entries = SnapshotTerms.entriesOf(snapshot, "p1");

        assertThat(entries)
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Hale", Gender.MALE, Origin.LIKELY),
                        tuple("Lyra", Gender.FEMALE, Origin.BOOK),
                        tuple("Sam", Gender.MALE, Origin.USER),
                        tuple("Quill", Gender.UNKNOWN, null));
    }
}
