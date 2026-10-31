package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.ChunkCommit;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/**
 * A glossary term matches an entry by the key of its lemma or of any form it lists, in both adapters: lookups, the
 * duplicate rule, the removal memory and a chunk's additions. The id stays the lemma's. The names are invented.
 */
public abstract class GlossaryIndexTest extends LexiconContractTest {

    @Test
    void findByKey_attestedForm_returnsTheLemmaEntry() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(velemir());

        assertThat(glossary.findByTerm("p1", "велемира").data())
                .isPresent()
                .hasValueSatisfying(entry -> assertThat(entry.id()).isEqualTo("p1:велемир"));
        assertThat(glossary.update("p1", "Велемиру", entry -> entry.withTarget("Велемир"))
                        .data())
                .isPresent()
                .hasValueSatisfying(entry -> assertThat(entry.term()).isEqualTo("Велемир"));
    }

    @Test
    void add_termHeldAsAForm_isRefusedAsDuplicate() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(velemir());

        final Result<GlossaryEntry> added = glossary.add(entry("p1:велемира", "Велемира"));

        assertThat(added.isErr()).isTrue();
        assertThat(Objects.requireNonNull(added.error()).code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void putAll_termHeldAsAnotherEntrysForm_answersValidation() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(velemir());

        final Result<List<GlossaryEntry>> put = glossary.putAll("p1", List.of(entry("p1:велемира", "Велемира")));

        assertThat(put.isErr()).isTrue();
        assertThat(glossary.all("p1").data()).extracting(GlossaryEntry::term).containsExactly("Велемир");
    }

    @Test
    void remove_lemma_remembersItsFormsAsRemoved() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(velemir());

        glossary.remove("p1", "p1:велемир");

        assertThat(glossary.wasRemoved("p1", "Велемиру").data()).isTrue();
    }

    @Test
    void commit_additionNamingAHeldForm_isSkipped() {
        givenProjects("p1");
        final GlossaryRepository glossary = glossaryRepository();
        glossary.add(velemir());
        final ChunkCommit commit = new ChunkCommit(
                "p1",
                List.of(),
                List.of(),
                List.of(
                        entry("p1:велемира", "Велемира"),
                        entry("p1:градимира", "Градимира").withForms(List.of("Велемиру"))),
                List.of());

        final Result<Integer> applied = checkpointPort().commit(commit);

        assertThat(applied.data()).isZero();
        assertThat(glossary.all("p1").data()).extracting(GlossaryEntry::term).containsExactly("Велемир");
    }

    private static GlossaryEntry velemir() {
        return entry("p1:велемир", "Велемир").withForms(List.of("Велемира", "Велемиру"));
    }

    private static GlossaryEntry entry(final String id, final String term) {
        return GlossaryEntry.of(id, "p1", term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }
}
