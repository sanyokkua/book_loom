package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TestPlanners;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.Focus;
import ua.bookloom.pipeline.context.PromptPlan;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.context.WindowPair;
import ua.bookloom.pipeline.index.IndexTestBooks;

/** The batch prompt as really planned: its blocks, its byte-identical prefix and examples a run's parser would accept. */
class BatchPromptTest {

    private static final int SYSTEM_BUDGET_TOKENS = 875;
    private static final String ITEMS_OPEN = "<Items>\n";
    private static final String REPLY_OPEN = "</Items>\nReply:";
    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "Hale opened the door for the master."),
            new BatchItem("2", "1881"),
            new BatchItem("3", "⟦g0⟧Hello⟦g1⟧ there."));
    private static final List<String> IDS = List.of("x:1", "x:2", "x:3");
    private static final GlossaryEntry HALE =
            GlossaryEntry.of("h", "p1", "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false);
    private static final LexiconEntry MASTER = new LexiconEntry("p1", "master", List.of(), "господар", null, null);

    private static BookBrief brief(final String source, final String target) {
        return BookBrief.defaults(source).withLanguages(source, target);
    }

    private static TranslatePlanner planner(final String source, final String target) {
        return TestPlanners.planner(
                brief(source, target), IndexTestBooks.oneUnit("Hale opened the door."), ModelProfile.defaults());
    }

    private static TranslateContext context(
            final BookBrief brief,
            final List<WindowPair> previous,
            @Nullable final String next,
            final List<GlossaryEntry> glossary,
            final List<LexiconEntry> terms,
            final List<String> memory,
            final List<String> keyTerms) {
        return new TranslateContext(
                new Window(previous, new Focus(List.of(), Map.of()), next),
                BookMap.of(glossary, terms, brief),
                memory,
                Map.of(),
                keyTerms);
    }

    private static TranslateContext empty() {
        return context(brief("en", "uk"), List.of(), null, List.of(), List.of(), List.of(), List.of());
    }

    private static TranslateContext full() {
        return context(
                brief("en", "uk"),
                List.of(new WindowPair("x:0", "He left.", "Він пішов.")),
                "She smiled.",
                List.of(HALE),
                List.of(),
                List.of("He left. → Він пішов."),
                List.of());
    }

    private static PromptPlan plan(
            final TranslatePlanner planner, final TranslateContext context, final List<BatchItem> items) {
        final ContextBuilder.FittedPlan fitted = Objects.requireNonNull(new BatchPromptBuilder(planner)
                .plan(context, items, IDS.subList(0, items.size()))
                .data());
        assertThat(fitted.keptItems()).isEqualTo(items.size());
        return fitted.plan();
    }

    private static PromptPlan plan(final TranslateContext context, final List<BatchItem> items) {
        return plan(planner("en", "uk"), context, items);
    }

    @Test
    void plan_fullContext_carriesEveryBlockAndTheNumberedItems() {
        final String user = plan(full(), ITEMS).userText();

        assertThat(user)
                .contains("[Glossary", "Hale → Гейл (character, male)", "[Previous pairs")
                .contains("Source: He left.\nTranslation: Він пішов.", "[Next item after the batch", "She smiled.")
                .contains("[Earlier decisions — keep consistent]\nHe left. → Він пішов.")
                .contains("<s id=\"1\">Hale opened the door for the master.</s>", "<s id=\"2\">1881</s>")
                .contains("<s id=\"3\">⟦g0⟧Hello⟦g1⟧ there.</s>")
                .doesNotContain("{{", "}}", "[Book so far", "[Characters");
    }

    @Test
    void plan_batchWithoutToken_remindsOfNoMark() {
        final List<BatchItem> plain = ITEMS.subList(0, 2);

        final PromptPlan plan = plan(full(), plain);

        assertThat(plan.userText()).doesNotContain("⟦");
        assertThat(plan(full(), ITEMS).userText()).endsWith("Keep every ⟦…⟧ mark exactly once.");
    }

    @Test
    void plan_lexiconAndKeyTerms_carryTheRenderingsAndTheClosedList() {
        final TranslateContext context = context(
                brief("en", "uk"), List.of(), null, List.of(), List.of(MASTER), List.of(), List.of("master", "imp"));

        final String user = plan(context, ITEMS).userText();

        assertThat(user)
                .contains("[Established renderings of recurring terms", "master → господар")
                .contains("[Key terms", "master, imp")
                .doesNotContain("{{", "}}");
    }

    @Test
    void plan_noLexiconAndNoKeyTerms_leavesBothBlocksOut() {
        assertThat(plan(empty(), ITEMS).userText()).doesNotContain("[Established renderings", "[Key terms");
    }

    @Test
    void plan_emptyContext_leavesEveryOptionalBlockOut() {
        final String user = plan(empty(), List.of(new BatchItem("1", "1881"))).userText();

        assertThat(user)
                .doesNotContain("[Glossary", "[Recently", "[Previous pairs", "[Earlier decisions", "[Next item")
                .doesNotContain("[Defects", "[Rejected reply", "(none");
    }

    @Test
    void plan_twoBatches_shareTheSameSystemBytes() {
        final TranslatePlanner planner = planner("en", "uk");

        final String first = plan(planner, full(), ITEMS).systemText();
        final String second = plan(planner, empty(), List.of(ITEMS.getLast())).systemText();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void plan_system_carriesOneLanguageRulesSectionAndStaysWithinTheBudget() {
        final String system = plan(empty(), ITEMS).systemText();

        assertThat(system).containsOnlyOnce("[Language rules: English -> Ukrainian]");
        assertThat(TokenEstimator.estimate(system, "en")).isLessThanOrEqualTo(SYSTEM_BUDGET_TOKENS);
    }

    @Test
    void plan_pairExample_hasABatchWithANumberOnlyLine() {
        final String system = plan(empty(), ITEMS).systemText();

        assertThat(system).contains("<s id=\"2\">1881</s>", "{\"id\":\"2\",\"target\":\"1881\"}");
    }

    @Test
    void plan_pairWithoutExamples_showsNoBatchExample() {
        final TranslatePlanner german = planner("en", "de");

        final String system = plan(
                        german,
                        context(brief("en", "de"), List.of(), null, List.of(), List.of(), List.of(), List.of()),
                        ITEMS)
                .systemText();

        assertThat(system).doesNotContain(ITEMS_OPEN);
    }

    @Test
    void plan_everyShownExample_isAReplyTheBatchParserAcceptsCleanly() {
        final BatchReplyParser parser = new BatchReplyParser(new ObjectMapper());
        final String system = plan(empty(), ITEMS).systemText();

        final List<Example> examples = examplesIn(system);

        assertThat(examples).hasSize(1);
        assertThat(examples)
                .allSatisfy(example -> assertThat(parser.parse(example.reply().strip(), itemsOf(example), "en", "uk")
                                .isClean())
                        .as(example.reply())
                        .isTrue());
    }

    private record Example(String items, String reply) {}

    private static List<BatchItem> itemsOf(final Example example) {
        return ITEM.matcher(example.items())
                .results()
                .map(item -> new BatchItem(item.group(1), item.group(2)))
                .toList();
    }

    // Each "<Items>…</Items>\nReply:…" block of the prompt: the item lines, and the reply up to a blank line.
    private static List<Example> examplesIn(final String system) {
        final List<Example> found = new ArrayList<>();
        for (int open = system.indexOf(ITEMS_OPEN); open >= 0; open = system.indexOf(ITEMS_OPEN, open + 1)) {
            final int itemsStart = open + ITEMS_OPEN.length();
            final int itemsEnd = system.indexOf(REPLY_OPEN, itemsStart);
            if (itemsEnd < 0) {
                break;
            }
            final int replyStart = itemsEnd + REPLY_OPEN.length();
            final int blank = system.indexOf("\n\n", replyStart);
            found.add(new Example(
                    system.substring(itemsStart, itemsEnd),
                    system.substring(replyStart, blank < 0 ? system.length() : blank)));
        }
        return found;
    }

    @Test
    void requestFor_json_asksForTheSchemaAndCapsTheReplyAtThePlansWindow() {
        final TranslatePlanner planner = planner("en", "uk");
        final PromptPlan plan = plan(planner, empty(), ITEMS);

        final ChatRequest request = new BatchPromptBuilder(planner).requestFor(plan, ITEMS, false);

        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().name()).isEqualTo("draft-batch-json");
        assertThat(request.callKind()).isEqualTo(CallKind.DRAFT);
        assertThat(request.contextWindow()).isEqualTo(plan.contextSize());
        // The reserve, never below the short-source floor a one-word reply once needed for its envelope.
        assertThat(request.maxOutputTokens()).isEqualTo(Math.max(plan.maxOutputTokens(), 128));
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().getLast().content()).isEqualTo(plan.userText());
    }

    @Test
    void requestFor_moreItems_getsALargerCap() {
        final TranslatePlanner planner = planner("en", "uk");
        final BatchPromptBuilder builder = new BatchPromptBuilder(planner);

        final List<BatchItem> long1 = List.of(new BatchItem("1", "The tide came in. ".repeat(40)));
        final List<BatchItem> long3 = List.of(
                long1.getFirst(),
                new BatchItem("2", "The gulls cried. ".repeat(40)),
                new BatchItem("3", "Night fell. ".repeat(40)));
        final ChatRequest one = builder.requestFor(plan(planner, empty(), long1), long1, false);
        final ChatRequest three = builder.requestFor(plan(planner, empty(), long3), long3, false);

        assertThat(three.maxOutputTokens()).isGreaterThan(one.maxOutputTokens());
    }
}
