package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.memory.ProtectedMask;

/** A window names a lemma entry by its lemma or by any form the book writes; the names are invented. */
class InjectedTermsTest {

    @Test
    void window_namingOnlyAnInflectedForm_carriesTheEntry() {
        final GlossaryEntry branets = GlossaryEntry.of(
                        "p1:бранец", "p1", "Бранец", "Бранець", TermType.CHARACTER, Gender.MALE, false)
                .withForms(List.of("Бранца"));
        final Segment window = ContextFixtures.segment(0, "Мы видели Бранца вчера.");

        assertThat(InjectedTerms.select(List.of(window), ProtectedMask.none(window.masked()), List.of(branets)))
                .extracting(injected -> injected.term().term())
                .containsExactly("Бранец");
    }

    @Test
    void window_namingNoFormOfTheEntry_leavesItOut() {
        final GlossaryEntry branets = GlossaryEntry.of(
                        "p1:бранец", "p1", "Бранец", "Бранець", TermType.CHARACTER, Gender.MALE, false)
                .withForms(List.of("Бранца"));
        final Segment window = ContextFixtures.segment(0, "Мы видели Тарна вчера.");

        assertThat(InjectedTerms.select(List.of(window), ProtectedMask.none(window.masked()), List.of(branets)))
                .isEmpty();
    }
}
