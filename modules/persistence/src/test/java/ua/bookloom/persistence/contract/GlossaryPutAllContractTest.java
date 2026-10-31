package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.MeaningChoice;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.PlaceKind;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;

/**
 * {@link GlossaryRepository#putAll}, the many-entry store a CSV import writes through, in its own file by the project's
 * class-length limit.
 */
public abstract class GlossaryPutAllContractTest extends ProjectFindersContractTest {

    protected abstract GlossaryRepository glossaryRepository();

    @Test
    void putAll_heldAndNewEntries_replacesInPlaceAndAppendsInOrder() {
        givenProjects("p1");
        final GlossaryRepository repository = glossaryRepository();
        repository.add(entry("e1", "Hale", "Гейл"));
        repository.add(entry("e2", "Mira", "Міра"));
        repository.remove("p1", "e2");

        final Result<List<GlossaryEntry>> put =
                repository.putAll("p1", List.of(entry("e3", "Mira", "Мира"), entry("e1", "Hale", "Хейл")));

        assertThat(put.isOk()).as("%s", put.error()).isTrue();
        assertThat(repository.all("p1").data())
                .extracting(GlossaryEntry::id, GlossaryEntry::target)
                .containsExactly(tuple("e1", "Хейл"), tuple("e3", "Мира"));
        assertThat(repository.wasRemoved("p1", "Mira").data()).isFalse();
    }

    @Test
    void putAll_termHeldUnderAnotherId_answersValidation() {
        givenProjects("p1");
        final GlossaryRepository repository = glossaryRepository();
        repository.add(entry("e1", "Hale", "Гейл"));

        final Result<List<GlossaryEntry>> put = repository.putAll("p1", List.of(entry("e9", "HALE", "Хейл")));

        assertThat(put.isErr()).isTrue();
        assertThat(put.error().code()).isEqualTo(ErrorCode.validation);
        assertThat(repository.all("p1").data())
                .extracting(GlossaryEntry::target)
                .containsExactly("Гейл");
    }

    // Proves the lemma entry's forms, origins, target state and place kind survive the store in both adapters.
    @Test
    void put_entryWithFormsOriginAndState_roundTrips() {
        givenProjects("p1");
        final GlossaryRepository repository = glossaryRepository();
        final GlossaryEntry lemma = lemma();

        final Result<List<GlossaryEntry>> put = repository.putAll("p1", List.of(lemma));

        assertThat(put.isOk()).as("%s", put.error()).isTrue();
        assertThat(repository.all("p1").data()).containsExactly(lemma);
        final GlossaryEntry stored =
                Objects.requireNonNull(repository.all("p1").data()).getFirst();
        assertThat(stored.forms()).containsExactly("Велемира", "Велемиру");
        assertThat(stored.targetOrigin()).isEqualTo(Origin.BOOK);
        assertThat(stored.placeKind()).isEqualTo(PlaceKind.RIVER);
        assertThat(stored.meaning()).isEqualTo("a note");
        assertThat(stored.meaningChoice()).isEqualTo(MeaningChoice.PENDING);
    }

    private static GlossaryEntry lemma() {
        return new GlossaryEntry(
                "e1",
                "p1",
                "Велемир",
                List.of("Велемира", "Велемиру"),
                true,
                "Велемир",
                TargetState.PINNED,
                Origin.BOOK,
                TermType.PLACE,
                PlaceKind.RIVER,
                Origin.RULE,
                Gender.MALE,
                Origin.LIKELY,
                true,
                false,
                "a note",
                MeaningChoice.PENDING,
                null);
    }

    private static GlossaryEntry entry(final String id, final String term, final String target) {
        return GlossaryEntry.of(id, "p1", term, target, TermType.CHARACTER, Gender.UNKNOWN, false);
    }
}
