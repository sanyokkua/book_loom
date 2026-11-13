package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Grammar v2 over the research corpus: 218 cases of which six are left out here, the display names of LM Studio
 * (DN01-DN04, DN06, DN07). Those are labels a program wrote for people, not ids a person types or a server lists,
 * and the rule that turns their spaces into "-" belongs to GGUF {@code general.base_model.*.name} values only (DN05
 * stays), never to a typed or pasted id.
 */
class ModelIdentityParserV2Test {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(
            resources = "/ua/bookloom/llm/identity/name-cases-v2.csv",
            numLinesToSkip = 1,
            quoteCharacter = '"',
            maxCharsPerColumn = 4096)
    void parse_corpusCase_matchesExpectedFields(
            final String id,
            final String input,
            final String inputKind,
            final String provenance,
            final String expectedJson,
            final String mustBeAbsent,
            final String resolvedBy,
            final String note) {
        NameCaseAssertions.assertMatches(
                id, NameCaseAssertions.parse(parser, inputKind, input), expectedJson, mustBeAbsent);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"gpt-oss:20b,3.6", "gemma4:26b-mlx,4.0", "glm-4.7-flash,3.0"})
    void parse_aliasFact_setsActiveSize(final String name, final double active) {
        assertThat(parser.parse(name).identity().activeB()).isEqualTo(active);
    }

    @Test
    void parse_aliasFactWithoutEvidence_leavesActiveSizeEmpty() {
        assertThat(parser.parse("gpt-oss:120b").identity().activeB()).isNull();
    }

    @Test
    void parse_activeSizeInTheName_winsOverTheAliasFact() {
        assertThat(parser.parse("gemma4:26b-a4b-it-qat").identity().activeB()).isEqualTo(4.0);
    }

    @Test
    void parse_typedIdWithSpaces_isNotRewrittenToHyphens() {
        assertThat(parser.parse("Qwen2.5 1.5B").identity().sizeB()).isNull();
        assertThat(parser.parseBaseModelName("Qwen2.5 1.5B").identity().sizeB()).isEqualTo(1.5);
    }
}
