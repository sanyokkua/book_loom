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
    private static final Map<String, String> REPLY_KEYS = Map.of(
            "draft", "target",
            "structural-repair", "target",
            "placeholder-repair", "target",
            "directed-fix", "target",
            "improve", "target",
            "polish", "target",
            "revision", "target",
            "judge", "score",
            "reflect", "issues",
            "prescan", "terms");

    private static final Map<String, String> OTHER_REPLY_KEYS =
            Map.of("review-terms", "verdicts", "summary", "summary");

    private static final int DRAFT_SYSTEM_BUDGET = 700;
    private static final int SYSTEM_BUDGET = 900;

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
            names = {"PRESCAN", "REVIEW_TERMS", "SUMMARY"},
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
        assertThat(TokenEstimator.estimate(system(name), "en")).isLessThanOrEqualTo(SYSTEM_BUDGET);
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
            names = {"IMPROVE", "POLISH", "REVISION"})
    void render_rewriteOfTokenText_listsTheImmutableTokens(final PromptName name) {
        assertThat(user(name)).contains("[Immutable tokens]\nCopy this exact ordered sequence unchanged: ⟦g0⟧ ⟦g1⟧");
    }

    @ParameterizedTest
    @EnumSource(
            value = PromptName.class,
            names = {"DIRECTED_FIX", "IMPROVE", "POLISH", "REVISION", "REFLECT"})
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
        return TEMPLATES.renderSystem(owner, FRAME);
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
