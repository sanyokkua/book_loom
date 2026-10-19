package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.NamesPlan.Candidate;
import ua.bookloom.pipeline.index.Mention;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WindowPlan;

/**
 * Which terms the names step reads at each quality dial, with how many windows of how many neighbouring segments and
 * tokens, how many windows go into one call, and the estimate of calls the plan gives before the first one (15i.N4).
 * The time budget (Fast 5–10, Balanced 15–25, Max 30–60 minutes on a novel of about 80k words on 26b) is held by these
 * numbers and the dial's cap of read calls.
 */
class NamesPlanTest {

    private static final String PROJECT = "p1";

    private static GlossaryEntry entry(final String term, final TermType type) {
        return new GlossaryEntry("p1:" + term, PROJECT, term, null, type, Gender.UNKNOWN, false);
    }

    private static Candidate candidate(final GlossaryEntry entry, final int mentions) {
        return new Candidate(entry, mentions);
    }

    /**
     * A glossary of every shape the dials tell apart: a character with five mentions, an untyped name a first-name
     * list already took for a man, a place, an untyped word, a character with four mentions and one with two.
     */
    private static List<Candidate> mixedGlossary() {
        return List.of(
                candidate(entry("Arlo", TermType.CHARACTER), 5),
                candidate(entry("Venn", TermType.OTHER).withSuggestedGender(Gender.MALE), 7),
                candidate(entry("Harrowmere", TermType.PLACE), 9),
                candidate(entry("Lantern", TermType.OTHER), 12),
                candidate(entry("Quill", TermType.CHARACTER), 4),
                candidate(entry("Pell", TermType.CHARACTER), 2));
    }

    private static List<String> readTerms(final NamesPlan plan) {
        return plan.read().stream().map(read -> read.entry().term()).toList();
    }

    /** Characters named {@code Name0}, {@code Name1}, … whose mentions run from {@code first} upwards. */
    private static List<Candidate> characters(final int count, final int first) {
        return IntStream.range(0, count)
                .mapToObj(index -> candidate(entry("Name" + index, TermType.CHARACTER), first + index))
                .toList();
    }

    /** Characters with the given mention counts, for the call estimate. */
    private static List<Candidate> charactersWith(final int... mentions) {
        return IntStream.range(0, mentions.length)
                .mapToObj(index -> candidate(entry("Name" + index, TermType.CHARACTER), mentions[index]))
                .toList();
    }

    /** Windows over one mention each, in reading order, as the index would cut them. */
    private static List<Window> windows(final int count) {
        return IntStream.range(0, count)
                .mapToObj(ordinal ->
                        new Window(new Mention("Ivo", ordinal, 0, 3, 0, true), ordinal, ordinal, "Ivo ran.", 0, 3))
                .toList();
    }

    private static List<Integer> sizes(final List<List<Window>> calls) {
        return calls.stream().map(List::size).toList();
    }

    // IF Fast read every capitalised word, THEN it would not fit five to ten minutes: it reads only the likely
    // characters (a character, or an untyped name a seed already took for a person) with five or more mentions.
    @Test
    void of_fast_readsOnlyLikelyCharactersWithFiveOrMoreMentionsMostFirst() {
        assertThat(readTerms(NamesPlan.of(QualityDial.FAST, mixedGlossary()))).containsExactly("Venn", "Arlo");
    }

    @Test
    void of_balanced_readsEveryCandidateWithThreeOrMoreMentionsMostFirst() {
        assertThat(readTerms(NamesPlan.of(QualityDial.BALANCED, mixedGlossary())))
                .containsExactly("Lantern", "Harrowmere", "Venn", "Arlo", "Quill");
    }

    // Max reads the same terms as Balanced, with more windows each.
    @Test
    void of_max_readsTheCandidatesBalancedReads() {
        assertThat(readTerms(NamesPlan.of(QualityDial.MAX, mixedGlossary())))
                .containsExactly("Lantern", "Harrowmere", "Venn", "Arlo", "Quill");
    }

    @ParameterizedTest(name = "{0}: {1} windows of ±{2} segments at {3} tokens, {4} a call")
    @CsvSource({"FAST,4,1,350,4", "BALANCED,6,2,450,6", "MAX,12,2,450,6"})
    void of_dial_cutsItsWindowsAndCallsToItsSize(
            final QualityDial dial, final int count, final int neighbours, final int tokens, final int perCall) {
        final NamesPlan plan = NamesPlan.of(dial, mixedGlossary());

        assertThat(plan.dial()).isEqualTo(dial);
        assertThat(plan.windows()).isEqualTo(new WindowPlan(count, neighbours, tokens));
        assertThat(plan.windowsPerCall()).isEqualTo(perCall);
    }

    // IF a technical book with hundreds of capitalised terms were read in full, THEN Fast would run 26 minutes against
    // its 5 to 10 (15i.M1): reading stops at the dial's call cap, keeping the terms with the most mentions.
    @ParameterizedTest(name = "{0}: {1} terms read of 200, the most mentioned {2} down to {3}")
    @CsvSource({"FAST,5,72,204,133", "BALANCED,5,156,204,49", "MAX,12,156,211,56"})
    void of_manyCharacters_readsUpToTheDialsCallCapMostMentionedFirst(
            final QualityDial dial, final int first, final int read, final int top, final int bottom) {
        final NamesPlan plan = NamesPlan.of(dial, characters(200, first));

        assertThat(plan.read()).hasSize(read);
        assertThat(plan.read().getFirst().mentions()).isEqualTo(top);
        assertThat(plan.read().getLast().mentions()).isEqualTo(bottom);
        assertThat(plan.estimatedCalls()).isLessThanOrEqualTo(NamesPlan.readCallCap(dial));
    }

