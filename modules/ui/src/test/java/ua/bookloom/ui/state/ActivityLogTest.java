package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.ui.i18n.MessageKey;

/** The activity log's entry for a kept chapter summary: the `sum` kind, naming the chapter and never its text. */
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
}
