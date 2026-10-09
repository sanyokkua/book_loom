package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.batch.BatchContext.Pair;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The batch prompt as really built: its blocks, its byte-identical prefix and examples a run's parser would accept. */
class BatchPromptTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final int SYSTEM_BUDGET_TOKENS = 875;
    private static final Pattern EXAMPLE = Pattern.compile(
            "<Items>\\n((?:<s id=\"\\d+\">.*</s>\\n)+)</Items>\\nReply:(.*?)(?:\\n\\n|\\z)", Pattern.DOTALL);
    private static final Pattern ITEM = Pattern.compile("<s id=\"(\\d+)\">(.*)</s>");
    private static final List<BatchItem> ITEMS = List.of(
            new BatchItem("1", "She opened the door."),
            new BatchItem("2", "1881"),
            new BatchItem("3", "⟦g0⟧Hello⟦g1⟧ there."));

    private static BatchPromptBuilder builder(final String source, final String target) {
        return new BatchPromptBuilder(
                TEMPLATES,
                new CallFrame(source, target, StyleSheet.from(BookBrief.defaults(source)), ForeignPassagePolicy.KEEP));
    }

    private static BatchContext fullContext() {
        return new BatchContext(
                new DraftContext(List.of(), "Hale searches.", List.of("Hale → Гейл (character, male)"), List.of()),
                List.of(new Pair("He left.", "Він пішов.")),
                "She smiled.",
                List.of("Hale — male"));
    }

    @Test
    void messagesFor_fullContext_carriesEveryBlockAndTheNumberedItems() {
        final String user =
                builder("en", "uk").messagesFor(fullContext(), ITEMS).get(1).content();

        assertThat(user)
                .contains("[Book so far", "[Glossary", "[Characters in these items", "[Previous pairs")
                .contains("Source: He left.\nTranslation: Він пішов.", "[Next item after the batch")
                .contains("She smiled.", "<s id=\"1\">She opened the door.</s>", "<s id=\"2\">1881</s>")
                .contains("[Immutable tokens per item", "3: ⟦g0⟧ ⟦g1⟧")
                .doesNotContain("1: ⟦g", "2: ⟦g", "{{", "}}");
    }

    @Test
    void messagesFor_batchWithoutToken_sendsNoTokenRulesAndNoTokenBlock() {
        final List<BatchItem> plain = List.of(new BatchItem("1", "She opened the door."), new BatchItem("2", "1881"));

        final List<ChatMessage> messages = builder("en", "uk").messagesFor(fullContext(), plain);

        assertThat(messages.getFirst().content())
                .doesNotContain("⟦gN⟧", "tokens", "Two tokens", "list-marker")
                .contains("7. Follow any [Extra instruction]");
        assertThat(messages.get(1).content()).doesNotContain("Immutable", "token");
    }

    @Test
    void messagesFor_batchWithOneTokenItem_sendsTheFullTokenRules() {
        final List<ChatMessage> messages = builder("en", "uk").messagesFor(fullContext(), ITEMS);

        assertThat(messages.getFirst().content())
                .contains("4. ⟦gN⟧ tokens stand for", "10. Follow any [Extra instruction]");
    }

    @Test
    void messagesFor_lexiconAndKeyTerms_carryTheRenderingsAndTheClosedList() {
        final BatchContext context = new BatchContext(
                new DraftContext(List.of(), null, List.of(), List.of(), List.of(), List.of("master → господар")),
                List.of(),
                null,
                List.of(),
                List.of("master", "imp"));

        final String user =
                builder("en", "uk").messagesFor(context, ITEMS).get(1).content();

        assertThat(user)
                .contains("[Established renderings of recurring terms", "master → господар")
                .contains("[Key terms", "master, imp")
                .doesNotContain("{{", "}}");
    }

    @Test
    void messagesFor_noLexiconAndNoKeyTerms_leavesBothBlocksOut() {
        final String user = builder("en", "uk")
                .messagesFor(BatchContext.empty(), ITEMS)
                .get(1)
                .content();

        assertThat(user).doesNotContain("[Established renderings", "[Key terms");
    }

    @Test
    void messagesFor_emptyContext_leavesEveryOptionalBlockOut() {
        final String user = builder("en", "uk")
                .messagesFor(BatchContext.empty(), List.of(new BatchItem("1", "1881")))
                .get(1)
                .content();

        assertThat(user)
                .doesNotContain("[Book so far", "[Glossary", "[Characters", "[Previous pairs", "[Next item")
                .doesNotContain("[Immutable tokens", "(none");
    }

    @Test
    void messagesFor_twoBatches_shareTheSameSystemBytes() {
        final BatchPromptBuilder builder = builder("en", "uk");

        final String first =
                builder.messagesFor(fullContext(), ITEMS).getFirst().content();
        final String second = builder.messagesFor(BatchContext.empty(), List.of(ITEMS.getLast()))
                .getFirst()
                .content();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void messagesFor_system_carriesOneLanguageRulesSectionAndStaysWithinTheBudget() {
        final String system = builder("en", "uk")
                .messagesFor(BatchContext.empty(), ITEMS)
                .getFirst()
                .content();

        assertThat(system).containsOnlyOnce("[Language rules: English -> Ukrainian]\nTarget — Ukrainian");
        assertThat(TokenEstimator.estimate(system, "en")).isLessThanOrEqualTo(SYSTEM_BUDGET_TOKENS);
    }

    @Test
    void messagesFor_pairExample_hasABatchWithANumberOnlyLine() {
        final String system = builder("en", "uk")
                .messagesFor(BatchContext.empty(), ITEMS)
                .getFirst()
                .content();

        assertThat(system).contains("<s id=\"2\">1881</s>");
        assertThat(system).contains("{\"id\":\"2\",\"target\":\"1881\"}");
    }

    @Test
    void messagesFor_pairWithoutBatchExamples_fallsBackToTheGenericNumberOnlyExample() {
        final String system = builder("en", "de")
                .messagesFor(BatchContext.empty(), ITEMS)
                .getFirst()
                .content();

        assertThat(system).contains("<s id=\"1\">1881</s>", "<s id=\"2\">* * *</s>");
    }

    @Test
    void messagesFor_everyShownExample_isAReplyTheBatchParserAcceptsCleanly() {
        final BatchReplyParser parser = new BatchReplyParser(new ObjectMapper());
        final String system = builder("en", "uk")
                .messagesFor(BatchContext.empty(), ITEMS)
                .getFirst()
                .content();

        final List<Matcher> examples = EXAMPLE.matcher(system)
                .results()
                .map(r -> EXAMPLE.matcher(r.group()))
                .toList();

        assertThat(examples).hasSize(1);
        examples.forEach(example -> {
            assertThat(example.matches()).isTrue();
            final List<BatchItem> items = ITEM.matcher(example.group(1))
                    .results()
                    .map(item -> new BatchItem(item.group(1), item.group(2)))
                    .toList();
            assertThat(parser.parse(example.group(2).strip(), items, "en", "uk").isClean())
                    .as(example.group())
                    .isTrue();
        });
    }

    @Test
    void requestFor_json_asksForTheSchemaAndCapsTheReply() {
        final BatchPromptBuilder builder = builder("en", "uk");
        final List<ChatMessage> messages = builder.messagesFor(BatchContext.empty(), ITEMS);

        final ChatRequest request = builder.requestFor(messages, ITEMS, false);

        assertThat(request.responseFormat()).isNotNull();
        assertThat(request.responseFormat().name()).isEqualTo("draft-batch-json");
        assertThat(request.callKind()).isEqualTo(CallKind.DRAFT);
        assertThat(request.maxOutputTokens()).isGreaterThanOrEqualTo(128);
    }

    @Test
    void requestFor_moreItems_getsALargerCap() {
        final BatchPromptBuilder builder = builder("en", "uk");
        final List<ChatMessage> messages = builder.messagesFor(BatchContext.empty(), ITEMS);

        final ChatRequest one = builder.requestFor(messages, ITEMS.subList(0, 1), false);
        final ChatRequest three = builder.requestFor(messages, ITEMS, false);

        assertThat(three.maxOutputTokens()).isGreaterThan(one.maxOutputTokens());
    }
}
