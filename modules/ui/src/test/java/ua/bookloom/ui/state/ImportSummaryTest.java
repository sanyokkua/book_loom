package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.CsvProblem;
import ua.bookloom.api.pipeline.GlossaryImportReport;
import ua.bookloom.ui.i18n.LocaleProvider;
import ua.bookloom.ui.i18n.Messages;

/** The words a CSV import's notice is made of: the lines it could not read as spans, and each cause once. */
class ImportSummaryTest {

    private static ImportSummary in(final String language) {
        return new ImportSummary(new Messages((LocaleProvider) () -> Locale.forLanguageTag(language)));
    }

    // IF 5,000 rows typed "person" were reported as 5,000 line numbers and nothing else, THEN the person would see a
    // wall of numbers and not learn the one thing to fix (G1-4); the lines read as a span and the cause is named once
    // with the values the file may use (the Ukrainian thousands are grouped with a no-break space).
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "en|Imported 0 rows. 5,000 lines could not be read: 2–5001. The type must be one of: character, place,"
                        + " term, title, other.",
                "uk|Імпортовано 0 рядків. Не вдалося прочитати 5 000 рядків: 2–5001. Тип має бути одним із:"
                        + " character, place, term, title, other."
            })
    void of_everyRowOfAnUnknownType_namesTheCauseOnceAndTheLinesAsASpan(final String language, final String expected) {
        final List<Integer> lines = IntStream.rangeClosed(2, 5001).boxed().toList();

        final String summary =
                in(language).of(new GlossaryImportReport(0, lines, List.of(), 0, Set.of(CsvProblem.UNKNOWN_TYPE)));

        assertThat(summary).isEqualTo(expected);
    }

    // IF scattered bad lines were listed without end, THEN a long file would bury the cause under numbers; at most
    // ten spans are named, then how many more, and every cause once.
    @Test
    void of_scatteredLinesOfSeveralCauses_namesTenSpansAndEachCauseOnce() {
        final List<Integer> lines =
                IntStream.rangeClosed(1, 12).map(i -> i * 3).boxed().toList();

        final String summary = in("en").of(new GlossaryImportReport(
                0,
                lines,
                List.of(),
                0,
                Set.of(CsvProblem.UNKNOWN_GENDER, CsvProblem.FIELD_COUNT, CsvProblem.UNKNOWN_LOCKED)));

        assertThat(summary)
                .isEqualTo("Imported 0 rows. 12 lines could not be read: 3, 6, 9, 12, 15, 18, 21, 24, 27, 30 and 2"
                        + " more. A row does not hold one value for each column. The gender must be one of: female,"
                        + " male, neuter, unknown. Locked must be true or false.");
    }
}
