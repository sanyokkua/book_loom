package ua.bookloom.pipeline.lexicon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.persistence.LexiconRepository;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.LexiconReading.Decision;
import ua.bookloom.pipeline.glossary.LexiconReading.Verdict;
import ua.bookloom.pipeline.review.ReviewFixtures;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A reading that calls a term a name moves it to the glossary only when the book writes it as a name (15i.N7): a
 * word the book writes in lower case at least {@code FrequencyScan.LOWER_SHARE_LIMIT} of the time is a recurring term
 * whatever one reading says. The book is invented: "lantern" is written four times in lower case and once capitalised
 * away from a sentence start, "Warden" always capitalised.
 */
class LexiconDecisionsTest {

    private static final String PARAGRAPH =
            "The old lantern hung near the door. She said that the Warden kept a lantern and a Lantern lit it.";

    @TempDir
    private Path tempDir;

    private Desk desk;
    private LexiconRepository lexicon;
    private LexiconDecisions decisions;

    @BeforeEach
    void open() {
        desk = ReviewFixtures.markdown(tempDir, String.join("\n\n", Collections.nCopies(4, PARAGRAPH)) + "\n");
        lexicon = Guice.createInjector(new InMemoryPersistenceModule()).getInstance(LexiconRepository.class);
        decisions = new LexiconDecisions(
                lexicon, desk.glossary(), desk.openProjects().index(desk.projectId()));
    }

    private static Map<String, Decision> promote(final String term) {
        return Map.of(LexiconEntry.keyOf(term), new Decision(Verdict.PROMOTE, "рендер"));
    }

    @Test
    void addDecided_nameMajorityOnAWordWrittenMostlyInLowerCase_isKeptAsARecurringTerm() {
        decisions.addDecided(desk.projectId(), List.of("lantern"), promote("lantern"));

        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::term)
                .containsExactly("lantern");
        assertThat(desk.glossary().all(desk.projectId()).data()).isEmpty();
    }

    @Test
    void addDecided_nameMajorityOnAWordWrittenCapitalised_goesToTheGlossary() {
        decisions.addDecided(desk.projectId(), List.of("Warden"), promote("Warden"));

        assertThat(lexicon.all(desk.projectId()).data()).isEmpty();
        assertThat(desk.glossary().findByTerm(desk.projectId(), "Warden").data())
                .hasValueSatisfying(entry -> assertThat(entry.target()).isEqualTo("рендер"));
    }

    @Test
    void applyReview_nameMajorityOnAWordWrittenMostlyInLowerCase_staysInTheLexicon() {
        final LexiconEntry asked = LexiconEntry.of(desk.projectId(), "lantern");
        lexicon.put(asked);

        decisions.applyReview(desk.projectId(), List.of(asked), promote("lantern"));

        assertThat(lexicon.all(desk.projectId()).data())
                .extracting(LexiconEntry::term, LexiconEntry::chosen)
                .containsExactly(org.assertj.core.api.Assertions.tuple("lantern", "рендер"));
        assertThat(desk.glossary().all(desk.projectId()).data()).isEmpty();
    }
}