    @ParameterizedTest
    @CsvSource({"FAST,72,120", "BALANCED,156,260", "MAX,312,520"})
    void callCaps_perDial_followTheM1Measurements(final QualityDial dial, final int reading, final int pass) {
        assertThat(NamesPlan.readCallCap(dial)).isEqualTo(reading);
        assertThat(NamesPlan.passCallBudget(dial)).isEqualTo(pass);
    }

    @Test
    void of_noCandidates_readsNothingAndEstimatesNoCall() {
        final NamesPlan plan = NamesPlan.of(QualityDial.BALANCED, List.of());

        assertThat(plan.read()).isEmpty();
        assertThat(plan.estimatedCalls()).isZero();
    }

    // At Fast and Balanced a term is read in one call; a term with two mentions is not read at all.
    @ParameterizedTest
    @CsvSource({"FAST,3", "BALANCED,3"})
    void estimatedCalls_fastOrBalanced_isOneCallPerTermRead(final QualityDial dial, final int expected) {
        assertThat(NamesPlan.of(dial, charactersWith(40, 6, 5, 2)).estimatedCalls())
                .isEqualTo(expected);
    }

    // At Max a term gets up to 12 windows, never more than its mentions, shown six to a call: 20 mentions -> 12
    // windows -> 2 calls; 7 -> 7 -> 2; 4 -> 4 -> 1; 3 -> 3 -> 1.
    @Test
    void estimatedCalls_max_countsACallPerSixWindowsOfEachTerm() {
        assertThat(NamesPlan.of(QualityDial.MAX, charactersWith(20, 7, 4, 3)).estimatedCalls())
                .isEqualTo(6);
    }

    @Test
    void estimatedCalls_fast_countsOnlyTheTermsItReads() {
        assertThat(NamesPlan.of(QualityDial.FAST, mixedGlossary()).estimatedCalls())
                .isEqualTo(2);
    }

    // The windows of a term are cut into calls of at most six, as even as they can be and in reading order, so a
    // seventh window is not read alone.
    @ParameterizedTest(name = "{0} with {1} windows -> {2}")
    @CsvSource({"MAX,12,'6,6'", "MAX,7,'4,3'", "MAX,8,'4,4'", "MAX,4,'4'", "BALANCED,6,'6'", "FAST,4,'4'"})
    void calls_termsWindows_areCutIntoEvenCallsOfAtMostTheDialsSize(
            final QualityDial dial, final int windowCount, final String expected) {
        final List<List<Window>> calls = NamesPlan.of(dial, mixedGlossary()).calls(windows(windowCount));

        assertThat(sizes(calls))
                .containsExactlyElementsOf(List.of(expected.split(",")).stream()
                        .map(Integer::valueOf)
                        .toList());
        assertThat(calls.stream().flatMap(List::stream).toList()).isEqualTo(windows(windowCount));
    }

    @Test
    void calls_noWindows_isNoCall() {
        assertThat(NamesPlan.of(QualityDial.MAX, mixedGlossary()).calls(List.of()))
                .isEmpty();
    }

    // IF "a mark on the glass" counted for the bartender Mark, THEN a common word would push a name over the dial's
    // threshold, and the other way round: a mention is the capitalised name, case-sensitive, in the story.
    @Test
    void candidates_bartenderMark_countsTheCapitalisedNameNotTheCommonWord() {
        final BookSlice book = GlossaryTestSegments.of(List.of(
                "Mark poured the beer.",
                "She left a mark on the glass.",
                "Mark wiped the bar.",
                "The mark faded by morning.",
                "Nobody saw Mark leave."));

        final List<Candidate> found = NamesPlan.candidates(List.of(entry("Mark", TermType.OTHER)), book);

        assertThat(found).extracting(Candidate::mentions).containsExactly(3);
    }

    @Test
    void candidates_termTheStoryNeverWrites_hasNoMentions() {
        final BookSlice book = GlossaryTestSegments.of(List.of("Mark poured the beer."));

        assertThat(NamesPlan.candidates(List.of(entry("Quill", TermType.CHARACTER)), book))
                .extracting(Candidate::mentions)
                .containsExactly(0);
    }

    // The one name-matching rule: a possessive ends a run, so "Arlo's Venn" is no mention of "Arlo Venn".
    @Test
    void candidates_multiWordName_countsOnlyBlankSeparatedRunsWithNoPossessive() {
        final BookSlice book = GlossaryTestSegments.of(List.of(
                "Arlo Venn sat down.", "It was Arlo’s Venn that won.", "Arlo, Venn and Pell left.", "Arlo Venn rose."));

        assertThat(NamesPlan.candidates(List.of(entry("Arlo Venn", TermType.CHARACTER)), book))
                .extracting(Candidate::mentions)
                .containsExactly(2);
    }

    // The windows the plan reads are cut around the capitalised name only, at the dial's width: Fast reads one
    // neighbouring paragraph each side.
    @Test
    void windowsOf_bartenderMark_cutsWindowsOnlyAroundTheCapitalisedName() {
        final BookSlice book = GlossaryTestSegments.of(
                List.of("Mark poured the beer.", "She left a mark on the glass.", "Mark wiped the bar."));

        final List<Window> windows =
                NamesPlan.of(QualityDial.FAST, List.of()).windowsOf(entry("Mark", TermType.OTHER), book);

        assertThat(windows).extracting(window -> window.mention().ordinal()).containsExactly(0, 2);
        assertThat(windows.getFirst().text()).isEqualTo("Mark poured the beer.\nShe left a mark on the glass.");
    }
}
