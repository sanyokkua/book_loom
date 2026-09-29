package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Guice;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.persistence.TmRepository;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.persistence.PersistenceModule;

/** Context-aware reuse, hints and suggestions over the in-memory translation-memory repository. */
class TranslationMemoryTest {

    private static final String PROJECT = "p1";
    private static final String BEFORE = "She looked up.";
    private static final String AFTER = "He shrugged.";

    private TmRepository repository;
    private TranslationMemory memory;

    @BeforeEach
    void setUp() {
        repository = Guice.createInjector(new PersistenceModule()).getInstance(TmRepository.class);
        memory = new TranslationMemory(repository, PROJECT);
    }

    @Test
    void lookup_sameTextBetweenSameNeighbours_reusesAlthoughPositionalIdsDiffer() {
        storeMiddle("ch01.xhtml:4", "Yes.", "Так.");
        final List<Segment> chapterThree = middle("ch03.xhtml:12", "Yes.");

        final TmLookup lookup = memory.lookup(chapterThree.get(1), chapterThree);

        assertThat(lookup.reuse()).isNotNull();
        assertThat(lookup.reuse().targetInner()).isEqualTo("Так.");
        assertThat(lookup.hints()).isEmpty();
        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_sameTextBetweenDifferentNeighbours_noReuseButTheStoredTargetIsAHint() {
        storeMiddle("ch01.xhtml:4", "Yes.", "Так.");
        final List<Segment> elsewhere = List.of(
                segment("ch05.xhtml:1", "It rained."),
                segment("ch05.xhtml:2", "Yes."),
                segment("ch05.xhtml:3", "Nobody came."));

        final TmLookup lookup = memory.lookup(elsewhere.get(1), elsewhere);

        assertThat(lookup.reuse()).isNull();
        assertThat(lookup.hints()).extracting(TmEntry::targetInner).containsExactly("Так.");
        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_firstSegmentFollowedByTheSentenceThatFollowedAMidUnitMatch_noReuseButAHint() {
        storeMiddle("ch01.xhtml:4", "Yes.", "Так.");
        final List<Segment> opening = List.of(segment("ch04.xhtml:0", "Yes."), segment("ch04.xhtml:1", AFTER));

        final TmLookup lookup = memory.lookup(opening.get(0), opening);

        assertThat(lookup.reuse()).isNull();
        assertThat(lookup.hints()).extracting(TmEntry::targetInner).containsExactly("Так.");
    }

    @Test
    void lookup_firstSegmentBesideTheSameNextNeighbourAsAStoredFirstSegment_reuses() {
        final List<Segment> chapterOne = List.of(segment("ch01.xhtml:0", "Yes."), segment("ch01.xhtml:1", AFTER));
        store(chapterOne.get(0), chapterOne, "Так.");
        final List<Segment> chapterFour = List.of(segment("ch04.xhtml:0", "Yes."), segment("ch04.xhtml:1", AFTER));

        final TmLookup lookup = memory.lookup(chapterFour.get(0), chapterFour);

        assertThat(lookup.reuse()).isNotNull();
        assertThat(lookup.reuse().targetInner()).isEqualTo("Так.");
    }

    @Test
    void lookup_lastSegmentBesideTheSamePreviousNeighbourAsAStoredLastSegment_reuses() {
        final List<Segment> chapterOne = List.of(segment("ch01.xhtml:0", BEFORE), segment("ch01.xhtml:1", "Yes."));
        store(chapterOne.get(1), chapterOne, "Так.");
        final List<Segment> chapterTwo = List.of(segment("ch02.xhtml:7", BEFORE), segment("ch02.xhtml:8", "Yes."));

        final TmLookup lookup = memory.lookup(chapterTwo.get(1), chapterTwo);

        assertThat(lookup.reuse()).isNotNull();
    }

    @Test
    void lookup_storedSourceOneEditAway_isASuggestionNotAReuse() {
        storeMiddle("ch01.xhtml:4", "He opened the old doors.", "Він відчинив старі двері.");
        final List<Segment> unit = middle("ch02.xhtml:9", "He opened the old door.");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.reuse()).isNull();
        assertThat(lookup.hints()).isEmpty();
        assertThat(lookup.suggestions()).extracting(TmEntry::targetInner).containsExactly("Він відчинив старі двері.");
    }

    @Test
    void lookup_storedSourceOfSimilarityPointSeven_isNotOffered() {
        storeMiddle("ch01.xhtml:4", "abcdefgxyz", "x");
        final List<Segment> unit = middle("ch02.xhtml:9", "abcdefghij");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_storedSourceOfSimilarityExactlyPointEightFive_isASuggestion() {
        storeMiddle("ch01.xhtml:4", "abcdefghijklmnopqrst", "x");
        final List<Segment> unit = middle("ch02.xhtml:9", "abcdefghijklmnopqXYZ");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.suggestions()).hasSize(1);
    }

    @Test
    void lookup_storedSourceOutsideTheLengthBand_isNeverOffered() {
        storeMiddle("ch01.xhtml:4", "abcde", "x");
        final List<Segment> unit = middle("ch02.xhtml:9", "abcdefghijklmnopqrst");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_eighteenEmojiStoredAgainstTwentyEmoji_isASuggestionAlthoughItsUtf16LengthLiesOutsideTheBand() {
        final String stored = "\uD83D\uDE00".repeat(18);
        storeMiddle("ch01.xhtml:4", stored, "x");
        final List<Segment> unit = middle("ch02.xhtml:9", "\uD83D\uDE00".repeat(20));

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(stored.length()).isEqualTo(36);
        assertThat(lookup.suggestions()).hasSize(1);
    }

    @Test
    void lookup_severalNearMatches_listsTheMostSimilarFirst() {
        storeMiddle("ch01.xhtml:1", "He opened the old dorm.", "B");
        storeMiddle("ch01.xhtml:5", "He opened the old doors.", "A");
        final List<Segment> unit = middle("ch02.xhtml:9", "He opened the old door.");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.suggestions()).extracting(TmEntry::targetInner).containsExactly("A", "B");
    }

    @Test
    void lookup_sameSourceInAnotherContext_isAHintAndNeverAlsoASuggestion() {
        storeMiddle("ch01.xhtml:4", "Yes.", "Так.");
        final List<Segment> solo = List.of(segment("ch06.xhtml:0", "Yes."));
        store(solo.get(0), solo, "Ага.");
        final List<Segment> unit = List.of(
                segment("ch07.xhtml:2", "Night fell."),
                segment("ch07.xhtml:3", "Yes."),
                segment("ch07.xhtml:4", "Then silence."));

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.reuse()).isNull();
        assertThat(lookup.hints()).extracting(TmEntry::targetInner).containsExactlyInAnyOrder("Так.", "Ага.");
        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_segmentWithNothingStored_findsNothing() {
        final List<Segment> unit = middle("ch02.xhtml:9", "Yes.");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.reuse()).isNull();
        assertThat(lookup.hints()).isEmpty();
        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_segmentWithNoTextOnlyTokens_offersNoSuggestion() {
        storeMiddle("ch01.xhtml:4", "", "x");
        final List<Segment> unit = middle("ch02.xhtml:9", "\u27E6g0\u27E7");

        final TmLookup lookup = memory.lookup(unit.get(1), unit);

        assertThat(lookup.suggestions()).isEmpty();
    }

    @Test
    void lookup_segmentAbsentFromItsUnit_isRejected() {
        final List<Segment> unit = middle("ch02.xhtml:9", "Yes.");

        assertThatThrownBy(() -> memory.lookup(segment("ch09.xhtml:0", "Yes."), unit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"20,17,23", "10,9,11", "1,1,1", "3,3,3"})
    void lookup_displayTextOfNCodePoints_asksTheRepositoryForTheBand(
            final int codePoints, final int min, final int max) {
        final BandRecordingTmRepository recording = new BandRecordingTmRepository(repository);
        final List<Segment> unit = middle("ch02.xhtml:9", "a".repeat(codePoints));

        new TranslationMemory(recording, PROJECT).lookup(unit.get(1), unit);

        assertThat(recording.bands()).containsExactly(List.of(min, max));
    }

    @Test
    void entryFor_maskedSegment_keepsDisplayTextAsSourceAndTheMaskedTargetAsTarget() {
        final List<Segment> unit = List.of(
                segment("ch01.xhtml:0", BEFORE),
                segment("ch01.xhtml:1", "\u27E6g0\u27E7Hale  opened\n the door.\u27E6g1\u27E7"),
                segment("ch01.xhtml:2", AFTER));

        final TmEntry entry = memory.entryFor(unit.get(1), unit, "\u27E6g0\u27E7Гейл відчинив двері.\u27E6g1\u27E7");

        assertThat(entry.projectId()).isEqualTo(PROJECT);
        assertThat(entry.sourceHash()).isEqualTo("h:\u27E6g0\u27E7Hale  opened\n the door.\u27E6g1\u27E7");
        assertThat(entry.contextKey()).isEqualTo("5167aa95634e1c47e62cbe653f5c59e05c776854ea18ccd2c2cc58b6206cca49");
        assertThat(entry.id()).isEqualTo(entry.sourceHash() + ":" + entry.contextKey());
        assertThat(entry.sourceInner()).isEqualTo("Hale opened the door.");
        assertThat(entry.targetInner()).isEqualTo("\u27E6g0\u27E7Гейл відчинив двері.\u27E6g1\u27E7");
    }

    @ParameterizedTest
    @CsvSource({
        "prev mid next,994582bc9d6ce32ff2cd5fd6e670244baf5a255b88a7e3b779ca99b9639236f4",
        "mid next,8b8639a43948e5ad7c58a226b38300b30983aed5ebf0e2268b13503cddc6342e",
        "prev mid,be9bda116c68fcd29f6dd3bbeb4ddb2a008fd4a00c9fc64235233022407465f0",
        "mid,d212373670e3722d088bfd5ee021839a2b636c28a646d65e145f40591c2bc20d"
    })
    void contextKey_neighboursOrUnitEdges_isTheSha256OfTheirSourceHashesOrEdgeMarkers(
            final String unitTexts, final String expected) {
        final List<Segment> unit = Arrays.stream(unitTexts.split(" "))
                .map(text -> segment("u:" + text, text))
                .toList();
        final Segment subject = unit.stream()
                .filter(candidate -> candidate.id().equals("u:mid"))
                .findFirst()
                .orElseThrow();

        assertThat(memory.contextKey(subject, unit)).isEqualTo(expected);
    }

    @Test
    void entryFor_sameTextBetweenSameNeighboursInTwoUnits_givesTheSameKey() {
        final List<Segment> first = middle("ch01.xhtml:4", "Yes.");
        final List<Segment> second = middle("ch03.xhtml:12", "Yes.");

        final TmEntry a = memory.entryFor(first.get(1), first, "Так.");
        final TmEntry b = memory.entryFor(second.get(1), second, "Так.");

        assertThat(a.id()).isEqualTo(b.id());
        assertThat(a.contextKey()).isEqualTo(b.contextKey());
    }

    @Test
    void entryFor_anyCall_writesNothingToTheRepository() {
        final List<Segment> unit = middle("ch01.xhtml:4", "Yes.");

        memory.entryFor(unit.get(1), unit, "Так.");
        memory.lookup(unit.get(1), unit);

        assertThat(repository.exact(PROJECT, unit.get(1).sourceHash()).data()).isEmpty();
    }

    private void store(final Segment subject, final List<Segment> unit, final String target) {
        repository.put(memory.entryFor(subject, unit, target));
    }

    private void storeMiddle(final String subjectId, final String text, final String target) {
        final List<Segment> unit = middle(subjectId, text);
        store(unit.get(1), unit, target);
    }

    /** A unit of three segments with the subject in the middle, between {@link #BEFORE} and {@link #AFTER}. */
    private static List<Segment> middle(final String subjectId, final String text) {
        final String unit = subjectId.substring(0, subjectId.indexOf(':'));
        return List.of(segment(unit + ":before", BEFORE), segment(subjectId, text), segment(unit + ":after", AFTER));
    }

    private static Segment segment(final String id, final String masked) {
        return TranslationMemoryFixtures.segment(id, masked);
    }
}
