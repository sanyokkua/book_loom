package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** The open gender deferrals read as what the person has to do, over invented names. */
class GenderWaitsTest {

    private static final String PROJECT = "p";

    private static GlossaryEntry character(final String term, final String target) {
        return GlossaryEntry.of("e:" + term, PROJECT, term, target, TermType.CHARACTER, Gender.UNKNOWN, false);
    }

    private static Deferral waiting(final String id, final String segmentId, final String character) {
        return new Deferral(id, PROJECT, segmentId, DeferralReason.GENDER_UNKNOWN, character, null, null, null);
    }

    // The export names who the waiting segments wait on, most waiting first, so the warning can say it.
    @Test
    void of_unknownGenderDeferrals_namesEachWaitingCharacterWithItsSegments() {
        final GenderWaits waits = GenderWaits.of(
                List.of(waiting("d1", "s1", "Sam"), waiting("d2", "s2", "Hale"), waiting("d3", "s3", "Hale")),
                List.of(character("Sam", "Сем"), character("Hale", "Хейл")));

        assertThat(waits.characters()).containsExactly(new GenderWait("Hale", 2), new GenderWait("Sam", 1));
    }

    // A gender the glossary came to suggest waits to be confirmed, which is not the same ask as setting a missing one.
    @Test
    void of_suggestedAndUnknownGenderDeferrals_areSplitByWhatThePersonHasToDo() {
        final GlossaryEntry hale = character("Hale", "Хейл").withSuggestedGender(Gender.MALE);

        final GenderWaits waits = GenderWaits.of(
                List.of(waiting("d1", "s1", "Sam"), waiting("d2", "s2", "Sam"), waiting("d3", "s1", "Hale")),
                List.of(character("Sam", "Сем"), hale));

        assertThat(waits.characters())
                .containsExactly(new GenderWait("Sam", 2, false), new GenderWait("Hale", 1, true));
        assertThat(waits.suggestedSegments()).isEqualTo(1);
        assertThat(waits.unknownSegments()).isEqualTo(2);
    }

    // Two unknown-gender characters of one segment are one waiting segment, not two.
    @Test
    void of_twoUnknownGenderDeferralsOnOneSegment_countAsOneWaitingSegment() {
        final GenderWaits waits = GenderWaits.of(
                List.of(waiting("d1", "s1", "Sam"), waiting("d2", "s1", "Hale")),
                List.of(character("Sam", "Сем"), character("Hale", "Хейл")));

        assertThat(waits.unknownSegments()).isEqualTo(1);
        assertThat(waits.characters()).hasSize(2);
    }
}
