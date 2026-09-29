package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/** How the deterministic summary text is condensed: the oldest headings go first, then the rarest entries. */
class SummaryTextTest {

    private static final int TOKEN_LIMIT = 300;
    private static final int LONG_HEADINGS = 30;

    @Test
    void build_fewEntriesAndThirtyLongHeadings_dropsOldestHeadingsKeepingEveryEntry() {
        final List<String> entries = List.of("Hale → Гейл (character, male)", "Milton → Мілтон (place, unknown)");
        final List<String> headings = IntStream.range(0, LONG_HEADINGS)
                .mapToObj(index -> "Heading %02d of the long story of the lighthouse keeper".formatted(index))
                .toList();

        final SummaryText text = SummaryText.build(entries, headings);

        assertThat(TokenEstimator.estimate(text.text(), null)).isLessThanOrEqualTo(TOKEN_LIMIT);
        assertThat(text.text()).contains("Hale → Гейл", "Milton → Мілтон", "Heading 29 of the long story");
        assertThat(text.text()).doesNotContain("Heading 00 ");
        assertThat(text.entriesDropped()).isZero();
        assertThat(text.headingsDropped()).isPositive();
        assertThat(text.estimatedTokens()).isEqualTo(TokenEstimator.estimate(text.text(), null));
    }

    @Test
    void build_underTheLimit_keepsEverythingInOrder() {
        final SummaryText text = SummaryText.build(List.of("A → Б (term, unknown)"), List.of("One", "Two"));

        assertThat(text.text()).isEqualTo("A → Б (term, unknown)\nOne\nTwo");
        assertThat(text.headingsDropped()).isZero();
        assertThat(text.entriesDropped()).isZero();
    }

    @Test
    void build_nothingToSay_isEmptyText() {
        assertThat(SummaryText.build(List.of(), List.of()).text()).isEmpty();
    }
}
