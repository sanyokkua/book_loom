package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.inject.Guice;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/** One person, one gender at the end of a review (15i.N2), over the in-memory glossary; the names are invented. */
class ClusterGenderReconcileTest {

    private static final String PROJECT = "p1";

    private final GlossaryRepository glossary = Guice.createInjector(
                    new DocumentModule(), new InMemoryPersistenceModule())
            .getInstance(GlossaryRepository.class);

    private static GlossaryEntry character(final String term) {
        return new GlossaryEntry(PROJECT + ":" + term, PROJECT, term, null, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    // IF the verified gender went to the members through the inferred-gender path, THEN a member already suggested
    // the same gender would stay a suggestion, since that path leaves an unchanged gender as it is (15i review).
    @Test
    void reconcile_memberSuggestedTheSameGender_becomesVerifiedWithTheCluster() {
        glossary.add(character("Arlo Venn").withInferredGender(Gender.MALE));
        glossary.add(character("Arlo").withSuggestedGender(Gender.MALE));

        ClusterGender.reconcile(glossary, PROJECT, "en");

        assertThat(glossary.all(PROJECT).data())
                .extracting(GlossaryEntry::term, GlossaryEntry::gender, GlossaryEntry::genderOrigin)
                .containsExactly(
                        tuple("Arlo Venn", Gender.MALE, GenderOrigin.VERIFIED),
                        tuple("Arlo", Gender.MALE, GenderOrigin.VERIFIED));
    }
}
