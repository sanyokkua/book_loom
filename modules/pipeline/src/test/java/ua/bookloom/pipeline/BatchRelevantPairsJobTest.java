package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.BATCH;
import static ua.bookloom.pipeline.BatchDraftingJobTest.batchReply;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.ChunkRunFixtures.formats;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.counts;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.glossary.GlossaryIds;

/**
 * A run shows each batch up to two accepted pairs from earlier in the book that name the same glossary names as the
 * batch (15i.T8), found through the book index with no model call. Six paragraphs go in two batches of four and two;
 * the second batch names Hale and Moreau, and the paragraphs before it name them like this:
 * 0 Hale, 1 Moreau, 2 neither, 3 both, the last two of which are its previous pairs.
 */
class BatchRelevantPairsJobTest {

    private static final String P0 = "Hale found a brass key in the old drawer.";
    private static final String P1 = "Moreau waited outside by the gate.";
    private static final String P2 = "The rain fell on the quiet town.";
    private static final String P3 = "Hale and Moreau walked past the mill.";
    private static final String P4 = "Moreau told Hale about the key.";
    private static final String P5 = "Nobody else heard them speak.";
    private static final String T0 = "Гейл знайшов мідний ключ у старій шухляді.";
    private static final String T1 = "Моро чекав надворі біля брами.";
    private static final String T2 = "Дощ падав на тихе спокійне містечко.";
    private static final String T3 = "Гейл і Моро повільно пройшли повз старий млин.";
    private static final String T4 = "Моро розповів Гейлу про ключ.";
    private static final String T5 = "Більше ніхто не чув, як вони розмовляли.";
    private static final String HEADING = "[Related earlier pairs";
    private static final int FIRST_BATCH_SIZE = 4;

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        System.clearProperty("bookloom.relevant.pairs");
        TranslationJobTestSupport.shutdownAll();
    }

    private static String pair(final String source, final String target) {
        return "Source: " + source + "\nTranslation: " + target;
    }

    private ScriptedChatModel runTheBook() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(BATCH, batchReply(items(entry("1", T0), entry("2", T1), entry("3", T2), entry("4", T3))))
                .answerTo(BATCH, batchReply(items(entry("1", T4), entry("2", T5))));
        final TestProject project = project(
                TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", P0, P1, P2, P3, P4, P5)),
                brief("en", "uk"));
        addTerm(project, "Hale", "Гейл");
        addTerm(project, "Moreau", "Моро");

        final var run =
                report(batchedJob(project, model, new RunRequest(project.id(), ReviewMode.UNATTENDED), FIRST_BATCH_SIZE)
                        .run());

        assertThat(run.end()).isEqualTo(JobState.COMPLETED);
        assertThat(counts(project)).isEqualTo(new SegmentCounts(0, 6, 0, 0, 0));
        return model;
    }

    private static void addTerm(final TestProject project, final String term, final String target) {
        project.stores()
                .glossary()
                .add(new GlossaryEntry(
                        GlossaryIds.of(project.id(), term),
                        project.id(),
                        term,
                        target,
                        TermType.CHARACTER,
                        Gender.MALE,
                        false));
    }

    private static String secondBatchPrompt(final ScriptedChatModel model) {
        assertThat(model.requests()).hasSize(2);
        final ChatRequest second = model.requests().get(1);
        return userMessage(second);
    }

    // IF the second batch names Hale and Moreau, THEN it shows the accepted earlier pairs that name them — not the two
    // it already shows as previous pairs, and none from the batch's own or later paragraphs — best first.
    @Test
    void run_secondBatchNamingGlossaryNames_showsTheEarlierPairsThatNameThemBeforeItsPreviousPairs() {
        final ScriptedChatModel model = runTheBook();

        final String user = secondBatchPrompt(model);

        assertThat(user)
                .containsSubsequence(
                        HEADING, pair(P1, T1), pair(P0, T0), "[Previous pairs", pair(P2, T2), pair(P3, T3), "<Items>");
        assertThat(user).doesNotContain(pair(P4, T4), pair(P5, T5));
    }

    // The paragraph naming both names would be the best related pair, but the batch already shows it as a previous
    // pair.
    @Test
    void run_pairAmongThePreviousPairs_isShownOnceNotAgainAsARelatedPair() {
        final String user = secondBatchPrompt(runTheBook());

        assertThat(user.split(Pattern.quote(pair(P3, T3)), -1)).hasSize(2);
    }

    // The first batch has nothing before it to relate to.
    @Test
    void run_firstBatch_showsNoRelatedPairs() {
        final ScriptedChatModel model = runTheBook();

        assertThat(userMessage(model.requests().getFirst())).doesNotContain(HEADING);
    }

    // IF related pairs are chosen by code, THEN the run makes only its two batch calls.
    @Test
    void run_choosingRelatedPairs_makesNoModelCallOfItsOwn() {
        final ScriptedChatModel model = runTheBook();

        assertThat(formats(model)).containsExactly(BATCH, BATCH);
    }

    // The switch is the eval's: with it off the same run sends the same batches without the block.
    @ParameterizedTest
    @CsvSource({"on,true", "off,false"})
    void run_switch_decidesWhetherTheSecondBatchShowsTheBlock(final String setting, final boolean shown) {
        System.setProperty("bookloom.relevant.pairs", setting);

        final String user = secondBatchPrompt(runTheBook());

        assertThat(user.contains(HEADING)).isEqualTo(shown);
        assertThat(user).contains("[Previous pairs", pair(P2, T2), pair(P3, T3));
    }
}
