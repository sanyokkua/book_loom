package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import javafx.scene.control.Label;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.ui.ReviewFixtures;

/** The desk names the check that doubts an accepted segment and the worst finding of a flagged one, in plain words. */
class ReviewDeskLabelsTest extends ReviewDeskTestBase {

    private static SegmentView doubted(final String raisedBy) {
        return ReviewFixtures.view(
                "ch05.xhtml:3",
                "ch5 · p03",
                SegmentStatus.ACCEPTED,
                List.of(new QaFinding("omission", Severity.MEDIUM, "a note", raisedBy)),
                null,
                null);
    }

    private String firstRowLine() {
        final Label line = fx(() -> (Label) rowList().lookup(".review-row-line"));
        return fx(line::getText);
    }

    // IF a "Worth a second look" row showed only its text, THEN the person would not know which check doubts it.
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "audit:negation, en, negation",
        "audit:terminal-mark, en, terminal mark",
        "audit:source-variants, en, source variants",
        "audit:name-variants, en, name variants",
        "audit:token-leak, en, leaked placeholder",
        "audit:function-word-residue, en, untranslated word",
        "audit:negation, uk, заперечення",
        "audit:terminal-mark, uk, кінцевий знак",
    })
    void secondLook_doubtingCheck_showsItsLabel(final String raisedBy, final String language, final String label)
            throws Exception {
        useLocale(Locale.forLanguageTag(language));
        openDeskWith(List.of(ReviewFixtures.wrongLanguage()), List.of(doubted(raisedBy)));

        chooseFilter(ReviewFilter.SUSPICIOUS);
        awaitFx(() -> firstRowLine().startsWith(label + " · "));

        assertThat(firstRowLine()).startsWith(label + " · ");
    }

    // IF the finding's label said "a check" for the audit's names, THEN the Why tab would not say what doubts it.
    @ParameterizedTest(name = "{0} in {1}")
    @CsvSource({
        "audit:negation, en, the negation check",
        "audit:negation, uk, перевірка заперечень",
        "audit:token-leak, en, the leaked-placeholder check",
    })
    void findingsTab_auditFinding_namesTheCheck(final String raisedBy, final String language, final String raiser)
            throws Exception {
        useLocale(Locale.forLanguageTag(language));
        openDeskWith(List.of(ReviewFixtures.wrongLanguage()), List.of(doubted(raisedBy)));
        chooseFilter(ReviewFilter.SUSPICIOUS);
        selectRow(0, "ch5 · p03");

        assertThat(textOf("review-findings")).contains(raiser);
    }

    // IF the row badged the residue finding, THEN a name that may be wrong would be hidden behind a lesser word.
    @ParameterizedTest(name = "{0}")
    @CsvSource({"en, a name may not match the glossary", "uk, ім'я може не відповідати глосарію"})
    void flaggedRow_residueAndNameFindings_badgesTheName(final String language, final String why) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        final SegmentView flagged = ReviewFixtures.view(
                "ch06.xhtml:1",
                "ch6 · p01",
                SegmentStatus.FLAGGED,
                List.of(
                        new QaFinding("language", Severity.MEDIUM, "left over", "function-word-residue"),
                        new QaFinding("glossary", Severity.MEDIUM, "name", "glossary")),
                null,
                null);

        openDeskWith(List.of(flagged), List.of());

        assertThat(firstRowLine()).startsWith(why);
    }

    // IF a flagged row and an accepted row carried the same word, THEN the second look would look like a flag.
    @ParameterizedTest
    @CsvSource({"en", "uk"})
    void flaggedRow_withAuditFinding_hasNoDoubtLabel(final String language) throws Exception {
        useLocale(Locale.forLanguageTag(language));
        final SegmentView flagged = ReviewFixtures.view(
                "ch06.xhtml:1",
                "ch6 · p01",
                SegmentStatus.FLAGGED,
                List.of(new QaFinding("omission", Severity.MEDIUM, "a note", "audit:negation")),
                null,
                null);

        openDeskWith(List.of(flagged), List.of());

        assertThat(firstRowLine()).doesNotStartWith("negation").doesNotStartWith("заперечення");
    }
}
