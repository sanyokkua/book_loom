package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Genre;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.chunk.TokenEstimator;

/**
 * The shape every rendered prompt keeps, whatever its wording: a literal, parseable reply the model can copy, the
 * placeholder rule wherever book text carries tokens, book text declared as data rather than instructions, no slot
 * left unfilled, and a length a small model still reads to the end.
 */
class PromptShapeTest {

    private static final PromptTemplates TEMPLATES = new PromptTemplates();
    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String MASKED = "He opened the ⟦g0⟧old⟦g1⟧ door.";
    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** The top-level key a literal reply of each response format holds. */
    private static final Map<String, String> REPLY_KEYS = Map.ofEntries(
            Map.entry("draft", "target"),
            Map.entry("structural-repair", "target"),
            Map.entry("placeholder-repair", "target"),
            Map.entry("directed-fix", "target"),
            Map.entry("improve", "target"),
            Map.entry("polish", "target"),
            Map.entry("revision", "target"),
            Map.entry("reviewer", "results"),
            Map.entry("reflect", "issues"),
            Map.entry("prescan", "terms"),
            Map.entry("consistency", "target"),
            Map.entry("file-name", "target"));

    private static final Map<String, String> OTHER_REPLY_KEYS = Map.of(
            "review-terms", "verdicts",
            "suggest-targets", "suggestions",
            "summary", "summary",
            "draft-batch-json", "items",
            "suspicious-words", "words",
            "brief-suggestion", "genre",
            "term-choice", "terms",
            "book-knowledge", "known",
            "brief-synthesis", "genre",
            "term-reading", "windows");

