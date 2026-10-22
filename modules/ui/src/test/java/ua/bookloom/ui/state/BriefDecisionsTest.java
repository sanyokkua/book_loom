package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSampleRead;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.pipeline.SampleAnswer;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;

/**
 * "What it decides": the rows the dialog shows while the samples are read (a tally, display only) and once the answer
 * arrives (the pipeline's own decision and evidence). The tally never decides anything the pipeline decides.
 */
class BriefDecisionsTest {

    private static final String QUOTE = "He left before the lamps were lit.";

    private static BriefSampleRead read(final int sample, final String register, final String quote) {
        return new BriefSampleRead(
                sample,
                4,
                Map.of(BriefField.REGISTER, new SampleAnswer(register, quote)),
                "ch" + sample + " · p02–p14",
                "Passage 1: " + QUOTE);
    }

    private static DecisionRow row(final List<DecisionRow> rows, final BriefField group) {
        return rows.stream().filter(row -> row.group() == group).findFirst().orElseThrow();
    }

    // Three of four samples answer formal with a quote that is in the book: the row says so.
    @Test
    void running_threeOfFourAnswerFormalWithQuotes_countsThreeAgreeingOfFourRead() {
        final List<BriefSampleRead> reads = List.of(
                read(1, "formal", QUOTE),
                read(2, "formal", QUOTE),
                read(3, "neutral", QUOTE),
                read(4, "formal", QUOTE));

        final DecisionRow register = row(BriefDecisions.running(reads), BriefField.REGISTER);

        assertThat(register.value()).isEqualToIgnoringCase("formal");
        assertThat(register.agreeing()).isEqualTo(3);
        assertThat(register.samples()).isEqualTo(4);
        assertThat(register.isUncertain()).isFalse();
    }

    // IF a value with no quote counted, THEN a guess would pass for a reading of the book.
    @Test
    void running_aValueWithNoQuote_isNotCountedAndIsShownNotUsed() {
        final List<BriefSampleRead> reads =
                List.of(read(1, "formal", QUOTE), read(2, "formal", ""), read(3, "formal", QUOTE));

        final DecisionRow register = row(BriefDecisions.running(reads), BriefField.REGISTER);

        assertThat(register.agreeing()).isEqualTo(2);
        assertThat(register.samplesRead())
                .extracting(SampleLine::sample, SampleLine::verified)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, true),
                        org.assertj.core.groups.Tuple.tuple(2, false),
                        org.assertj.core.groups.Tuple.tuple(3, true));
    }

    @Test
    void running_eachSampleLine_carriesItsLocationValueAndQuote() {
        final List<BriefSampleRead> reads = List.of(read(1, "formal", QUOTE));

        final SampleLine line = row(BriefDecisions.running(reads), BriefField.REGISTER)
                .samplesRead()
                .getFirst();

        assertThat(line.sample()).isEqualTo(1);
        assertThat(line.location()).isEqualTo("ch1 · p02–p14");
        assertThat(line.value()).isEqualToIgnoringCase("formal");
        assertThat(line.quote()).isEqualTo(QUOTE);
    }

    @Test
    void running_noSampleRead_hasNoRows() {
        assertThat(BriefDecisions.running(List.of())).isEmpty();
    }

    // The four rows are in the order the mockup shows them: genre, register, who tells the story, who reads it.
    @Test
    void answered_anyAnswer_hasFourRowsInTheMockupOrder() {
        final BriefSuggestion answer = new BriefSuggestion(
                "crime", Register.FORMAL_LITERARY, "terse", "adults", NarratorPerson.THIRD, Gender.UNKNOWN, Map.of());

        final List<DecisionRow> rows = BriefDecisions.answered(answer, List.of(read(1, "formal", QUOTE)));

        assertThat(rows)
                .extracting(DecisionRow::group)
                .containsExactly(BriefField.GENRE, BriefField.REGISTER, BriefField.NARRATOR, BriefField.AUDIENCE);
    }

    // The final row is the pipeline's decision: its value, and how many samples agreed of how many were read.
    @Test
    void answered_registerAgreedByThreeOfFour_isTheSuggestionsValueNotUncertain() {
        final BriefSuggestion answer = new BriefSuggestion(
                null,
                Register.CASUAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.REGISTER, new FieldEvidence(FieldEvidence.Basis.PASSAGES, List.of(QUOTE), 3, 4)));

        final DecisionRow register = row(BriefDecisions.answered(answer, List.of()), BriefField.REGISTER);

        assertThat(register.value()).isEqualTo("Casual");
        assertThat(register.agreeing()).isEqualTo(3);
        assertThat(register.samples()).isEqualTo(4);
        assertThat(register.isUncertain()).isFalse();
    }

    // IF a two-two split read as a decision, THEN the brief would carry a coin toss as a reading of the book.
    @Test
    void answered_registerSplitTwoAndTwo_isUncertain() {
        final BriefSuggestion answer = new BriefSuggestion(
                null,
                Register.NEUTRAL,
                null,
                null,
                NarratorPerson.UNSPECIFIED,
                Gender.UNKNOWN,
                Map.of(BriefField.REGISTER, new FieldEvidence(FieldEvidence.Basis.UNCERTAIN, List.of(), 2, 4)));

        final DecisionRow register = row(BriefDecisions.answered(answer, List.of()), BriefField.REGISTER);

        assertThat(register.value()).isEqualTo("Neutral");
        assertThat(register.agreeing()).isEqualTo(2);
        assertThat(register.samples()).isEqualTo(4);
        assertThat(register.isUncertain()).isTrue();
    }
}
