package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.NameCandidate;

/** A scan that meets a held term in a form the entry lacks adds the form; the rendering and the term stay. */
class LexiconFormsTest {

    private final LexiconRepository lexicon =
            Guice.createInjector(new InMemoryPersistenceModule()).getInstance(LexiconRepository.class);

    @Test
    void merge_heldTermMetInNewForms_gainsThemAndKeepsItsRendering() {
        lexicon.put(new LexiconEntry("p1", "фонарь", List.of(), null, null, null, null, List.of("фонаря"))
                .withChosen("ліхтар"));
        final NameCandidate group = new NameCandidate("фонарь", 9, "Он зажёг фонарь.", List.of("фонаря", "фонарём"));

        final var changed = LexiconForms.merge(lexicon, "p1", List.of(group));

        assertThat(changed.data()).isEqualTo(1);
        assertThat(lexicon.all("p1").data()).singleElement().satisfies(entry -> {
            assertThat(entry.forms()).containsExactly("фонаря", "фонарём");
            assertThat(entry.chosen()).isEqualTo("ліхтар");
        });
    }

    @Test
    void merge_groupOfNoHeldTerm_changesNothing() {
        lexicon.put(LexiconEntry.of("p1", "стража"));
        final NameCandidate group = new NameCandidate("фонарь", 9, "Он зажёг фонарь.", List.of("фонаря"));

        assertThat(LexiconForms.merge(lexicon, "p1", List.of(group)).data()).isEqualTo(0);
        assertThat(lexicon.all("p1").data())
                .singleElement()
                .satisfies(entry -> assertThat(entry.forms()).isEmpty());
    }
}
