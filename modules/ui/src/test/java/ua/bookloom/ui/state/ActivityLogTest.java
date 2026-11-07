package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.api.pipeline.ProofreadWindowRead;
import ua.bookloom.api.project.ProofreadOutcome;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The activity log's entries for a kept chapter summary and a read proofread window: the `sum` and `proof` kinds,
 * naming the chapter or the segments and never their text.
 */
class ActivityLogTest extends LiveSessionTestBase {

    private static final String TEXT = "Він знайшов листа. Вона поїхала до міста.";

    // IF a kept summary left no `sum` line naming its chapter, THEN the log would not show which chapters the later
    // drafts are told about; the summary's text is book text and stays out of the entry.
    @Test
    void summaryKept_kindSumNamesChapter() {
        final RunSession session = session();

        session.onEvent(new ChapterSummaryKept("unit-3#0", 3, 2, TEXT));
        tick(session);

        assertThat(logEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo(LogKind.SUMMARY);
            assertThat(entry.kind().tag()).isEqualTo("sum");
            assertThat(entry.messageKey()).isEqualTo(MessageKey.LOG_CHAPTER_SUMMARY_KEPT);
            assertThat(entry.args()).containsExactly("3");
            assertThat(entry.technical()).isFalse();
        });
    }
    // IF a read proofread window left no `proof` line naming where it read, THEN the log could not say what the last
    // pass touched; the texts are book text and stay out of the entry.
    @Test
    void windowRead_kindProofNamesTheSegment() {
        final RunSession session = session();

        session.onEvent(new ProofreadWindowRead(
                1,
                List.of(new ProofreadItem(
                        "ch7.xhtml:42",
                        "ch7 · p42",
                        "Ліна сказав.",
                        "Ліна сказала.",
                        ProofreadOutcome.KEPT,
                        "",
                        List.of()))));
        tick(session);

        assertThat(logEntries()).singleElement().satisfies(entry -> {
            assertThat(entry.kind()).isEqualTo(LogKind.PROOF);
            assertThat(entry.kind().tag()).isEqualTo("proof");
            assertThat(entry.messageKey()).isEqualTo(MessageKey.LOG_PROOFREAD);
            assertThat(entry.args()).containsExactly("ch7 · p42", "1", "0", "0", "0");
            assertThat(String.join(" ", entry.args())).doesNotContain("Ліна");
        });
    }
}
