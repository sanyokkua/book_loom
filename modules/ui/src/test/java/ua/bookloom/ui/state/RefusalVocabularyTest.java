package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.ConsistencySummary;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/** A refusal rule is worded the same wherever it shows: in the export's lines and in the call panel's detail. */
class RefusalVocabularyTest {

    private final Injector injector = UiTestInjector.create(Locale.ENGLISH);
    private final Messages messages = injector.getInstance(Messages.class);

    private String exportLine(final String rule) {
        final ConsistencySummary summary = new ConsistencySummary(
                ConsistencySummary.Status.RAN,
                0,
                0,
                Map.of(),
                0,
                new ConsistencyChecks(0, 0, 0, Map.of(rule, 1), 0),
                List.of());
        return String.join("\n", ExportReportLines.consistency(messages, summary));
    }

    // IF the export said "quote marks" where the call panel said "quote marks not paired", THEN one refusal would read
    // as two different problems.
    @ParameterizedTest
    @CsvSource({
        "quotes,quote marks not paired",
        "sentences,sentences lost",
        "words,words lost",
        "dashes,dashes lost",
        "latin_run,English words left",
        "gate,refused by the checks",
        "control_chars,quote marks sent as control codes",
        "worse,scored worse"
    })
    void rule_inTheExportLinesAndTheCallDetail_isWordedAlike(final String rule, final String words) {
        assertThat(exportLine(rule)).contains("(" + words + ": 1)");
        assertThat(messages.code(MessageKey.LIVE_CALL_DETAIL, rule)).isEqualTo(words);
    }

    @Test
    void rule_unknownToTheCatalogue_isShownAsItsOwnCode() {
        assertThat(exportLine("brand_new_rule")).contains("(brand_new_rule: 1)");
    }
}
