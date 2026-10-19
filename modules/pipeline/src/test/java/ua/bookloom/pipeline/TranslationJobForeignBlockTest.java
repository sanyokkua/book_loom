package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.ChunkRunFixtures.DRAFT;
import static ua.bookloom.pipeline.ChunkRunFixtures.REVIEW;
import static ua.bookloom.pipeline.ChunkRunFixtures.reviewed;
import static ua.bookloom.pipeline.ChunkRunFixtures.userMessage;
import static ua.bookloom.pipeline.TranslationJobTestSupport.epubBrief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.job;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;
import static ua.bookloom.pipeline.TranslationJobTestSupport.replies;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;
import static ua.bookloom.pipeline.TranslationJobTestSupport.stored;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * A paragraph whose own block declares another language — the fixture book's {@code <p xml:lang="la">} — is kept as it
 * is without a model call under Keep as-is, and translated like any other under the two translate policies.
 */
class TranslationJobForeignBlockTest {

    private static final String LATIN = "Gravitas omnia trahit, sed nemo videt.";
    private static final String LATIN_ID = "OEBPS/ch0.xhtml:1";
    // The book helper wraps each paragraph in <p>…</p>; closing the first one early opens a Latin paragraph of its own.
    private static final List<List<String>> BOOK =
            List.of(List.of("He spoke.</p><p xml:lang=\"la\">" + LATIN, "She left."));

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUpWorkers() {
        TranslationJobTestSupport.shutdownAll();
    }

    @Test
    void run_latinBlockUnderKeep_isKeptAsItIsWithoutAModelCall() {
        final TestProject project = project(book(), withForeign(ForeignPassagePolicy.KEEP));
        final ScriptedChatModel model = replies("Він заговорив.", "Вона пішла.").answerTo(REVIEW, reviewed());

        final JobReport report = report(job(project, model).run());

        assertThat(report.end()).isEqualTo(JobState.COMPLETED);
        assertThat(model.requests())
                .hasSize(2)
                .noneMatch(request -> userMessage(request).contains("<Text>\n" + LATIN));
        assertThat(stored(project, LATIN_ID))
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::machineTarget)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.VERBATIM, LATIN);
    }

    @ParameterizedTest
    @EnumSource(
            value = ForeignPassagePolicy.class,
            names = {"TRANSLATE", "TRANSLATE_WITH_NOTE"})
    void run_latinBlockUnderATranslatePolicy_isDrafted(final ForeignPassagePolicy policy) {
        final TestProject project = project(book(), withForeign(policy));
        final ScriptedChatModel model = replies("Він заговорив.", "Гравітація тягне все.", "Вона пішла.")
                .answerTo(REVIEW, reviewed());

        report(job(project, model).run());

        assertThat(drafts(model)).hasSize(3);
        assertThat(userMessage(drafts(model).get(1))).contains(LATIN);
        assertThat(stored(project, LATIN_ID).path()).isNotEqualTo(SegmentPath.VERBATIM);
    }

    // Translate with a note tells the model to add the original wording after the translation.
    @Test
    void run_latinBlockUnderTranslateWithNote_asksForTheOriginalInParentheses() {
        final TestProject project = project(book(), withForeign(ForeignPassagePolicy.TRANSLATE_WITH_NOTE));
        final ScriptedChatModel model = replies("Він заговорив.", "Гравітація тягне все.", "Вона пішла.")
                .answerTo(REVIEW, reviewed());

        report(job(project, model).run());

        assertThat(systemMessage(drafts(model).get(1)))
                .contains("add its original wording in parentheses right after the translation");
    }

    private Path book() {
        return TestBooks.epub(tempDir.resolve("Book.epub"), BOOK, "en");
    }

    private static List<ChatRequest> drafts(final ScriptedChatModel model) {
        return model.requests().stream()
                .filter(request -> request.responseFormat() != null
                        && DRAFT.equals(request.responseFormat().name()))
                .toList();
    }

    private static String systemMessage(final ChatRequest request) {
        return request.messages().getFirst().content();
    }

    private static BookBrief withForeign(final ForeignPassagePolicy policy) {
        final BookBrief brief = epubBrief();
        return new BookBrief(
                brief.sourceLanguage(),
                brief.targetLanguage(),
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                policy,
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }
}
