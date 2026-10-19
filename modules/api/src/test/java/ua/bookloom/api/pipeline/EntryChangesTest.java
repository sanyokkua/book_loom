package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EntryChangesTest {

    @Test
    void between_sameLists_isEmpty() {
        final EntryChanges<String> changes =
                EntryChanges.between(List.of("a1", "b1"), List.of("a1", "b1"), s -> s.substring(0, 1), s -> null);

        assertThat(changes.isEmpty()).isTrue();
        assertThat(changes.size()).isZero();
    }

    @Test
    void between_oneAddedOneRemovedOneChanged_listsEachWithItsReason() {
        final EntryChanges<String> changes = EntryChanges.between(
                List.of("a1", "b1", "c1"), List.of("a2", "c1", "d1"), s -> s.substring(0, 1), s -> "why " + s);

        assertThat(changes.added()).containsExactly("d1");
        assertThat(changes.removed()).containsExactly(new EntryChanges.Removal<>("b1", "why b1"));
        assertThat(changes.changed()).containsExactly(new EntryChanges.Change<>("a1", "a2", "why a1"));
        assertThat(changes.size()).isEqualTo(3);
    }

    @Test
    void none_hasNothing() {
        assertThat(EntryChanges.<String>none().isEmpty()).isTrue();
    }
}
