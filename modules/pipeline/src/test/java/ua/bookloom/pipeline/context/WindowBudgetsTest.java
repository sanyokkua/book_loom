package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ReasoningControl;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/** The tier budgets and the drop order that fit one call's window (design D8, "Budgets"). */
class WindowBudgetsTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ru";
    private static final String TARGET = "uk";
    private static final int WINDOW = 16_384;
    // An invented ru→uk system part: the rules, the style sheet and the examples of a small model's run.
    private static final int SYSTEM_TOKENS = 585;

    /** Sizes a candidate the way the builder does: a fixed system part plus every text estimated in its language. */
    private record EstimateSizer(BookMap map) implements WindowBudgets.Sizer {

        @Override
        public List<MapEntry> map(final Window window) {
            return WindowMap.selectCounted(window, List.of(), map, List.of(), LexiconFilter.of(SOURCE, TARGET), TARGET);
        }

        @Override
        public int promptTokens(final Window window, final MapLines lines, final List<String> summaries) {
            final List<TokenEstimator.Message> messages = new ArrayList<>();
            window.previous().forEach(pair -> {
                messages.add(new TokenEstimator.Message(pair.maskedSource(), SOURCE));
                messages.add(new TokenEstimator.Message(String.valueOf(pair.maskedTarget()), TARGET));
            });
            window.focus()
                    .items()
                    .forEach(item -> messages.add(new TokenEstimator.Message(item.maskedSource(), SOURCE)));
            final @Nullable String next = window.nextSource();
            if (next != null) {
                messages.add(new TokenEstimator.Message(next, SOURCE));
            }
            Stream.of(lines.glossary(), lines.locked(), lines.suggested(), lines.lexicon(), summaries)
                    .flatMap(List::stream)
                    .forEach(line -> messages.add(new TokenEstimator.Message(line, TARGET)));
            return SYSTEM_TOKENS + TokenEstimator.estimateCall(messages, 0);
        }

        @Override
        public int reserve(final Focus focus) {
            return OutputReserve.of(100, focus.items().size(), 1.5, ReasoningControl.Kind.NONE, WINDOW);
        }
    }

    private static WindowBudgets.Budget budget(final WindowBudgets.Shares shares) {
        return new WindowBudgets.Budget(shares, WINDOW, SOURCE, TARGET);
    }

    private static BookMap map(final List<GlossaryEntry> entries, final List<LexiconEntry> terms) {
        final BookBrief brief = FrameFixtures.brief(SOURCE, TARGET);
        return BookMap.of(entries, terms, brief);
    }

    private static GlossaryEntry entry(
            final String term, final String target, final TermType type, final Gender gender) {
        return GlossaryEntry.of(term, PROJECT, term, target, type, gender, false);
    }

    // A text of exactly `length` characters that names `name` first, padded with Cyrillic letters.
    private static String text(final String name, final int length) {
        return name + " " + "д".repeat(length - name.length() - 1);
    }

    private static Focus focus(final String... sources) {
        final List<FocusItem> items = new ArrayList<>();
        for (int index = 0; index < sources.length; index++) {
            items.add(new FocusItem("ch2:" + (index + 10), sources[index]));
        }
        return new Focus(items, Map.of());
    }

    @Test
    void fit_typicalBatchOfFour_staysWhole() {
        final BookMap map = map(
                List.of(
                        entry("Ивар", "Івар", TermType.CHARACTER, Gender.MALE),
                        entry("Глафира", "Глафіра", TermType.CHARACTER, Gender.FEMALE),
                        entry("Зотов", "Зотов", TermType.CHARACTER, Gender.MALE),
                        entry("Милтон", "Мілтон", TermType.PLACE, Gender.UNKNOWN)),
                List.of());
        // Two previous pairs of 115 source tokens each, 230 in all; four items of 35 tokens; one earlier summary.
        final Window window = new Window(
                List.of(
                        new WindowPair("ch2:8", text("Тогда", 330), text("Тоді", 330)),
                        new WindowPair("ch2:9", text("Потом", 330), text("Потім", 330))),
                focus(text("Ивар", 100), text("Глафира", 100), text("Зотов", 100), text("Милтон", 100)),
                null);
        final List<String> summaries = List.of(text("Розділ", 300));

        final WindowBudgets.Fitted fitted =
                WindowBudgets.fit(budget(WindowBudgets.SMALL), window, summaries, new EstimateSizer(map));

        assertThat(fitted.promptTokens()).isBetween(1300, 1360);
        assertThat(fitted.window()).isEqualTo(window);
        assertThat(fitted.lines().glossary()).hasSize(4);
        assertThat(fitted.summaries()).isEqualTo(summaries);
        assertThat(fitted.keptItems()).isEqualTo(4);
    }

    @Test
    void fit_tooManyPrevious_cutsOldestFirst() {
        // 660 Cyrillic characters are 230 source tokens, so two are 460, over the small tier's 300.
        final Window window = new Window(
                List.of(
                        new WindowPair("ch2:8", text("Тогда", 660), text("Тоді", 660)),
                        new WindowPair("ch2:9", text("Потом", 660), text("Потім", 660))),
                focus(text("Утром", 100)),
                text("Вечером", 100));

        final WindowBudgets.Fitted fitted = WindowBudgets.fit(
                budget(WindowBudgets.SMALL), window, List.of(), new EstimateSizer(map(List.of(), List.of())));

        assertThat(fitted.window().previous()).extracting(WindowPair::segmentId).containsExactly("ch2:9");
        assertThat(fitted.window().nextSource()).isEqualTo(text("Вечером", 100));
    }

    @Test
    void fit_pinnedNameInWindow_neverDropped() {
        final BookMap map = map(
                List.of(
                        entry("Зотов", "Зотов", TermType.CHARACTER, Gender.MALE),
                        GlossaryEntry.of("Милтон", PROJECT, "Милтон", null, TermType.PLACE, Gender.UNKNOWN, false)
                                .withSuggestedTarget("Мілтон")),
                List.of(LexiconEntry.of(PROJECT, "фонарь").withChosen("ліхтар")));
        final Window window = new Window(
                List.of(
                        new WindowPair("ch2:8", text("Тогда", 200), text("Тоді", 200)),
                        new WindowPair("ch2:9", text("Потом", 200), text("Потім", 200))),
                focus("Зотов нёс фонарь к Милтон.", text("Утром", 100)),
                text("Вечером", 100));
        // A ceiling of one token: everything that may go goes.
        final WindowBudgets.Shares floor = new WindowBudgets.Shares(300, 600, 150, 600, 1);

        final WindowBudgets.Fitted fitted =
                WindowBudgets.fit(budget(floor), window, List.of("Раньше.", "Недавно."), new EstimateSizer(map));

        assertThat(fitted.lines().glossary()).containsExactly("Зотов → Зотов (character, male)");
        assertThat(fitted.lines().suggested()).isEmpty();
        assertThat(fitted.lines().lexicon()).isEmpty();
        assertThat(fitted.window().previous()).isEmpty();
        assertThat(fitted.window().nextSource()).isNull();
        assertThat(fitted.summaries()).isEmpty();
        assertThat(fitted.keptItems()).isEqualTo(1);
        assertThat(fitted.window().focus().segmentIds()).containsExactly("ch2:10");
    }

    // A prompt over the window (not the ceiling) drops the next source first and stops as soon as it fits.
    @Test
    void fit_overTheWindow_dropsNextFirstAndStops() {
        final Window window = new Window(
                List.of(new WindowPair("ch2:9", text("Потом", 200), text("Потім", 200))),
                focus(text("Утром", 100)),
                text("Вечером", 140));
        final WindowBudgets.Sizer sizer = new EstimateSizer(map(List.of(), List.of()));
        final int whole = sizer.promptTokens(window, MapLines.NONE, List.of());
        final int withoutNext = whole - TokenEstimator.estimate(text("Вечером", 140), SOURCE);
        final int reserve = sizer.reserve(window.focus());
        final int contextWindow = withoutNext + reserve + 500;

        final WindowBudgets.Fitted fitted = WindowBudgets.fit(
                new WindowBudgets.Budget(WindowBudgets.LARGE, contextWindow, SOURCE, TARGET), window, List.of(), sizer);

        assertThat(fitted.window().nextSource()).isNull();
        assertThat(fitted.window().previous()).hasSize(1);
        assertThat(fitted.promptTokens()).isEqualTo(withoutNext);
    }

    @ParameterizedTest
    @CsvSource({"SMALL,600,3500", "LARGE,1000,6000"})
    void currentPart_tier_isTheTiersBudget(final ModelProfile.Tier tier, final int currentPart, final int ceiling) {
        assertThat(WindowBudgets.currentPart(tier)).isEqualTo(currentPart);
        assertThat(WindowBudgets.of(tier).promptCeiling()).isEqualTo(ceiling);
    }
}
