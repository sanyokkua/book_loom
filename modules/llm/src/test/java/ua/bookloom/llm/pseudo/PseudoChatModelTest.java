package ua.bookloom.llm.pseudo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.llm.LlmModule;

/** Proves the pseudo model answers every catalogue response-format name with its documented shape. */
class PseudoChatModelTest {

    private static final String SCHEMA = "{\"type\":\"object\"}";

    // A draft request's one <Text> block, not the instructions around it, becomes the upper-cased target.
    @Test
    void chat_draftFormat_repliesWithUppercasedTargetObject() {
        final String message = "<Text>\nHe opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>";

        final ChatResponse response = send(message, "draft");

        assertThat(response.content()).isEqualTo("{\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"}");
        assertThat(response.usage()).isNull();
    }

    // A directed fix reads the rejected target from its one <Translation> block, ignoring the <Source> beside it.
    @Test
    void chat_directedFixFormat_repliesWithUppercasedRejectedTarget() {
        final String message = "<Source>\nHe opened the ⟦g0⟧old⟦g1⟧ door.\n</Source>\n\n"
                + "<Translation>\nhe opened the ⟦g0⟧old⟦g1⟧ door.\n</Translation>";

        final ChatResponse response = send(message, "directed-fix");

        assertThat(response.content()).isEqualTo("{\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"}");
    }

    // A batch draft answers every numbered item under its own id, tokens kept, and ignores the context blocks.
    @Test
    void chat_batchDraftFormat_repliesWithAnUppercasedEntryPerItemId() {
        final String message = "[Previous pairs]\nSource: Hi.\nTranslation: Привіт.\n\n<Items>\n"
                + "<s id=\"1\">He opened the ⟦g0⟧old⟦g1⟧ door.</s>\n<s id=\"2\">1881</s>\n</Items>";

        final ChatResponse response = send(message, "draft-batch-json");

        assertThat(response.content())
                .isEqualTo("{\"items\":[{\"id\":\"1\",\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"},"
                        + "{\"id\":\"2\",\"target\":\"1881\"}]}");
    }

    // Every other repair-shaped format answers the same single-target object.
    @ParameterizedTest
    @ValueSource(strings = {"structural-repair", "placeholder-repair", "directed-fix", "revision"})
    void chat_repairFormats_repliesWithUppercasedTargetObject(String formatName) {
        final ChatResponse response = send("<Text>\nhi\n</Text>", formatName);

        assertThat(response.content()).isEqualTo("{\"target\":\"HI\"}");
    }

    // The reviewer never asks for an edit, so a whole pseudo run never changes or flags a segment on its say-so.
    @Test
    void chat_reviewerFormat_repliesWithNoResults() {
        final ChatResponse response = send("anything", "reviewer");

        assertThat(response.content()).isEqualTo("{\"results\":[]}");
    }

    // The pre-scan reports each distinct capitalised word, in order of first appearance, as an unlocked other term.
    @Test
    void chat_prescanFormat_repliesWithCapitalisedWordsAsOtherTerms() {
        final ChatResponse response = send("<Text>\nMr Hale met Margaret in Milton.\n</Text>", "prescan");

        assertThat(response.content())
                .isEqualTo("{\"terms\":["
                        + "{\"term\":\"Mr\",\"type\":\"other\",\"gender\":\"unknown\"},"
                        + "{\"term\":\"Hale\",\"type\":\"other\",\"gender\":\"unknown\"},"
                        + "{\"term\":\"Margaret\",\"type\":\"other\",\"gender\":\"unknown\"},"
                        + "{\"term\":\"Milton\",\"type\":\"other\",\"gender\":\"unknown\"}]}");
    }

    // The glossary review keeps every listed term as a name and guesses nothing, so a pseudo review changes nothing.
    @Test
    void chat_reviewTermsFormat_judgesEveryListedTermANameWithNoGuess() {
        final ChatResponse response =
                send("[Terms]\n- Hale — 4× — \"Hale left.\"\n- Well — 9× — \"Well, no.\"\n", "review-terms");

        assertThat(response.content())
                .isEqualTo("{\"verdicts\":["
                        + "{\"term\":\"Hale\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\"},"
                        + "{\"term\":\"Well\",\"verdict\":\"name\",\"type\":\"other\",\"gender\":\"unknown\"}]}");
    }

    // The offline model knows no target language, so it suggests no rendering for any listed term.
    @Test
    void chat_suggestTargetsFormat_answersEveryListedTermWithNoSuggestion() {
        final ChatResponse response = send(
                "[Names]\n- Hale — person, male — \"Hale left.\"\n- Milton — place, unknown — \"In Milton.\"\n",
                "suggest-targets");

        assertThat(response.content())
                .isEqualTo("{\"suggestions\":["
                        + "{\"term\":\"Hale\",\"target\":\"\",\"gender\":\"unknown\"},"
                        + "{\"term\":\"Milton\",\"target\":\"\",\"gender\":\"unknown\"}]}");
    }

    // Summary is always empty and reports no facts.
    @Test
    void chat_summaryFormat_repliesWithEmptyBilingualSummary() {
        final ChatResponse response = send("anything", "summary");

        assertThat(response.content()).isEqualTo("{\"summary\":{\"source\":\"\",\"target\":\"\"},\"facts\":[]}");
    }

    // The cap is a provider-side control the pseudo model has no use for; the reply is the same with it set.
    @Test
    void chat_requestWithOutputCap_repliesAsWithoutOne() {
        final PseudoChatModel model = new PseudoChatModel(mapper());
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "<Text>\nhello\n</Text>")),
                null,
                new ResponseFormat("draft", SCHEMA),
                null,
                null,
                null,
                661);

        final Result<ChatResponse> result = model.chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).content()).isEqualTo("{\"target\":\"HELLO\"}");
    }

    private static ChatResponse send(String message, String formatName) {
        final PseudoChatModel model = new PseudoChatModel(mapper());
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, message)), null, new ResponseFormat(formatName, SCHEMA));

        final Result<ChatResponse> result = model.chat(request);

        assertThat(result.isOk()).as("pseudo chat result: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data(), "chat response");
    }

    private static ObjectMapper mapper() {
        return new LlmModule().objectMapper();
    }
}
