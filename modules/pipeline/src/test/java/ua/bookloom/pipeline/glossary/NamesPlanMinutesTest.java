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
import ua.bookloom.pipeline.glossary.NamesPlan.Minutes;

/**
 * What the names plan says it costs before the first call (15i.N8, 15i.M1). The dial's time budget (Fast 5–10, Balanced
 * 15–25, Max 30–60 minutes) is what a pass of the dial's call budget (120, 260, 520 calls) takes at the measured pace of
 * about 4.6 seconds a call on e4b. The minutes are the calls' seconds rounded up to a whole minute, at both ends of the
 * range.
 */
class NamesPlanMinutesTest {

    private static List<Candidate> characters(final int count, final int mentions) {
        return IntStream.range(0, count)
                .mapToObj(index -> new Candidate(
                        new GlossaryEntry(
                                "p1:c" + index, "p1", "Name" + index, null, TermType.CHARACTER, Gender.MALE, false),
                        mentions))
                .toList();
    }

    // IF the estimate were a flat number per dial, THEN a short book would be told "about 15–25 minutes"; it scales
    // with the calls, and the dial's call budget takes the dial's documented minutes.
    @ParameterizedTest(name = "{0}: {1} calls -> {2}-{3} min")
    @CsvSource({
        "FAST,     120,  6, 10",
        "BALANCED, 260, 16, 25",
        "MAX,      520, 31, 60",
        "FAST,      16,  1,  2",
        "BALANCED,   3,  1,  1",
        "MAX,        5,  1,  1",
        "BALANCED,   0,  0,  0"
    })
    void minutesFor_callBudgetOfTheDial_fitsTheDialsMinutes(
            final QualityDial dial, final int calls, final int low, final int high) {
        assertThat(NamesPlan.minutesFor(dial, calls)).isEqualTo(new Minutes(low, high));
    }

    @ParameterizedTest
    @CsvSource({"FAST,5,10", "BALANCED,15,25", "MAX,30,60"})
    void passCallBudget_perDial_staysInsideTheDialsMinutes(final QualityDial dial, final int low, final int high) {
        final Minutes minutes = NamesPlan.minutesFor(dial, NamesPlan.passCallBudget(dial));

        assertThat(minutes.low()).isBetween(low, high);
        assertThat(minutes.high()).isBetween(low, high);
    }

    @ParameterizedTest(name = "{0}: {1} terms of {2} mentions -> {3} calls, {4}-{5} min")
    @CsvSource({
        "FAST,     16,  5, 16, 1, 2",
        "BALANCED, 16,  6, 16, 1, 2",
        "MAX,      16, 12, 32, 2, 4",
        "BALANCED,  0,  6,  0, 0, 0"
    })
    void estimatedMinutes_knownCandidates_scalesWithTheCallsRead(
            final QualityDial dial,
            final int terms,
            final int mentions,
            final int calls,
            final int low,
            final int high) {
        final NamesPlan plan = NamesPlan.of(dial, characters(terms, mentions));

        assertThat(plan.estimatedCalls()).isEqualTo(calls);
        assertThat(plan.estimatedMinutes()).isEqualTo(new Minutes(low, high));
    }

    // IF a term read below the dial's mention floor counted, THEN the estimate would promise calls never made.
    @Test
    void estimatedMinutes_termsBelowTheFloor_countNoCalls() {
        final NamesPlan plan = NamesPlan.of(QualityDial.BALANCED, characters(10, 2));

        assertThat(plan.estimatedMinutes()).isEqualTo(new Minutes(0, 0));
    }
}
