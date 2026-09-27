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

    // A directed fix reads the rejected target from its one <Text> block, ignoring the source outside it.
    @Test
    void chat_directedFixFormat_repliesWithUppercasedRejectedTarget() {
        final String message =
                "[Source]\nHe opened the ⟦g0⟧old⟦g1⟧ door.\n" + "<Text>\nhe opened the ⟦g0⟧old⟦g1⟧ door.\n</Text>";

        final ChatResponse response = send(message, "directed-fix");

        assertThat(response.content()).isEqualTo("{\"target\":\"HE OPENED THE ⟦g0⟧OLD⟦g1⟧ DOOR.\"}");
    }

    // Every other repair-shaped format answers the same single-target object.
    @ParameterizedTest
    @ValueSource(strings = {"structural-repair", "placeholder-repair", "directed-fix", "improve", "polish", "revision"})
    void chat_repairFormats_repliesWithUppercasedTargetObject(String formatName) {
        final ChatResponse response = send("<Text>\nhi\n</Text>", formatName);

        assertThat(response.content()).isEqualTo("{\"target\":\"HI\"}");
    }

    // The judge always accepts, so a whole pseudo run never flags a segment on the judge's say-so.
    @Test
    void chat_judgeFormat_repliesWithAcceptingVerdict() {
        final ChatResponse response = send("anything", "judge");

        assertThat(response.content())
                .isEqualTo("{\"score\":1.0,\"verdict\":\"accept\",\"findings\":[],\"deferrals\":[]}");
    }

    // Reflect never raises a critique of its own.
    @Test
    void chat_reflectFormat_repliesWithEmptyIssues() {
        final ChatResponse response = send("anything", "reflect");

        assertThat(response.content()).isEqualTo("{\"issues\":[]}");
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

    // Summary is always empty and reports no facts.
    @Test
    void chat_summaryFormat_repliesWithEmptyBilingualSummary() {
        final ChatResponse response = send("anything", "summary");

        assertThat(response.content()).isEqualTo("{\"summary\":{\"source\":\"\",\"target\":\"\"},\"facts\":[]}");
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
