package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** The sixteen ids the owner has installed, as the engines list them; a wrong family here would pick a wrong profile. */
class OwnerInstalledIdsTest {

    private static final String RESOURCE = "/ua/bookloom/llm/identity/owner-installed-ids.csv";

    private final ModelIdentityParser parser = new ModelIdentityParser();

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(resources = RESOURCE, numLinesToSkip = 1, quoteCharacter = '"', maxCharsPerColumn = 4096)
    void parse_ownerId_matchesExpectedFields(
            final String id,
            final String input,
            final String inputKind,
            final String provenance,
            final String expectedJson,
            final String mustBeAbsent,
            final String resolvedBy,
            final String note) {
        NameCaseAssertions.assertMatches(id, parser.parse(input), expectedJson, mustBeAbsent);
    }

    @Test
    void ownerIds_file_hasSixteenUniqueIds() throws IOException {
        final List<String> lines;
        try (InputStream in = OwnerInstalledIdsTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
        final List<String> ids = lines.stream()
                .skip(1)
                .map(line -> line.substring(0, line.indexOf(',')))
                .toList();

        assertThat(ids).hasSize(16).doesNotHaveDuplicates();
    }
}
