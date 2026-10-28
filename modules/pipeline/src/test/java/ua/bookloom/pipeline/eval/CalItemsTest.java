package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ua.bookloom.pipeline.eval.CalItems.Item;
import ua.bookloom.pipeline.eval.CalItems.Kind;

class CalItemsTest {

    private static final int LONG_WORDS = 30;

    @Test
    void items_calOne_hasSixtyItemsAndTheRequiredMix() {
        final CalItems cal = CalItems.loadBundled(CalItems.CAL_1);

        assertThat(cal.items()).hasSize(60);
        assertThat(count(cal, Item::kind)).isEqualTo(Map.of(Kind.SINGLE, 30L, Kind.PAIR, 30L));
        assertThat(count(cal, Item::split)).isEqualTo(Map.of("few-shot", 10L, "dev", 25L, "test", 25L));
        assertThat(count(cal, Item::pair).get("ru-uk")).isGreaterThanOrEqualTo(20L);
        assertThat(cal.items().stream().filter(i -> i.words() > LONG_WORDS)).hasSizeGreaterThanOrEqualTo(10);
        assertThat(cal.items().stream().map(Item::id).distinct()).hasSize(60);
        assertThat(cal.items()).allSatisfy(i -> assertThat(i.translations()).hasSize(i.kind() == Kind.PAIR ? 2 : 1));
        assertThat(unlabelledButNotListed(cal)).isEmpty();
    }

    @Test
    void items_calOne_mixesKindsAndPairsInEverySplit() {
        final CalItems cal = CalItems.loadBundled(CalItems.CAL_1);

        assertThat(cal.items().stream()
                        .map(i -> i.split() + "/" + i.kind() + "/" + i.pair())
                        .distinct())
                .hasSize(12);
    }

    @Test
    void items_judgeItem_carriesTheLanguagesTermsAndNeighbours() {
        final Item item = CalItems.loadBundled(CalItems.CAL_1).items().stream()
                .filter(i -> i.id().equals("cal-s12"))
                .findFirst()
                .orElseThrow();

        final JudgeItem judged = item.judgeItem();

        assertThat(judged.caseId()).isEqualTo("cal-s12");
        assertThat(judged.sourceLanguage()).isEqualTo("ru");
        assertThat(judged.targetLanguage()).isEqualTo("uk");
        assertThat(judged.neighbours()).hasSize(2);
    }

    private static <K> Map<K, Long> count(final CalItems cal, final Function<Item, K> key) {
        return cal.items().stream().collect(Collectors.groupingBy(key, Collectors.counting()));
    }

    // Before labels.json exists nothing is unlabelled; afterwards every item is graded by both graders or listed.
    private static List<String> unlabelledButNotListed(final CalItems cal) {
        return CalItems.loadBundledLabels(CalItems.CAL_1)
                .map(labels -> cal.items().stream()
                        .map(Item::id)
                        .filter(id -> !labels.hasBothGraders(id)
                                && !labels.unlabelled().contains(id))
                        .toList())
                .orElse(List.of());
    }
}
