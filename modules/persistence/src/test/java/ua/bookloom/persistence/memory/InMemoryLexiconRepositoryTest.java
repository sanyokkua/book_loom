package ua.bookloom.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/** The lexicon port over the in-memory adapter: keys ignore case, counts survive concurrent records. */
class InMemoryLexiconRepositoryTest {

    private final LexiconRepository repository = new InMemoryLexiconRepository(new InMemoryStore());

    private List<LexiconEntry> held() {
        return Objects.requireNonNull(repository.all("p1").data());
    }

    @Test
    void all_unknownProject_isEmpty() {
        assertThat(repository.all("nobody").data()).isEmpty();
    }

    @Test
    void put_sameTermInAnotherCase_replacesTheEntry() {
        repository.put(LexiconEntry.of("p1", "Master"));
        repository.put(LexiconEntry.of("p1", "master").withChosen("господар"));

        assertThat(held()).hasSize(1);
        assertThat(held().getFirst().chosen()).isEqualTo("господар");
    }

    @Test
    void record_unknownTerm_addsItWithTheRenderingCountedOnce() {
        final LexiconEntry stored =
                Objects.requireNonNull(repository.record("p1", "imp", "біс").data());

        assertThat(stored.renderings()).containsExactly(new LexiconEntry.Rendering("біс", 1));
    }

    @Test
    void record_manyThreads_losesNoCount() {
        final int uses = 200;
        IntStream.range(0, uses).parallel().forEach(i -> repository.record("p1", "master", "господар"));

        assertThat(held().getFirst().renderings()).containsExactly(new LexiconEntry.Rendering("господар", uses));
    }

    @Test
    void remove_heldTerm_isGoneAndASecondRemoveSaysSo() {
        repository.put(LexiconEntry.of("p1", "master"));

        assertThat(repository.remove("p1", "MASTER").data()).isTrue();
        assertThat(repository.remove("p1", "master").data()).isFalse();
        assertThat(held()).isEmpty();
    }

    @Test
    void update_heldTerm_appliesTheChangeToTheEntryAsItStands() {
        repository.record("p1", "master", "господар");

        final LexiconEntry stored = Objects.requireNonNull(repository
                        .update("p1", "MASTER", entry -> entry.withChosen("пан"))
                        .data())
                .orElseThrow();

        assertThat(stored.chosen()).isEqualTo("пан");
        assertThat(stored.renderings()).containsExactly(new LexiconEntry.Rendering("господар", 1));
    }

    @Test
    void update_removedTerm_createsNothing() {
        assertThat(repository
                        .update("p1", "master", entry -> entry.withChosen("пан"))
                        .data())
                .isEmpty();
        assertThat(held()).isEmpty();
    }

    @Test
    void update_alongsideManyRecords_losesNoCount() {
        repository.record("p1", "master", "господар");
        final int uses = 200;
        IntStream.range(0, uses).parallel().forEach(i -> {
            repository.record("p1", "master", "господар");
            repository.update("p1", "master", entry -> entry.withSuggested("пан"));
        });

        assertThat(held().getFirst().renderings()).containsExactly(new LexiconEntry.Rendering("господар", uses + 1));
    }
}
