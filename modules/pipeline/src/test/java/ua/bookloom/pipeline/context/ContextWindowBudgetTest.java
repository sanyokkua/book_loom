package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.api.project.TmEntry;
import ua.bookloom.pipeline.batch.BatchContext;
import ua.bookloom.pipeline.batch.BatchItem;
import ua.bookloom.pipeline.batch.BatchPromptBuilder;
import ua.bookloom.pipeline.chunk.Chunk;
import ua.bookloom.pipeline.chunk.ChunkPacker;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.memory.TmLookup;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.DraftPromptBuilder;
import ua.bookloom.pipeline.prompt.PromptBreakdown;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.run.ChunkBudget;

/**
 * A small model is never sent more than its window: for a unit full of glossary terms, a long summary and the dial's
 * preceding text, every real draft prompt plus the reply its chunk may need fits the window less the safety margin.
 */
class ContextWindowBudgetTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String PARAGRAPH =
            "Nobody in the harbour town had ever seen a ship like it, and the old men argued on the quay until dark. ";
    private static final String SUMMARY = "The harbour town waits for a ship that never came. ".repeat(12);
    private static final int PRECEDING_COUNT = 3;
    private static final int TERMS = 40;

    @ParameterizedTest
    @ValueSource(ints = {4096, 8192, 32768})
    void draftPrompt_anyWindow_neverExceedsWindowLessMarginAndReply(final int window) {
        final List<Segment> unit = unit();
        final List<GlossaryEntry> glossary = glossary();
        final ContextBudget budget = ChunkBudget.budget(FRAME, window);
        final int allowance = ChunkBudget.dynamicAllowance(FRAME, window);
        final DraftPromptBuilder builder = new DraftPromptBuilder(TEMPLATES, FRAME);
        int worst = 0;

        for (final Chunk chunk : ChunkPacker.pack(unit, "en", budget.chunkTokens(), 8)) {
            final int reply = budget.outputTokens(sourceTokens(chunk));
            for (final Segment segment : chunk.segments()) {
                final List<ChatMessage> messages =
                        draftMessages(builder, chunk, segment, glossary, allowance, earlierTargets(unit, segment));
                worst = Math.max(worst, PromptBreakdown.of(messages).total() + reply + ContextBudget.SAFETY_MARGIN);
            }
        }

        assertThat(worst).isLessThanOrEqualTo(window);
    }

    @ParameterizedTest
    @ValueSource(ints = {4096, 8192})
    void draftPrompt_memoryAndLexiconFillingTheirAllowance_stillFitsWindowLessMarginAndReply(final int window) {
        final List<Segment> unit = unit();
        final List<GlossaryEntry> glossary = glossary().subList(0, 2);
        final ContextBudget budget = ChunkBudget.budget(FRAME, window);
        final int allowance = ChunkBudget.dynamicAllowance(FRAME, window);
        final DraftPromptBuilder builder = new DraftPromptBuilder(TEMPLATES, FRAME);
        int worst = 0;

        for (final Chunk chunk : ChunkPacker.pack(unit, "en", budget.chunkTokens(), 50)) {
            final int reply = budget.outputTokens(sourceTokens(chunk));
            for (final Segment segment : chunk.segments()) {
                final ContextInputs inputs = new ContextInputs(
                        FRAME.styleSheet(),
                        SUMMARY,
                        PRECEDING_COUNT,
                        glossary,
                        earlierTargets(unit, segment),
                        allowance,
                        lexicon());
                final DraftContext context = ContextPackageAssembler.assemble(
                                chunk, segment, ContextFixtures.mask(segment, glossary), richMemory(), inputs)
                        .draftContext();
                final List<ChatMessage> messages = builder.messagesFor(segment, context, segment.masked());
                worst = Math.max(worst, PromptBreakdown.of(messages).total() + reply + ContextBudget.SAFETY_MARGIN);
            }
        }

        assertThat(worst).isLessThanOrEqualTo(window);
    }

    private static TmLookup richMemory() {
        final List<TmEntry> hints = new ArrayList<>();
        for (int index = 0; index < TERMS; index++) {
            hints.add(ContextFixtures.tmEntry(PARAGRAPH + "Memory " + index, "Пам'ятний абзац про гавань " + index));
        }
        return new TmLookup(null, hints, List.of());
    }

    private static List<LexiconEntry> lexicon() {
        final List<LexiconEntry> entries = new ArrayList<>();
        for (final String word : List.of("harbour", "town", "ship", "quay", "men", "dark", "argued", "seen", "ever")) {
            entries.add(new LexiconEntry(
                    "p1", word, List.of(new LexiconEntry.Rendering("рендеринг " + word, 3)), null, null));
        }
        return entries;
    }

    @Test
    void draftPrompt_tinyWindowWithRichContext_cutsContextWithinItsAllowance() {
        final List<Segment> unit = unit();
        final List<GlossaryEntry> glossary = glossary();
        final Chunk chunk = new Chunk(unit.getFirst().unit(), List.of(unit.get(5)), false);

        final int allowance = ChunkBudget.dynamicAllowance(FRAME, 2048);
        final int small = contextTokens(chunk, glossary, allowance, unit);
        final int large = contextTokens(chunk, glossary, ChunkBudget.dynamicAllowance(FRAME, 8192), unit);

        assertThat(small).isLessThan(large).isLessThanOrEqualTo(allowance);
    }

    @Test
    void staticPrefix_draftSystemMessage_fitsTheReservedPrefix() {
        final int actual = PromptBreakdown.of(new DraftPromptBuilder(TEMPLATES, FRAME).messagesFor(unit().getFirst()))
                .systemTokens();

        assertThat(ChunkBudget.staticPrefix(FRAME)).isGreaterThanOrEqualTo(actual);
    }

    @Test
    void staticPrefix_batchSystemMessage_fitsTheReservedPrefix() {
        final int actual = PromptBreakdown.of(new BatchPromptBuilder(TEMPLATES, FRAME)
                        .messagesFor(BatchContext.empty(), List.of(new BatchItem("1", "He left."))))
                .systemTokens();

        assertThat(ChunkBudget.staticPrefix(FRAME)).isGreaterThanOrEqualTo(actual);
    }

    @Test
    void messagesFor_twoCallsWithDifferentSegmentsAndContext_shareTheSameSystemMessage() {
        final DraftPromptBuilder builder = new DraftPromptBuilder(TEMPLATES, FRAME);
        final List<Segment> unit = unit();

        final String first = builder.messagesFor(unit.get(0)).getFirst().content();
        final String second = builder.messagesFor(
                        unit.get(7),
                        new DraftContext(
                                List.of("Один.", "Два."), SUMMARY, List.of("Hale → Гейл (character, male)"), List.of()))
                .getFirst()
                .content();

        assertThat(second).isEqualTo(first);
    }

    private static int contextTokens(
            final Chunk chunk, final List<GlossaryEntry> glossary, final int allowance, final List<Segment> unit) {
        final Segment segment = chunk.segments().getFirst();
        final DraftContext context = ContextPackageAssembler.assemble(
                        chunk,
                        segment,
                        ContextFixtures.mask(segment, glossary),
                        ContextFixtures.NO_MEMORY,
                        inputs(glossary, allowance, earlierTargets(unit, segment)))
                .draftContext();
        return TokenEstimator.estimate(
                String.join("\n", context.glossaryLines())
                        + context.summary()
                        + String.join("\n", context.precedingTargets()),
                null);
    }

    private static List<ChatMessage> draftMessages(
            final DraftPromptBuilder builder,
            final Chunk chunk,
            final Segment segment,
            final List<GlossaryEntry> glossary,
            final int allowance,
            final List<String> earlier) {
        final DraftContext context = ContextPackageAssembler.assemble(
                        chunk,
                        segment,
                        ContextFixtures.mask(segment, glossary),
                        ContextFixtures.NO_MEMORY,
                        inputs(glossary, allowance, earlier))
                .draftContext();
        return builder.messagesFor(segment, context, segment.masked());
    }

    private static ContextInputs inputs(
            final List<GlossaryEntry> glossary, final int allowance, final List<String> earlier) {
        return new ContextInputs(FRAME.styleSheet(), SUMMARY, PRECEDING_COUNT, glossary, earlier, allowance);
    }

    private static int sourceTokens(final Chunk chunk) {
        return chunk.segments().stream()
                .mapToInt(segment -> TokenEstimator.estimate(segment.masked(), "en"))
                .sum();
    }

    private static List<String> earlierTargets(final List<Segment> unit, final Segment segment) {
        return unit.subList(0, unit.indexOf(segment)).stream()
                .map(earlier -> "Перекладений абзац про гавань і старий корабель, номер " + earlier.order() + ".")
                .toList();
    }

    private static List<Segment> unit() {
        final List<Segment> segments = new ArrayList<>();
        for (int order = 0; order < 30; order++) {
            segments.add(ContextFixtures.segment(order, PARAGRAPH + termsIn(order)));
        }
        return segments;
    }

    private static String termsIn(final int order) {
        return "Hale" + order % TERMS + " met Moreau" + (order + 1) % TERMS + ".";
    }

    private static List<GlossaryEntry> glossary() {
        final List<GlossaryEntry> entries = new ArrayList<>();
        for (int index = 0; index < TERMS; index++) {
            entries.add(ContextFixtures.entry("Hale" + index, "Гейл" + index, TermType.CHARACTER, Gender.MALE, false));
            entries.add(
                    ContextFixtures.entry("Moreau" + index, "Моро" + index, TermType.CHARACTER, Gender.MALE, false));
        }
        return entries;
    }
}
