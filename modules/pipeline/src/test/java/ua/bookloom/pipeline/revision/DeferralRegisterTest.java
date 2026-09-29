package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;

/** Which decided segments a changed glossary term leaves holding a rendering the person has replaced. */
class DeferralRegisterTest {

    private static final String PROJECT = "p1";

    static Stream<Arguments> changesThatRecordNothing() {
        return Stream.of(
                Arguments.of("an unlocked entry", term("Hale", "Хейл", false), term("Hale", "Гейл", false)),
                Arguments.of("an entry with no previous target", term("Hale", null, false), term("Hale", "Гейл", true)),
                Arguments.of(
                        "an entry with a blank previous target", term("Hale", " ", true), term("Hale", "Гейл", true)),
                Arguments.of("an unchanged target", term("Hale", "Хейл", true), term("Hale", "Хейл", true)),
                Arguments.of(
                        "an entry that was locked and is now unlocked",
                        term("Hale", "Хейл", true),
                        term("Hale", "Гейл", false)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("changesThatRecordNothing")
    void termChanged_changeThatDoesNotQualify_recordsNothing(
            final String description, final GlossaryEntry before, final GlossaryEntry after) {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Хейл пішов."));

        assertThat(DeferralRegister.termChanged(before, after, decided)).isEmpty();
    }

    @Test
    void termChanged_lockedTargetChanged_recordsOnlyTheSegmentsHoldingThePreviousTargetWholeWord() {
        final List<SegmentRecord> decided = List.of(
                decided("ch01.xhtml:3", "Хейл пішов."),
                decided("ch01.xhtml:4", "Хейлова пішла."),
                decided("ch01.xhtml:5", "Він пішов."));

        final List<Deferral> deferrals =
                DeferralRegister.termChanged(term("Hale", "Хейл", false), term("Hale", "Гейл", true), decided);

        assertThat(deferrals)
                .containsExactly(new Deferral(
                        "p1:ch01.xhtml:3:TERM:Hale",
                        PROJECT,
                        "ch01.xhtml:3",
                        DeferralReason.TERM,
                        "Hale",
                        "Хейл",
                        null,
                        null));
    }

    @Test
    void termChanged_bothTermsChangedInOneSegment_recordsTwoDeferralsOnIt() {
        final List<SegmentRecord> decided = List.of(decided("ch01.xhtml:3", "Хейл і Мілтон пішли."));
        final List<Deferral> deferrals = new ArrayList<>();

        deferrals.addAll(DeferralRegister.termChanged(term("Hale", "Хейл", true), term("Hale", "Гейл", true), decided));
        deferrals.addAll(
                DeferralRegister.termChanged(term("Milton", "Мілтон", true), term("Milton", "Мілтен", true), decided));

        assertThat(deferrals)
                .extracting(Deferral::segmentId, Deferral::waitingOn, Deferral::replacedRendering, Deferral::id)
                .containsExactly(
                        tuple("ch01.xhtml:3", "Hale", "Хейл", "p1:ch01.xhtml:3:TERM:Hale"),
                        tuple("ch01.xhtml:3", "Milton", "Мілтон", "p1:ch01.xhtml:3:TERM:Milton"));
    }

    @Test
    void termChanged_segmentWithAnEditOfItsOwn_isJudgedByTheEditNotTheMachineTarget() {
        final SegmentRecord editedAway =
                decided("ch01.xhtml:3", "Хейл пішов.").withUserTarget("Він пішов.", "Він пішов.");
        final SegmentRecord editedToHold =
                decided("ch01.xhtml:4", "Він пішов.").withUserTarget("Хейл сів.", "Хейл сів.");

        final List<Deferral> deferrals = DeferralRegister.termChanged(
                term("Hale", "Хейл", true), term("Hale", "Гейл", true), List.of(editedAway, editedToHold));

        assertThat(deferrals).extracting(Deferral::segmentId).containsExactly("ch01.xhtml:4");
    }

    @Test
    void termChanged_segmentWithNoTargetYet_recordsNothing() {
        final SegmentRecord pending = decided("ch01.xhtml:3", "x").withMachineTarget(null, null);

        assertThat(DeferralRegister.termChanged(
                        term("Hale", "Хейл", true), term("Hale", "Гейл", true), List.of(pending)))
                .isEmpty();
    }

    private static GlossaryEntry term(final String term, @Nullable final String target, final boolean locked) {
        return new GlossaryEntry(
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
                0,
                false,
                null);
    }
}
