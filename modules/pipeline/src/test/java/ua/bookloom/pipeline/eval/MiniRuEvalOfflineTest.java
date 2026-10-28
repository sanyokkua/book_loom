package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/** The Russian-to-Ukrainian mini book goes through the whole flow on the pseudo model, which upper-cases its source. */
class MiniRuEvalOfflineTest {

    @TempDir
    private Path workDir;

    @Test
    void run_pseudoModel_decidesEverySeventeenBlocksWithNoError() {
        final MiniRun run = new MiniRunner(
                        MiniBook.load(MiniBook.MINI_RU), QualityDial.FAST, NamePolicy.TRANSLITERATE, null)
                .run(new PseudoChatModel(new ObjectMapper()), workDir);

        assertThat(run.errors()).isEmpty();
        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(run.outcomes()).hasSize(17);
        assertThat(run.outcomes().values())
                .allSatisfy(o -> assertThat(o.target()).isNotNull());
        assertThat(MiniScorer.score(MiniExpectations.load(MiniBook.MINI_RU), run))
                .extracting(MiniResult::id)
                .doesNotHaveDuplicates();
    }
}
