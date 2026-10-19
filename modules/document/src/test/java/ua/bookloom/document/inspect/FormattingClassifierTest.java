package ua.bookloom.document.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookStats.Formatting;

/**
 * {@link FormattingClassifier} (task 4.5): the tag→kind mapping for EPUB/FB2 masked fragments, and the
 * punctuation→kind mapping for Markdown masked fragments.
 */
class FormattingClassifierTest {

    @Test
    void classifyTag_openingItalicsTag_isItalics() {
        assertThat(FormattingClassifier.classifyTag("<i>")).isEqualTo(Formatting.ITALICS);
    }

    @Test
    void classifyTag_closingEmTag_isItalics() {
        assertThat(FormattingClassifier.classifyTag("</em>")).isEqualTo(Formatting.ITALICS);
    }

    @Test
    void classifyTag_strongTag_isBold() {
        assertThat(FormattingClassifier.classifyTag("<strong>")).isEqualTo(Formatting.BOLD);
    }

    @Test
    void classifyTag_anchorTag_isLinks() {
        assertThat(FormattingClassifier.classifyTag("<a href=\"x\">")).isEqualTo(Formatting.LINKS);
    }

    @Test
    void classifyTag_quoteTag_isQuotes() {
        assertThat(FormattingClassifier.classifyTag("<q>")).isEqualTo(Formatting.QUOTES);
    }

    @Test
    void classifyTag_codeTag_isCode() {
        assertThat(FormattingClassifier.classifyTag("<code>")).isEqualTo(Formatting.CODE);
    }

    @Test
    void classifyTag_selfClosingBreakTag_isLineBreaks() {
        assertThat(FormattingClassifier.classifyTag("<br/>")).isEqualTo(Formatting.LINE_BREAKS);
    }

    @Test
    void classifyTag_unrecognisedTag_isOther() {
        assertThat(FormattingClassifier.classifyTag("<span>")).isEqualTo(Formatting.OTHER);
    }

    @Test
    void classifyTag_comment_isNull() {
        assertThat(FormattingClassifier.classifyTag("<!-- note -->")).isNull();
    }

    @Test
    void classifyTag_processingInstruction_isNull() {
        assertThat(FormattingClassifier.classifyTag("<?pi?>")).isNull();
    }

    @Test
    void classifyTag_plainProtectedText_isNull() {
        assertThat(FormattingClassifier.classifyTag("locked term")).isNull();
    }

    @Test
    void classifyMarkdown_imageSpan_isOther() {
        assertThat(FormattingClassifier.classifyMarkdown("![alt](x.png)")).isEqualTo(Formatting.OTHER);
    }

    @Test
    void classifyMarkdown_linkOpenBracket_isLinks() {
        assertThat(FormattingClassifier.classifyMarkdown("[")).isEqualTo(Formatting.LINKS);
    }

    @Test
    void classifyMarkdown_linkCloseDestination_isLinks() {
        assertThat(FormattingClassifier.classifyMarkdown("](x)")).isEqualTo(Formatting.LINKS);
    }

    @Test
    void classifyMarkdown_codeSpanBacktick_isCode() {
        assertThat(FormattingClassifier.classifyMarkdown("`code`")).isEqualTo(Formatting.CODE);
    }

    @Test
    void classifyMarkdown_doubleAsterisk_isBold() {
        assertThat(FormattingClassifier.classifyMarkdown("**")).isEqualTo(Formatting.BOLD);
    }

    @Test
    void classifyMarkdown_doubleUnderscore_isBold() {
        assertThat(FormattingClassifier.classifyMarkdown("__")).isEqualTo(Formatting.BOLD);
    }

    @Test
    void classifyMarkdown_singleAsterisk_isItalics() {
        assertThat(FormattingClassifier.classifyMarkdown("*")).isEqualTo(Formatting.ITALICS);
    }

    @Test
    void classifyMarkdown_singleUnderscore_isItalics() {
        assertThat(FormattingClassifier.classifyMarkdown("_")).isEqualTo(Formatting.ITALICS);
    }

    @Test
    void classifyMarkdown_backslashLineBreak_isLineBreaks() {
        assertThat(FormattingClassifier.classifyMarkdown("\\")).isEqualTo(Formatting.LINE_BREAKS);
    }

    @Test
    void classifyMarkdown_trailingSpaces_isLineBreaks() {
        assertThat(FormattingClassifier.classifyMarkdown("  ")).isEqualTo(Formatting.LINE_BREAKS);
    }

    @Test
    void classifyMarkdown_unrecognisedText_isOther() {
        assertThat(FormattingClassifier.classifyMarkdown("~~strike~~")).isEqualTo(Formatting.OTHER);
    }
}
