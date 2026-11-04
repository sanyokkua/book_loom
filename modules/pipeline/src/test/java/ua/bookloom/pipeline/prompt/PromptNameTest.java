package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ResponseFormat;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/** Verifies each template's call kind, response-format name and temperature. */
class PromptNameTest {

    @ParameterizedTest
    @CsvSource({
        "DRAFT,draft,DRAFT,draft,0.2",
        "STRUCTURAL_REPAIR,structural-repair,STRUCTURAL_REPAIR,structural-repair,0.2",
        "REPAIR,repair,REPAIR,draft-batch-json,0.1",
        "TRANSLATE,translate,DRAFT,draft-batch-json,0.2",
        "FORMAT_REASK,format-reask,STRUCTURAL_REPAIR,draft-batch-json,0.2",
        "REVIEWER,reviewer,REVIEW,reviewer,0.0",
        "DIRECTED_FIX,directed-fix,DIRECTED_FIX,directed-fix,0.2",
        "REVISION,revision,REVISION,revision,0.2",
        "PRESCAN,prescan,PRESCAN,prescan,0.2",
        "REVIEW_TERMS,review-terms,REVIEW_TERMS,review-terms,0.1",
        "SUGGEST_TARGETS,suggest-targets,SUGGEST_TARGETS,suggest-targets,0.1",
        "SUMMARY,summary,SUMMARY,summary,0.2",
        "SUSPICIOUS_WORDS,suspicious-words,REVIEW,suspicious-words,0.0",
        "BOOK_KNOWLEDGE,book-knowledge,BOOK_KNOWLEDGE,book-knowledge,0.0",
        "BRIEF_SYNTHESIS,brief-synthesis,BRIEF_SYNTHESIS,brief-synthesis,0.0",
        "TERM_READING,term-reading,TERM_READING,term-reading,0.0",
        // 15i.N7: the recurring term's reading is a reading of one term in windows; its batch choice has its own kind.
        "LEXICON_READING,lexicon-reading,TERM_READING,lexicon-reading,0.0",
        "TERM_CHOICE,term-choice,TERM_CHOICE,term-choice,0.1",
        // 15i.N5: the summary is a reading of one term, shown and labelled as a TERM_READING call.
        "TERM_SUMMARY,term-summary,TERM_READING,term-summary,0.0"
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

    // Task 2.3: batches go out in one JSON format; the marked and dual batch prompts must not come back.
    @ParameterizedTest
    @ValueSource(strings = {"DRAFT_BATCH_" + "MARKERS", "DRAFT_BATCH_" + "DUAL", "DRAFT_BATCH_" + "JSON"})
    void values_retiredBatchPrompt_isNotAName(final String retired) {
        assertThat(Arrays.stream(PromptName.values()).map(PromptName::name)).doesNotContain(retired);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT", "TRANSLATE"})
    void temperature_draftLower_isPointOne(final PromptName name) {
        assertThat(name.temperature(true)).isEqualTo(0.1);
    }

    @ParameterizedTest
    @CsvSource({
        "STRUCTURAL_REPAIR",
        "FORMAT_REASK",
        "REPAIR",
        "REVIEWER",
        "DIRECTED_FIX",
        "REVISION",
        "PRESCAN",
        "REVIEW_TERMS",
        "SUGGEST_TARGETS",
        "SUMMARY",
        "SUSPICIOUS_WORDS",
        "BOOK_KNOWLEDGE",
        "BRIEF_SYNTHESIS",
        "TERM_READING",
        "LEXICON_READING",
        "TERM_SUMMARY"
    })
    void temperature_lowerNotDefined_throws(final PromptName name) {
        assertThatThrownBy(() -> name.temperature(true)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"DRAFT", "STRUCTURAL_REPAIR"})
    void pseudoModel_everyResponseFormatName_answersTargetObject(final PromptName name) {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "<Text>\nHi.\n</Text>")),
                name.temperature(false),
                new ResponseFormat(name.responseFormatName(), DraftSchema.SCHEMA),
                ReasoningLevel.OFF);

        final var result = new PseudoChatModel(new ObjectMapper()).chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(result.data()).extracting(ChatResponse::content).isEqualTo("{\"target\":\"HI.\"}");
    }

    // A single draft is a batch of one: the offline model answers every translate-family call by its items.
    @ParameterizedTest
    @CsvSource({"TRANSLATE", "FORMAT_REASK", "REPAIR"})
    void pseudoModel_translateFamily_answersTheItemsObject(final PromptName name) {
        final ChatRequest request = new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "<Items>\n<s id=\"1\">Hi.</s>\n</Items>")),
                name.temperature(false),
                new ResponseFormat(name.responseFormatName(), name.responseSchema()),
                ReasoningLevel.OFF);

        final var result = new PseudoChatModel(new ObjectMapper()).chat(request);

        assertThat(result.isOk()).isTrue();
        assertThat(result.data())
                .extracting(ChatResponse::content)
                .isEqualTo("{\"items\":[{\"id\":\"1\",\"target\":\"HI.\"}]}");
    }
}
