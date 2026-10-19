package ua.bookloom.persistence.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;

/**
 * The lexicon remembers, for the session, a term that was removed (15i.N8), as the glossary does, so a scan that
 * proposes the term again does not silently add back what a person (or a model decision) removed.
 */
class InMemoryLexiconRemovalTest {

    private final LexiconRepository repository = new InMemoryLexiconRepository(new InMemoryStore());

    @Test
    void wasRemoved_termNeverHeld_isFalse() {
        assertThat(repository.wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void wasRemoved_afterRemoveInAnotherCase_isTrue() {
        repository.put(LexiconEntry.of("p1", "master"));

        repository.remove("p1", "master");

        assertThat(repository.wasRemoved("p1", "MASTER").data()).isTrue();
    }

    @Test
    void wasRemoved_removeOfATermNotHeld_isStillFalse() {
        repository.remove("p1", "master");

        assertThat(repository.wasRemoved("p1", "master").data()).isFalse();
    }

    // IF a term stored again stayed in the memory, THEN a person who typed it back would see it refused by the scan.
    @Test
    void wasRemoved_afterTheTermIsPutAgain_isFalse() {
        repository.put(LexiconEntry.of("p1", "master"));
        repository.remove("p1", "master");

        repository.put(LexiconEntry.of("p1", "master"));

        assertThat(repository.wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void wasRemoved_afterARecordedUseOfTheTerm_isFalse() {
        repository.put(LexiconEntry.of("p1", "master"));
        repository.remove("p1", "master");

        repository.record("p1", "master", "господар");

        assertThat(repository.wasRemoved("p1", "master").data()).isFalse();
    }

    @Test
    void wasRemoved_otherProject_isFalse() {
        repository.put(LexiconEntry.of("p1", "master"));
        repository.remove("p1", "master");

        assertThat(repository.wasRemoved("p2", "master").data()).isFalse();
    }
}
