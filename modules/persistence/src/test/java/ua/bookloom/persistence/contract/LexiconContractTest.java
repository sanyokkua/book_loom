package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/**
 * The {@link LexiconRepository} contract, continuing {@link CheckpointContractTest} in its own file by the project's
 * class-length limit: keys ignore case, counts survive concurrent records, and a removed term is remembered.
 */
public abstract class LexiconContractTest extends CheckpointContractTest {

    protected abstract LexiconRepository lexiconRepository();

    private List<LexiconEntry> held() {
        return Objects.requireNonNull(lexiconRepository().all("p1").data());
    }

    @Test
    void lexiconAll_unknownProject_isEmpty() {
        assertThat(lexiconRepository().all("nobody").data()).isEmpty();
    }

    @Test
    void lexiconAll_threeTerms_areListedByTheirKey() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "warden"));
        lexiconRepository().put(LexiconEntry.of("p1", "Master"));
        lexiconRepository().put(LexiconEntry.of("p1", "imp"));

        assertThat(held()).extracting(LexiconEntry::term).containsExactly("imp", "Master", "warden");
    }

    @Test
    void lexiconPut_sameTermInAnotherCase_replacesTheEntry() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "Master"));
        lexiconRepository().put(LexiconEntry.of("p1", "master").withChosen("господар"));

        assertThat(held()).hasSize(1);
        assertThat(held().getFirst().chosen()).isEqualTo("господар");
    }

    @Test
    void lexiconRecord_unknownTerm_addsItWithTheRenderingCountedOnce() {
        givenProjects("p1");
        final LexiconEntry stored = Objects.requireNonNull(
                lexiconRepository().record("p1", "imp", "біс").data());

        assertThat(stored.renderings()).containsExactly(new LexiconEntry.Rendering("біс", 1));
        assertThat(held()).containsExactly(stored);
    }

    @Test
    void lexiconRecord_manyThreads_losesNoCount() {
        givenProjects("p1");
        final int uses = 200;
        IntStream.range(0, uses).parallel().forEach(i -> lexiconRepository().record("p1", "master", "господар"));

        assertThat(held().getFirst().renderings()).containsExactly(new LexiconEntry.Rendering("господар", uses));
    }

    @Test
    void lexiconRemove_heldTerm_isGoneAndASecondRemoveSaysSo() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));

        assertThat(lexiconRepository().remove("p1", "MASTER").data()).isTrue();
        assertThat(lexiconRepository().remove("p1", "master").data()).isFalse();
        assertThat(held()).isEmpty();
    }

    @Test
    void lexiconUpdate_heldTerm_appliesTheChangeToTheEntryAsItStands() {
        givenProjects("p1");
        lexiconRepository().record("p1", "master", "господар");

        final LexiconEntry stored = Objects.requireNonNull(lexiconRepository()
                        .update("p1", "MASTER", entry -> entry.withChosen("пан"))
                        .data())
                .orElseThrow();

        assertThat(stored.chosen()).isEqualTo("пан");
        assertThat(stored.renderings()).containsExactly(new LexiconEntry.Rendering("господар", 1));
        assertThat(held().getFirst().chosen()).isEqualTo("пан");
    }

    @Test
    void lexiconUpdate_termNotHeld_createsNothing() {
        givenProjects("p1");
        assertThat(lexiconRepository()
                        .update("p1", "master", entry -> entry.withChosen("пан"))
                        .data())
                .isEmpty();
        assertThat(held()).isEmpty();
    }

    @Test
    void lexiconUpdate_alongsideManyRecords_losesNoCount() {
        givenProjects("p1");
        lexiconRepository().record("p1", "master", "господар");
        final int uses = 200;
        IntStream.range(0, uses).parallel().forEach(i -> {
            lexiconRepository().record("p1", "master", "господар");
            lexiconRepository().update("p1", "master", entry -> entry.withSuggested("пан"));
        });

        assertThat(held().getFirst().renderings()).containsExactly(new LexiconEntry.Rendering("господар", uses + 1));
    }

    @Test
    void lexiconWasRemoved_termNeverHeld_isFalse() {
        assertThat(lexiconRepository().wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void lexiconWasRemoved_afterRemoveInAnotherCase_isTrue() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));

        lexiconRepository().remove("p1", "master");

        assertThat(lexiconRepository().wasRemoved("p1", "MASTER").data()).isTrue();
    }

    @Test
    void lexiconWasRemoved_removeOfATermNotHeld_isStillFalse() {
        givenProjects("p1");
        lexiconRepository().remove("p1", "master");

        assertThat(lexiconRepository().wasRemoved("p1", "master").data()).isFalse();
    }

    // IF a term stored again stayed in the memory, THEN a person who typed it back would see it refused by the scan.
    @Test
    void lexiconWasRemoved_afterTheTermIsPutAgain_isFalse() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));
        lexiconRepository().remove("p1", "master");

        lexiconRepository().put(LexiconEntry.of("p1", "master"));

        assertThat(lexiconRepository().wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void lexiconWasRemoved_afterARecordedUseOfTheTerm_isFalse() {
        givenProjects("p1");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));
        lexiconRepository().remove("p1", "master");

        lexiconRepository().record("p1", "master", "господар");

        assertThat(lexiconRepository().wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void lexiconWasRemoved_otherProject_isFalse() {
        givenProjects("p1", "p2");
        lexiconRepository().put(LexiconEntry.of("p1", "master"));
        lexiconRepository().remove("p1", "master");

        assertThat(lexiconRepository().wasRemoved("p2", "master").data()).isFalse();
    }
}
