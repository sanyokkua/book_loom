package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.FieldEvidence.Basis;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/** A suggestion is written into the brief without dropping its policy keys, and the fields that stand on a guess are marked likely. */
class SuggestionMergeKeysTest {

    private static BriefSuggestion suggestion(final Map<BriefField, FieldEvidence> evidence) {
        return new BriefSuggestion(
                "Gothic novel",
                Register.FORMAL_LITERARY,
                null,
                "adults",
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                evidence);
    }

    @Test
    void withSuggestion_brief_keepsApostropheQuoteStyleAddressAndConventions() {
        final BookBrief held = BriefKeysFixtures.withKeys(BookBrief.defaults("en"));

        final BookBrief merged = SuggestionMerge.withSuggestion(held, suggestion(Map.of()));

        assertThat(merged.genre()).isEqualTo("Gothic novel");
        assertThat(merged.apostrophe()).isEqualTo("’");
        assertThat(merged.quoteStyle()).isEqualTo(BriefKeysFixtures.QUOTES);
        assertThat(merged.defaultAddress()).isEqualTo(BriefKeysFixtures.ADDRESS);
        assertThat(merged.conventions()).isEqualTo(BriefKeysFixtures.CONVENTIONS);
    }

    // IF a likely field were not stored, THEN a guess would later read as a settled value.
    @Test
    void withSuggestion_likelyFields_areStoredAsLikely() {
        final Map<BriefField, FieldEvidence> evidence = Map.of(
                BriefField.GENRE, new FieldEvidence(Basis.LIKELY, List.of(), 1, 3),
                BriefField.AUDIENCE, new FieldEvidence(Basis.PASSAGES, List.of(), 3, 3));

        final BookBrief merged = SuggestionMerge.withSuggestion(BookBrief.defaults("en"), suggestion(evidence));

        assertThat(merged.likely()).containsExactly(ua.bookloom.api.project.BriefField.GENRE);
    }

    // The register of a LIKELY field counts as kept: the model's best guess is applied and marked.
    @Test
    void withSuggestion_likelyRegister_isApplied() {
        final Map<BriefField, FieldEvidence> evidence =
                Map.of(BriefField.REGISTER, new FieldEvidence(Basis.LIKELY, List.of(), 1, 3));

        final BookBrief merged = SuggestionMerge.withSuggestion(BookBrief.defaults("en"), suggestion(evidence));

        assertThat(merged.register()).isEqualTo(Register.FORMAL_LITERARY);
        assertThat(merged.likely()).contains(ua.bookloom.api.project.BriefField.REGISTER);
    }
}
