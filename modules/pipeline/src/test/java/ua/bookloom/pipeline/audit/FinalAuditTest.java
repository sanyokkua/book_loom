package ua.bookloom.pipeline.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.eval.ExportedBooks;

/** The audit-only checks: a lost pair of negations and the odd translation of a repeated line. */
class FinalAuditTest {

    private static final String LOST_TWO = "Он не знал, что никто не придёт, и ничего не сказал.";
    private static final String LOST_ONE = "Он не знал, что она придёт, и ничего не сказал.";
    private static final String CLEAN_TARGET = "Він знав, що вона прийде, і розповів їй усе.";

    @TempDir
    private Path tempDir;

    private Map<String, List<QaFinding>> scan(
            final String from, final String to, final String source, final String target) {
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Document original = ExportedBooks.open(documents, TestBooks.markdown(tempDir.resolve("a.md"), source));
        final Document written = ExportedBooks.open(documents, TestBooks.markdown(tempDir.resolve("b.md"), target));
        final BookBrief defaults = BookBrief.defaults(from);
        final BookBrief brief = new BookBrief(
                from,
                to,
                defaults.genre(),
                defaults.register(),
                defaults.voiceEra(),
                defaults.audience(),
                defaults.names(),
                defaults.foreignPassages(),
                defaults.footnotes(),
                defaults.units(),
                defaults.balance(),
                defaults.alsoTranslate(),
                defaults.dial());
        return FinalAudit.scan(
                new FinalAudit.Book(brief, original, List.of(), WordValidator.none()),
                ExportedBooks.recordsOf(original, written));
    }

    @Test
    void audit_twoNegationsLost_doubts() {
        final Map<String, List<QaFinding>> doubted = scan("ru", "uk", LOST_TWO, CLEAN_TARGET);

        assertThat(doubted.values().stream().flatMap(List::stream))
                .extracting(QaFinding::raisedBy)
                .contains("audit:negation");
    }

    @Test
    void audit_oneNegationLost_isNotDoubted() {
        final Map<String, List<QaFinding>> doubted =
                scan("ru", "uk", LOST_ONE, "Він не знав, що вона прийде, і нічого розповів.");

        assertThat(doubted.values().stream().flatMap(List::stream))
                .extracting(QaFinding::raisedBy)
                .doesNotContain("audit:negation");
    }

    @Test
    void audit_pairThatDoesNotCountNegations_isNotDoubted() {
        final Map<String, List<QaFinding>> doubted =
                scan("en", "uk", "He did not know that nobody would never come.", "Він знав, що прийдуть усі.");

        assertThat(doubted.values().stream().flatMap(List::stream))
                .extracting(QaFinding::raisedBy)
                .doesNotContain("audit:negation");
    }

    @Test
    void audit_repeatedLineTranslatedTwoWays_doubtsTheOddOne() {
        final String line = "The gate stayed shut until the morning came.";
        final Map<String, List<QaFinding>> doubted = scan(
                "en",
                "uk",
                String.join("\n\n", line, line, line),
                String.join(
                        "\n\n",
                        "Брама лишалася зачиненою до ранку.",
                        "Брама була замкнена до світанку.",
                        "Брама лишалася зачиненою до ранку."));

        assertThat(doubted).hasSize(1);
        assertThat(doubted.values().stream().flatMap(List::stream))
                .extracting(QaFinding::raisedBy)
                .containsExactly("audit:source-variants");
    }
}
