package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/** Verifies each template's call kind, response-format name and temperature. */
class PromptNameTest {

    @ParameterizedTest
    @CsvSource({
        "DRAFT,draft,DRAFT,draft,0.2",
        "STRUCTURAL_REPAIR,structural-repair,STRUCTURAL_REPAIR,structural-repair,0.2",
        "PLACEHOLDER_REPAIR,placeholder-repair,PLACEHOLDER_REPAIR,placeholder-repair,0.2",
        "JUDGE,judge,JUDGE,judge,0.1",
        "DIRECTED_FIX,directed-fix,DIRECTED_FIX,directed-fix,0.2",
        "REFLECT,reflect,REFLECT,reflect,0.35",
        "IMPROVE,improve,IMPROVE,improve,0.35",
        "POLISH,polish,POLISH,polish,0.2",
        "PRESCAN,prescan,PRESCAN,prescan,0.2",
        "SUMMARY,summary,SUMMARY,summary,0.2"
    })
    void constants_declared_carryListedValues(
            final PromptName name,
            final String base,
            final CallKind kind,
            final String format,
            final double temperature) {
        assertThat(name.resourceBaseName()).isEqualTo(base);
        assertThat(name.callKind()).isEqualTo(kind);
        assertThat(name.responseFormatName()).isEqualTo(format);
        assertThat(name.temperature(false)).isEqualTo(temperature);
    }

    @Test
    void temperature_draftLower_isPointOne() {
        assertThat(PromptName.DRAFT.temperature(true)).isEqualTo(0.1);
    }

    @ParameterizedTest
    @CsvSource({
        "STRUCTURAL_REPAIR",
        "PLACEHOLDER_REPAIR",
        "JUDGE",
        "DIRECTED_FIX",
        "REFLECT",
        "IMPROVE",
        "POLISH",
        "PRESCAN",
        "SUMMARY"
    })
    void temperature_lowerNotDefined_throws(final PromptName name) {
        assertThatThrownBy(() -> name.temperature(true)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT", "STRUCTURAL_REPAIR", "PLACEHOLDER_REPAIR"})
    void pseudoModel_everyResponseFormatName_answersTargetObject(final PromptName name) {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "<Text>\nHi.\n</Text>")),
                name.temperature(false),
                new ResponseFormat(name.responseFormatName(), DraftSchema.SCHEMA),
                false);

        final var result = new PseudoChatModel(new ObjectMapper()).chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("{\"target\":\"HI.\"}");
    }
}
