package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.pipeline.eval.JudgeRubric.Mode;

class JudgeRubricTest {

    // shasum -a 256 modules/pipeline/src/test/resources/eval/judge/bl-judge-1.prompt | cut -c1-12
    private static final String EXPECTED_HASH = "d7b8b8ded24e";
    private static final String TUNED_HASH = "ef3c44349bc6";

    @Test
    void hash_sameText_isStableAcrossRuns() {
        assertThat(JudgeRubric.load().hash()).isEqualTo(EXPECTED_HASH);
        assertThat(JudgeRubric.load().hash()).isEqualTo(EXPECTED_HASH);
    }

    @Test
    void system_pointwise_holdsTheCommonRulesAndOnlyThePointwiseShape() {
        final String system = JudgeRubric.load().system(Mode.POINTWISE);

        assertThat(system).contains("careful, demanding reader", "\"scores\"").doesNotContain("\"winner\"");
    }

    @Test
    void system_pairwise_holdsTheCommonRulesAndOnlyThePairwiseShape() {
        final String system = JudgeRubric.load().system(Mode.PAIRWISE);

        assertThat(system).contains("careful, demanding reader", "\"winner\"").doesNotContain("\"scores\"");
    }

    @Test
    void load_tunedFile_carriesThatFilesOwnHashAndSections(@TempDir final Path dir) throws IOException {
        final Path tuned = Files.writeString(
                dir.resolve("tuned.prompt"),
                "@@common\nRead closely.\n@@pointwise\nScore it.\n@@pairwise\nPick one.\n",
                StandardCharsets.UTF_8);

        final JudgeRubric rubric = JudgeRubric.load(tuned);

        // printf of the six-line text above piped to shasum -a 256, first 12 digits
        assertThat(rubric.hash()).isEqualTo(TUNED_HASH);
        assertThat(rubric.system(Mode.PAIRWISE)).isEqualTo("Read closely.\n\nPick one.");
    }
}
