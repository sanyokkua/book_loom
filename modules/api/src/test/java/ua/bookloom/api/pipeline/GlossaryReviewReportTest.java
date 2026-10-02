package ua.bookloom.api.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;

/** A review's counts are never negative and its entries never change after it is made. */
class GlossaryReviewReportTest {

    @ParameterizedTest
    @CsvSource({"-1,0,0", "0,-1,0", "0,0,-1"})
    void new_negativeCount_isRejected(final int removed, final int updated, final int suggested) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GlossaryReviewReport(removed, updated, suggested, List.of()));
    }

    @Test
    void new_entriesListChangedAfterwards_keepsTheEntriesItWasGiven() {
        final List<GlossaryEntry> entries = new ArrayList<>();
        entries.add(new GlossaryEntry("e1", "p1", "Hale", null, TermType.CHARACTER, Gender.MALE, false));

        final GlossaryReviewReport report = new GlossaryReviewReport(1, 0, entries);
        entries.clear();

        assertThat(report.entries()).extracting(GlossaryEntry::term).containsExactly("Hale");
    }
}
