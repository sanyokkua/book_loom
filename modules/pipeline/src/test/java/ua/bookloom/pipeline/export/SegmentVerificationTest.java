package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The placeholder markup compared once an image's description is set aside, and nothing else. */
class SegmentVerificationTest {

    // Each form an image description takes in a fragment is emptied; every other attribute or text is kept.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "<img src=\"fig1.png\" alt=\"Рисунок 1\"/>|<img src=\"fig1.png\" alt=\"\"/>",
                "<img alt='Figure 1' src='a.png'/>|<img alt='' src='a.png'/>",
                "<img src=\"a.png\" title=\"alt text\"/>|<img src=\"a.png\" title=\"alt text\"/>",
                "<img data-alt=\"x\" src=\"a.png\"/>|<img data-alt=\"x\" src=\"a.png\"/>",
                "<a href=\"#n\"><img alt=\"One\"/><img alt=\"Two\"/></a>|<a href=\"#n\"><img alt=\"\"/><img alt=\"\"/></a>",
                "![Figure 1](fig1.png)|![](fig1.png)",
                "[![Cover](c.png)](book.html)|[![](c.png)](book.html)",
                "<i>|<i>",
                "*|*"
            })
    void withoutAltText_fragment_emptiesOnlyTheDescription(final String fragment, final String expected) {
        assertThat(SegmentVerification.withoutAltText(fragment)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "<span class=\"x\">|<span>",
                "</i>|</i>",
                "<a:b.c-d e>|<a:b.c-d>",
                "<span|#5",
                "<1>|<1>",
                "<>|<>",
                "**|**",
                "plain text|#10"
            })
    void tagOf_fragment_namesTheTagOrItsSize(final String fragment, final String expected) {
        assertThat(SegmentVerification.tagOf(fragment)).isEqualTo(expected);
    }

    @Test
    void tagOf_longOpenAngleRunWithoutClose_isLinear() {
        final String fragment = "<a" + "<a".repeat(200_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(SegmentVerification.tagOf(fragment)).startsWith("#"));
    }
}
