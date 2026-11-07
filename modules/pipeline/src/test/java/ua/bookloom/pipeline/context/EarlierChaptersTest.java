package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.llm.ModelWindow;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.pipeline.index.IndexTestBooks;
import ua.bookloom.pipeline.project.ChapterStandings;

/** Block 8's earlier chapters (design D10, "Use"): every finished chapter before the focus's, oldest first, in budget. */
class EarlierChaptersTest {

    private static final String HEADING =
            "[Earlier chapters] The story so far, in the book's target language — context only; do NOT translate.";
    private static final String FILLER = "Туман лежал над рекой, и лодки стояли у берега.";
    private static final int CHAPTER_PARAGRAPHS = 20;
    private static final String PROJECT = "p1";
    private static final String LONG_SUMMARY = "Мірон повернувся до міста над рікою, знайшов старий ліхтар у вежі"
            + " і зрозумів, що хтось стежить за ним від самого берега.";

    // Twenty fillers are 340 tokens, so each chapter is over the 300-token minimum and none joins another.
    private static Document book(final int chapters) {
        final String[] fillers =
                IntStream.range(0, CHAPTER_PARAGRAPHS).mapToObj(index -> FILLER).toArray(String[]::new);
        return IndexTestBooks.document(IntStream.rangeClosed(1, chapters)
                .mapToObj(number -> IndexTestBooks.unit("ch" + number, UnitRole.BODY, fillers))
                .toArray(Unit[]::new));
    }

    // Every chapter of the test book holds the same paragraphs, so one hash is the fresh one for all of them.
    private static final String FRESH_HASH =
            ChapterStandings.sourceHash(book(1).units().getFirst().segments());

    private static ChapterSummary summary(final String chapterKey, final String text) {
        return summary(chapterKey, text, FRESH_HASH);
    }

    private static ChapterSummary summary(final String chapterKey, final String text, final String sourceHash) {
        return new ChapterSummary(
                PROJECT,
                chapterKey,
                chapterKey.substring(0, chapterKey.indexOf(':')),
                0,
                CHAPTER_PARAGRAPHS,
                text,
                sourceHash);
    }

    private static PromptPlan draftAt(
            final Document document, final BookBrief brief, final String segmentId, final List<ChapterSummary> stored) {
        final RunFrame frame = FrameFixtures.frame(document, brief, ModelProfile.defaults());
        final int window = ModelWindow.of(ModelProfile.defaults().context(), null);
        final TranslatePlanner planner = new TranslatePlanner(
                FrameFixtures.BUILDER,
                frame,
                ModelProfile.defaults(),
                () -> window,
                document.units(),
                () -> Result.ok(stored),
                brief::summariesOn);
        final Focus focus = TranslatePlanner.focusOf(segmentId, FILLER);
        final TranslateContext context = new TranslateContext(
                Window.of(focus), FrameFixtures.emptyMap(brief), List.of(), Map.of(), planner.earlierChapters(focus));
        return Objects.requireNonNull(planner.plan(CallKind.DRAFT, context, focus, KindExtra.NONE)
                        .data())
                .plan();
    }

    private static String scene(final PromptPlan plan) {
        return plan.blocks().stream()
                .filter(block -> block.slot().equals(BlockSlots.SCENE))
                .map(Block::text)
                .findFirst()
                .orElse("");
    }

    @Test
    void block8_chapter3_carriesChapters1And2() {
        // Saved in reverse, with a summary whose chapter the book no longer has.
        final List<ChapterSummary> stored = List.of(
                summary("ch2:0", "Другий розділ."), summary("ch1:0", "Перший розділ."), summary("gone:4", "Зник."));

        final PromptPlan plan = draftAt(book(3), FrameFixtures.brief("ru", "uk"), "ch3:0", stored);

        assertThat(scene(plan)).isEqualTo(HEADING + "\nChapter 1: Перший розділ.\nChapter 2: Другий розділ.");
        assertThat(plan.chapterSummaryKeys()).containsExactly("ch1:0", "ch2:0");
    }

    @Test
    void block8_overBudget_dropsOldestKeepsNewest() {
        final List<ChapterSummary> stored = new ArrayList<>();
        IntStream.rangeClosed(1, 29).forEach(number -> stored.add(summary("ch" + number + ":0", LONG_SUMMARY)));

        final PromptPlan plan = draftAt(book(30), FrameFixtures.brief("ru", "uk"), "ch30:0", stored);

        final List<String> keys = plan.chapterSummaryKeys();
        assertThat(keys).hasSizeBetween(2, 28).doesNotContain("ch1:0").endsWith("ch28:0", "ch29:0");
        assertThat(stored.stream().map(ChapterSummary::chapterKey).toList()).endsWith(keys.toArray(String[]::new));
        assertThat(scene(plan)).contains("Chapter 29: ").doesNotContain("Chapter 1: ");
    }

    @Test
    void block8_staleSummary_passedOver() {
        final List<ChapterSummary> stored = List.of(
                summary("ch1:0", "Перший розділ.", "written-from-other-text"), summary("ch2:0", "Другий розділ."));

        final PromptPlan plan = draftAt(book(3), FrameFixtures.brief("ru", "uk"), "ch3:0", stored);

        assertThat(scene(plan)).isEqualTo(HEADING + "\nChapter 2: Другий розділ.");
        assertThat(plan.chapterSummaryKeys()).containsExactly("ch2:0");
    }

    @Test
    void block8_firstChapter_none() {
        final List<ChapterSummary> stored = List.of(summary("ch1:0", "Перший розділ."));

        final PromptPlan plan = draftAt(book(3), FrameFixtures.brief("ru", "uk"), "ch1:5", stored);

        assertThat(plan.userText()).doesNotContain("[Earlier chapters]");
        assertThat(plan.chapterSummaryKeys()).isEmpty();
    }

    @Test
    void block8_switchOff_none() {
        final List<ChapterSummary> stored = List.of(summary("ch1:0", "Перший розділ."));
        final BookBrief off = FrameFixtures.brief("ru", "uk").withChapterSummaries(false);

        final PromptPlan plan = draftAt(book(3), off, "ch2:0", stored);

        assertThat(plan.userText()).doesNotContain("[Earlier chapters]");
        assertThat(plan.chapterSummaryKeys()).isEmpty();
    }

    @Test
    void block8_storeUnreadable_none() {
        final Document document = book(3);
        final BookBrief brief = FrameFixtures.brief("ru", "uk");
        final RunFrame frame = FrameFixtures.frame(document, brief, ModelProfile.defaults());
        final TranslatePlanner planner = new TranslatePlanner(
                FrameFixtures.BUILDER,
                frame,
                ModelProfile.defaults(),
                () -> ModelWindow.DEFAULT_WINDOW,
                document.units(),
                () -> Result.err(AppError.of(ErrorCode.internal, "Store failed", "The summaries could not be read.")),
                brief::summariesOn);

        assertThat(planner.earlierChapters(TranslatePlanner.focusOf("ch3:0", FILLER)))
                .isEmpty();
    }
}
