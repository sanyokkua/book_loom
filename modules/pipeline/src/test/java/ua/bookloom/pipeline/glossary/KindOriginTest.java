package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NameKnowledge;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.TermType;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;

/**
 * Where an entry's kind came from (design D3): the scan's unset kind is a rule's, a pronoun count is the book's (or
 * likely when weak), the model's bare-name answer and a review reading are likely, and nothing automatic retypes a kind
 * the person chose other than the unset kind, other.
 */
class KindOriginTest {

    private static final String PROJECT = "p1";
    private static final List<String> STRONG_FEMALE = List.of(
            "Wren poured the tea. She smiled.",
            "Wren stopped at the door. She looked back.",
            "Wren was tired; she sat.",
            "Wren laughed. She waved.",
            "Wren ran home. She was late.");
    private static final List<String> WEAK_FEMALE = STRONG_FEMALE.subList(0, 3);

    private static GlossaryEntry scanned() {
        final GlossaryRepository glossary = Guice.createInjector(new DocumentModule(), new InMemoryPersistenceModule())
                .getInstance(GlossaryRepository.class);
        final ScanEntries.Found found = Objects.requireNonNull(
                ScanEntries.of(PROJECT, List.of(new NameCandidate("Wren", 5, "Wren ran.")), glossary, List.of(), "en")
                        .data());
        return found.proposals().getFirst();
    }

    private static GlossaryEntry personTyped(final TermType type) {
        return GlossaryEntry.of("p1:wren", PROJECT, "Wren", null, type, Gender.UNKNOWN, false);
    }

    @Test
    void scan_newProposal_hasTheKindOtherFromARule() {
        final GlossaryEntry proposal = scanned();

        assertThat(proposal.type()).isEqualTo(TermType.OTHER);
        assertThat(proposal.typeOrigin()).isEqualTo(Origin.RULE);
    }

    @Test
    void nameKnowledge_unsetKind_takesTheAnswerAsLikely() {
        final GlossaryEntry applied = NameKnowledgeReplies.applyTo(
                scanned(), new NameKnowledge(Gender.FEMALE, TermType.CHARACTER, null, 0.9));

        assertThat(applied.type()).isEqualTo(TermType.CHARACTER);
        assertThat(applied.typeOrigin()).isEqualTo(Origin.LIKELY);
    }

    // Other is the unset kind: an entry the person added without choosing one still takes the model's answer.
    @Test
    void nameKnowledge_kindOtherFromTheAddDialog_takesTheAnswerAsLikely() {
        final GlossaryEntry applied = NameKnowledgeReplies.applyTo(
                personTyped(TermType.OTHER), new NameKnowledge(Gender.FEMALE, TermType.CHARACTER, null, 0.9));

        assertThat(applied.type()).isEqualTo(TermType.CHARACTER);
        assertThat(applied.typeOrigin()).isEqualTo(Origin.LIKELY);
    }

    @Test
    void reviewReading_unsetKind_isLikely() {
        final GlossaryEntry entry = scanned();

        final GlossaryEntry guessed = ReviewCommit.guessed(
                entry, new Verdict(entry, Kind.NAME, TermType.PLACE, Gender.UNKNOWN, "", false, true));

        assertThat(guessed.type()).isEqualTo(TermType.PLACE);
        assertThat(guessed.typeOrigin()).isEqualTo(Origin.LIKELY);
    }

    @Test
    void reviewReading_characterThePersonChoseWithALikelyGender_isNotRetyped() {
        final GlossaryEntry entry = personTyped(TermType.CHARACTER).withSuggestedGender(Gender.MALE);

        final GlossaryEntry guessed = ReviewCommit.guessed(
                entry, new Verdict(entry, Kind.NAME, TermType.PLACE, Gender.UNKNOWN, "", false, true));

        assertThat(guessed.type()).isEqualTo(TermType.CHARACTER);
        assertThat(guessed.typeOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void pronounCount_strong_makesACharacterFromTheBook() {
        final GlossaryEntry seeded = PronounGender.seeded(scanned(), STRONG_FEMALE, "en");

        assertThat(seeded.type()).isEqualTo(TermType.CHARACTER);
        assertThat(seeded.typeOrigin()).isEqualTo(Origin.BOOK);
    }

    @Test
    void pronounCount_weak_makesALikelyCharacter() {
        final GlossaryEntry seeded = PronounGender.seeded(scanned(), WEAK_FEMALE, "en");

        assertThat(seeded.type()).isEqualTo(TermType.CHARACTER);
        assertThat(seeded.typeOrigin()).isEqualTo(Origin.LIKELY);
    }

    @Test
    void pronounCount_termThePersonChose_staysTheirTerm() {
        final GlossaryEntry seeded = PronounGender.seeded(personTyped(TermType.TERM), STRONG_FEMALE, "en");

        assertThat(seeded.type()).isEqualTo(TermType.TERM);
        assertThat(seeded.typeOrigin()).isEqualTo(Origin.USER);
    }

    @Test
    void pronounCount_characterThePersonChose_keepsTheirOrigin() {
        final GlossaryEntry seeded = PronounGender.seeded(personTyped(TermType.CHARACTER), STRONG_FEMALE, "en");

        assertThat(seeded.gender()).isEqualTo(Gender.FEMALE);
        assertThat(seeded.typeOrigin()).isEqualTo(Origin.USER);
    }
}
