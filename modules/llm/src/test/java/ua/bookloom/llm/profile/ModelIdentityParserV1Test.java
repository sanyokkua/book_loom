package ua.bookloom.llm.profile;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * The 108 grammar v1 cases, kept as written. Grammar v2 adds two facts the v1 file lists as "must be absent": the
 * alias tables now name the family a fine-tune is built on ({@code base_family}) and the active size of a tag such as
 * {@code gemma4:26b} ({@code active_b}). The v2 file expects them on the same inputs (H28 against IN02, L11 against
 * IN01), so only those two keys are not checked for absence here; every other expectation is unchanged.
 */
class ModelIdentityParserV1Test {

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(
            resources = "/ua/bookloom/llm/identity/name-cases-v1.csv",
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
                id, parser.parse(input), expectedJson, NameCaseAssertions.withoutKeysV2Added(mustBeAbsent));
    }
}