    // Raised from 697 by the pair's straight-quote example line (15h.C3), about 21 tokens; measured 698 before the
    // language-rules map replaced the per-language example files.
    private static final int DRAFT_SYSTEM_BUDGET = 720;
    private static final int SYSTEM_BUDGET = 900;
    // The reviewer also carries the languages' reviewer checks; the suggestion call carries its name examples.
    private static final int LARGE_SYSTEM_BUDGET = 1230;

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCall_showsALiteralReplyItsParserReads(final PromptName name) {
        assertThat(hasLiteralReply(prompt(name), replyKey(name)))
                .as("a literal %s reply in %s", replyKey(name), name)
                .isTrue();
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCall_declaresBookTextAsDataNotInstructions(final PromptName name) {
        assertThat(prompt(name)).contains("not instructions");
    }

    // The name scans and the summary read display text, which carries no tokens.
    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {
                "PRESCAN",
                "REVIEW_TERMS",
                "SUGGEST_TARGETS",
                "SUMMARY",
                "SUSPICIOUS_WORDS",
                "FILE_NAME",
                "BRIEF_SUGGESTION",
                "TERM_CHOICE",
                "BOOK_KNOWLEDGE",
                "BRIEF_SYNTHESIS",
                "TERM_READING"
            },
            mode = EnumSource.Mode.EXCLUDE)
    void render_callWithTokens_statesThePlaceholderRule(final PromptName name) {
        assertThat(prompt(name)).contains("⟦gN⟧ token");
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCall_leavesNoSlotUnfilled(final PromptName name) {
        assertThat(prompt(name)).doesNotContain("{{", "}}");
    }

    @ParameterizedTest
    @EnumSource(PromptName.class)
    void render_everyCall_keepsItsSystemMessageWithinBudget(final PromptName name) {
        final boolean large = name == PromptName.REVIEWER || name == PromptName.SUGGEST_TARGETS;

        assertThat(TokenEstimator.estimate(system(name), "en"))
                .isLessThanOrEqualTo(large ? LARGE_SYSTEM_BUDGET : SYSTEM_BUDGET);
    }

    // IF the brief call invited the model's memory of a famous book, THEN its answer could not be checked against the
    // text; IF its example named a gender, THEN a small model would copy it.
    @Test
    void render_briefSuggestion_asksForAQuoteFromThePassagesAndShowsNoGender() {
        final String prompt = prompt(PromptName.BRIEF_SUGGESTION);

        assertThat(prompt)
                .contains("copied exactly from the passage", "\"confidence\"", "Dialogue share")
                .doesNotContain("what you know of this book", "\"value\":\"male\"", "\"value\":\"female\"");
    }

    // IF the example reply said "known: true", THEN a small model would copy it for every book, invented ones too; the
    // example is the safe answer.
    @Test
    void render_bookKnowledge_exampleReplyIsTheNotKnownAnswer() {
        final JsonNode example = prompt(PromptName.BOOK_KNOWLEDGE)
                .lines()
                .filter(line -> line.contains("{") && !line.contains("<"))
                .map(line -> line.substring(line.indexOf('{')))
                .map(PromptShapeTest::tree)
                .filter(node -> node.has("known"))
                .findFirst()
                .orElseThrow();

        assertThat(example.path("known").isBoolean()).isTrue();
        assertThat(example.path("known").asBoolean()).isFalse();
        assertThat(example.path("confidence").isNumber()).isTrue();
    }

    // The question names the book by its title and author and gives the model nothing to read; the book's own words
    // are what the answer is checked against, so the prompt tells the model to answer unknown rather than guess.
    @Test
    void render_bookKnowledge_asksAboutTheTitleAndAuthorAndAllowsNotKnowing() {
        final String user = TEMPLATES.renderUser(
                PromptName.BOOK_KNOWLEDGE, Map.of("title", "The Lantern Ledger", "author", "Orsolya Venn"));

        assertThat(user).contains("The Lantern Ledger", "Orsolya Venn").doesNotContain("{{", "}}");
        assertThat(system(PromptName.BOOK_KNOWLEDGE).toLowerCase(java.util.Locale.ROOT))
                .contains("known")
                .containsAnyOf("do not guess", "never guess", "not guess", "not sure", "unsure");
    }

    // IF the example put the value first, THEN a small model would copy the order and decide before it cites; the
    // synthesis is also told it may leave a field empty rather than guess (15i.B4).
    @Test
    void render_briefSynthesis_exampleReplyCitesItsBasisBeforeEachValueAndAllowsAnEmptyValue() {
        final JsonNode example = prompt(PromptName.BRIEF_SYNTHESIS)
                .lines()
                .filter(line -> line.contains("{") && !line.contains("<"))
                .map(line -> line.substring(line.indexOf('{')))
                .map(PromptShapeTest::tree)
                .filter(node -> node.has("genre"))
                .findFirst()
                .orElseThrow();

        assertThat(example.path("genre").fieldNames()).toIterable().containsExactly("basis", "value");
        assertThat(example.path("genre").path("basis").has("passages")).isTrue();
        assertThat(system(PromptName.BRIEF_SYNTHESIS).toLowerCase(java.util.Locale.ROOT))
                .contains("basis", "passage", "empty");
    }

    // IF a genre were added to the forty and not to the prompt, THEN the model would never be offered it.
    @ParameterizedTest
    @EnumSource(Genre.class)
    void render_briefSynthesis_listsEveryGenreByItsEnglishName(final Genre genre) {
        assertThat(system(PromptName.BRIEF_SYNTHESIS)).contains(genre.english());
    }

    @Test
    void render_bookKnowledgeWithoutAnAuthor_showsNoAuthorLine() {
        final String user = TEMPLATES.renderUser(PromptName.BOOK_KNOWLEDGE, Map.of("title", "The Lantern Ledger"));

        assertThat(user).contains("The Lantern Ledger").doesNotContain("Author");
    }

    // 15i.N4: the example reply copies the quote before it labels the window, as the schema orders it, and the example
    // quote holds a third-person pronoun of the narration.
    @Test
    void render_termReading_exampleReplyPutsEachQuoteBeforeItsLabels() {
        final JsonNode example = prompt(PromptName.TERM_READING)
                .lines()
                .filter(line -> line.contains("{") && !line.contains("<"))
                .map(line -> line.substring(line.indexOf('{')))
                .map(PromptShapeTest::tree)
                .filter(node -> node.has("windows") && node.path("windows").size() > 0)
                .findFirst()
                .orElseThrow();
        final java.util.List<String> fields = new java.util.ArrayList<>();
        example.path("windows").get(0).fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactly("window", "quote", "usage", "kind", "gender");
        assertThat(example.has("speaking")).isTrue();
        assertThat(example.has("brand")).isTrue();
    }

    // The reading tells the model what each label means and that a pronoun inside quoted speech is no answer.
    @Test
    void render_termReading_namesEveryUsageAndSaysSpeechIsNoEvidence() {
        final String system = system(PromptName.TERM_READING).toLowerCase(java.util.Locale.ROOT);

        assertThat(system)
                .contains("part-of-name", "\"word\"", "\"title\"", "speaking", "brand", "none", "[[")
                .containsAnyOf("inside quoted speech", "inside quotation marks", "in quoted speech");
    }

    @Test
    void render_termReading_userCarriesTheTermLineAndTheWindows() {
        final String user = TEMPLATES.renderUser(
                PromptName.TERM_READING, Map.of("term", "Arlo — 7 mentions", "windows", "[1] [[Arlo]] sat down."));

        assertThat(user).contains("Arlo — 7 mentions", "[1] [[Arlo]] sat down.").doesNotContain("{{", "}}");
    }

    // The draft system is sent with every segment of a book; a small model must still read its last rule.
    @Test
    void render_draftSystemWithExamples_staysWithinSevenHundredTokens() {
        final String system = system(PromptName.DRAFT);

        assertThat(system).contains("Examples (");
        assertThat(TokenEstimator.estimate(system, "en")).isLessThanOrEqualTo(DRAFT_SYSTEM_BUDGET);
    }

    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"DRAFT", "REVIEWER", "SUMMARY", "PRESCAN", "SUGGEST_TARGETS"})
    void render_callWithTheLanguageRulesSlot_carriesOneSectionForThePair(final PromptName name) {
        assertThat(system(name))
                .containsOnlyOnce("[Language rules: English -> Ukrainian]\nTarget — Ukrainian")
                .contains("Source — English", "Pair — English -> Ukrainian")
                .doesNotContain("Polish", "Russian");
    }

    @Test
    void render_reviewer_addsTheReviewerChecksTheDraftDoesNotCarry() {
        assertThat(system(PromptName.REVIEWER)).contains("Check: Quotes are «…» and balanced");
        assertThat(system(PromptName.DRAFT)).doesNotContain("Check: ");
    }

    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"REVISION"})
    void render_rewriteOfTokenText_listsTheImmutableTokens(final PromptName name) {
        assertThat(user(name)).contains("[Immutable tokens]\nCopy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧");
    }

    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"DIRECTED_FIX", "REVISION"})
    void render_rewrite_delimitsSourceAndTranslationApart(final PromptName name) {
        assertThat(user(name))
                .contains("<Source>\n" + MASKED + "\n</Source>", "<Translation>\n" + MASKED + "\n</Translation>")
                .doesNotContain("<Text>");
    }

    private static String prompt(final PromptName name) {
        return system(name) + "\n" + user(name);
    }

    /** The system message the call is sent with; the two repairs reuse the draft's. */
    private static String system(final PromptName name) {
        final PromptName owner = name.systemSlots().isPresent() ? name : PromptName.DRAFT;
        return owner == PromptName.SUGGEST_TARGETS
                ? TEMPLATES.renderSystem(
                        owner, FRAME, Map.of("nameRule", NameRules.bundled().rule(NamePolicy.TRANSLITERATE, "uk")))
                : TEMPLATES.renderSystem(owner, FRAME);
    }

    /** The user message with every slot the template declares filled, tokens included where it lists them. */
    private static String user(final PromptName name) {
        final Map<String, String> values = new HashMap<>();
        name.userSlots().required().forEach(slot -> values.put(slot, MASKED));
        name.userSlots().optional().forEach(slot -> values.put(slot, MASKED));
        values.computeIfPresent("tokens", (slot, value) -> "⟦g0⟧ ⟦g1⟧");
        values.computeIfPresent("expectedTokens", (slot, value) -> "⟦g0⟧ ⟦g1⟧");
        return TEMPLATES.renderUser(name, values);
    }

    private static JsonNode tree(final String candidate) {
        try {
            return MAPPER.readTree(candidate);
        } catch (JsonProcessingException notJson) {
            return MAPPER.missingNode();
        }
    }

    private static String replyKey(final PromptName name) {
        return REPLY_KEYS.getOrDefault(
                name.responseFormatName(), OTHER_REPLY_KEYS.getOrDefault(name.responseFormatName(), "?"));
    }

    /** Whether a line holds, from its first brace on, a JSON object with {@code key} and no {@code <…>} placeholder. */
    private static boolean hasLiteralReply(final String prompt, final String key) {
        return prompt.lines()
                .filter(line -> line.contains("{") && !line.contains("<"))
                .map(line -> line.substring(line.indexOf('{')))
                .anyMatch(candidate -> parsesWith(candidate, key));
    }

    private static boolean parsesWith(final String candidate, final String key) {
        try {
            final JsonNode node = MAPPER.readTree(candidate);
            return node.isObject() && node.has(key);
        } catch (JsonProcessingException notJson) {
            return false;
        }
    }
}
