package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Guards the research file itself: a lost or duplicated row would silently shrink the proof. */
class NameCasesFileTest {

    private static final String RESOURCE = "/ua/bookloom/llm/identity/name-cases-v1.csv";

    @Test
    void nameCasesV1_file_hasOneHundredEightUniqueIds() throws IOException {
        // Ids and the header hold no comma or quote, and no row spans lines, so the text before the first comma is
        // the id.
        final List<String> lines;
        try (InputStream in = NameCasesFileTest.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).isNotNull();
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        }
        final List<String> ids = lines.stream()
                .skip(1)
                .map(line -> line.substring(0, line.indexOf(',')))
                .toList();

        assertThat(lines.getFirst())
                .isEqualTo("id,input,input_kind,provenance,expected_json,must_be_absent,resolved_by,note");
        assertThat(ids).hasSize(108).doesNotHaveDuplicates();
    }
}
