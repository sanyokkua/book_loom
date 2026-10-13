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
 * What the names plan says it costs before the first call (15i.N8). The dial's time budget (Fast 5–10, Balanced 15–25,
 * Max 30–60 minutes) is what the plan with its most calls takes (80 terms read: 80 calls at Fast and Balanced, 160 at
 * Max), spread over those calls: a read call takes 3.75 to 7.5 seconds at Fast, 11.25 to 18.75 at Balanced and 11.25
 * to 22.5 at Max. The minutes are the calls' seconds rounded up to a whole minute, at both ends of the range.
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
    // with the calls the plan makes, and the plan with its cap of 80 terms is the dial's documented budget.
    @ParameterizedTest(name = "{0}: {1} terms of {2} mentions -> {3} calls, {4}-{5} min")
    @CsvSource({
        "FAST,     80,  5,  80,  5, 10",
        "BALANCED, 80,  6,  80, 15, 25",
        "MAX,      80, 12, 160, 30, 60",
        "FAST,     16,  5,  16,  1,  2",
        "BALANCED, 16,  6,  16,  3,  5",
        "MAX,      16, 12,  32,  6, 12",
        "BALANCED,  3,  6,   3,  1,  1",
        "MAX,       5,  6,   5,  1,  2",
        "BALANCED,  0,  6,   0,  0,  0"
    })
    void estimatedMinutes_knownCandidates_followsTheDialsBudget(
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
