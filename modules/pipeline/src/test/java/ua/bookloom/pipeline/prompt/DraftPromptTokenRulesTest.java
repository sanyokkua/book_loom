package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.BookBrief;

/** A draft call is sent the token rules and the token block only when its text holds a token. */
class DraftPromptTokenRulesTest {

    // A text with no token is sent no token rule and no token block, so the model has nothing to invent a token for.
    @Test
    void messagesFor_textWithoutToken_sendsNoTokenRulesAndNoTokenBlock() {
        final DraftPromptBuilder builder = builder();

        final var messages = builder.messagesFor(GoldenCases.segment("He opened the old door."));

        assertThat(messages.getFirst().content())
                .doesNotContain("⟦gN⟧", "tokens", "Two tokens", "list-marker")
                .contains("3. Text of only numbers or symbols is copied.", "6. Follow any [Extra instruction]");
        assertThat(messages.get(1).content()).doesNotContain("Immutable", "token", "none —");
    }

    // The same builder answers a text with a token with the full rules, so one run sends both variants.
    @Test
    void messagesFor_textWithToken_sendsTheTokenRulesAndTheTokenBlock() {
        final DraftPromptBuilder builder = builder();

        final var messages = builder.messagesFor(GoldenCases.segment("He opened the ⟦g0⟧old⟦g1⟧ door."));

        assertThat(messages.getFirst().content())
                .contains("3. ⟦gN⟧ tokens stand for", "9. Follow any [Extra instruction]");
        assertThat(messages.get(1).content())
                .contains("[Immutable tokens for this text]", "sequence unchanged: ⟦g0⟧ ⟦g1⟧");
    }

    private static DraftPromptBuilder builder() {
        final BookBrief brief = BookBrief.defaults("en");
        return new DraftPromptBuilder(
                new PromptTemplates(), new CallFrame("en", "uk", StyleSheet.from(brief), brief.foreignPassages()));
    }
}
