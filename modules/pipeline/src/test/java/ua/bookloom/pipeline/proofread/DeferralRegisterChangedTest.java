package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Origin;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;

/**
 * The one helper every glossary writer calls (task 13.3): which term and gender deferrals a write leaves on decided
 * segments, and which it must not.
 */
class DeferralRegisterChangedTest {

    private static final String PROJECT = "p1";

    @Test
    void unknownGender_likelyFemale_recordsNone() {
        assertThat(DeferralRegister.unknownGender(
                        PROJECT,
                        sam(),
                        List.of(entry("Sam", TermType.CHARACTER, Gender.UNKNOWN).withSuggestedGender(Gender.FEMALE))))
                .isEmpty();
    }

    @Test
    void termChanged_twoLockedEntries_twoDeferrals() {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Хейл і Мілтон пішли."));

        final List<Deferral> deferrals = new ArrayList<>(DeferralRegister.changed(
                term("Hale", "Хейл", true), term("Hale", "Гейл", true), decided, Set.of(), null));
        deferrals.addAll(DeferralRegister.changed(
                term("Milton", "Мілтон", true), term("Milton", "Мілтен", true), decided, Set.of(), null));

        assertThat(deferrals).extracting(Deferral::waitingOn).containsExactly("Hale", "Milton");
    }

    @Test
    void termChanged_pinnedInflectedForm_recorded() {
        final List<SegmentRecord> decided =
                List.of(decided("ch01.xhtml:3", "Він пішов до Хейла."), decided("ch01.xhtml:4", "Він пішов."));

        final List<Deferral> deferrals =
                DeferralRegister.termChanged(term("Hale", "Хейл", false), term("Hale", "Гейл", false), decided, "uk");

        assertThat(deferrals).extracting(Deferral::segmentId).containsExactly("ch01.xhtml:3");
    }

    @Test
    void termChanged_lockedInflectedForm_notRecorded() {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Він пішов до Хейла."));

        assertThat(DeferralRegister.termChanged(term("Hale", "Хейл", true), term("Hale", "Гейл", true), decided, "uk"))
                .isEmpty();
    }

    @Test
    void changed_entryHadNoGender_waitingDeferralsStay() {
        final GlossaryEntry before = entry("Sam", TermType.CHARACTER, Gender.UNKNOWN);
        final List<SegmentRecord> decided =
                List.of(decided("ch01.xhtml:9", "Сем прийшов."), decided("ch01.xhtml:10", "Сем пішов."));

        assertThat(DeferralRegister.changed(
                        before, before.withGender(Gender.MALE), decided, Set.of("ch01.xhtml:9", "ch01.xhtml:10"), null))
                .isEmpty();
    }

    @Test
    void changed_likelyConfirmedSame_onePerSegment() {
        final GlossaryEntry likely =
                entry("Corin Ashby", TermType.CHARACTER, Gender.UNKNOWN).withSuggestedGender(Gender.MALE);
        final List<SegmentRecord> decided = List.of(
                decided("ch01.xhtml:1", "Корін прийшов."),
                decided("ch01.xhtml:2", "Корін пішов."),
                decided("ch01.xhtml:3", "Корін сів."),
                decided("ch01.xhtml:4", "Ніхто."));

        final List<Deferral> deferrals = DeferralRegister.changed(
                likely,
                likely.withGenderFrom(Gender.MALE, Origin.BOOK),
                decided,
                Set.of("ch01.xhtml:1", "ch01.xhtml:2", "ch01.xhtml:3"),
                null);

        assertThat(deferrals)
                .extracting(Deferral::reason, Deferral::segmentId, Deferral::waitingOn)
                .containsExactly(
                        tuple(DeferralReason.GENDER_CHANGED, "ch01.xhtml:1", "Corin Ashby"),
                        tuple(DeferralReason.GENDER_CHANGED, "ch01.xhtml:2", "Corin Ashby"),
                        tuple(DeferralReason.GENDER_CHANGED, "ch01.xhtml:3", "Corin Ashby"));
    }

    @Test
    void changed_enforcedGenderChanged_recordsOnePerNamingSegment() {
        final GlossaryEntry before = entry("Mara Quill", TermType.CHARACTER, Gender.FEMALE);

        assertThat(DeferralRegister.changed(
                        before,
                        before.withGender(Gender.MALE),
                        List.of(decided("ch01.xhtml:1", "Мара."), decided("ch01.xhtml:2", "Мара.")),
                        Set.of("ch01.xhtml:1", "ch01.xhtml:2"),
                        null))
                .hasSize(2);
    }

    @Test
    void changed_likelyValueArrives_recordsNone() {
        final GlossaryEntry before = entry("Sam", TermType.CHARACTER, Gender.MALE);

        assertThat(DeferralRegister.changed(
                        before,
                        before.withSuggestedGender(Gender.FEMALE),
                        List.of(decided("ch01.xhtml:1", "Сем.")),
                        Set.of("ch01.xhtml:1"),
                        null))
                .isEmpty();
    }

    private static Segment sam() {
        final String masked = "Sam met Alex at the Harbour.";
        return new Segment(
                "ch01.xhtml:9",
                "ch01.xhtml",
                9,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static GlossaryEntry entry(final String term, final TermType type, final Gender gender) {
        return GlossaryEntry.of(
                PROJECT + ":" + term.toLowerCase(Locale.ROOT), PROJECT, term, null, type, gender, false);
    }

    private static GlossaryEntry term(final String term, @Nullable final String target, final boolean locked) {
        return GlossaryEntry.of(
                PROJECT + ":" + term.toLowerCase(Locale.ROOT),
                PROJECT,
                term,
                target,
                TermType.CHARACTER,
                Gender.MALE,
                locked);
    }

    private static SegmentRecord decided(final String segmentId, final String target) {
        return new SegmentRecord(
                PROJECT,
                segmentId,
                "ch01.xhtml",
                0,
                SegmentKind.PARAGRAPH,
                SegmentStatus.ACCEPTED,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                false,
                null);
    }
}
