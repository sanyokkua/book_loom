package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Group 10's acceptance (design D12-D14): a code repair restores what a draft lost with no model call, and a draft the
 * checks fault gets exactly one repair call. Written red in task 10.1; green in 10.10.
 */
@Tag("slow")
class OneRepairE2eTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String REPAIR_PROMPT_MARK = "[Defects to fix";
    private static final String PREVIOUS_ATTEMPT_MARK = "[Previous attempt";

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_headingNumberLost_exportsRestoredNumberWithNoModelCall(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            // A one-segment book is drafted alone, so the reply is the single-segment form, not a batch.
            provider.stubSequence(List.of(provider.target("Нічний перевал", Duration.ZERO)));
            final Path book = TestBooks.markdown(directory.resolve("Book.md"), "# 4. Ночной перевал");
            final WholeBookRun.Project project = WholeBookRun.project(book, QualityDial.BALANCED, "ru", "uk");

            report(project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}))
                    .run());
            WholeBookRun.export(project, directory.resolve("Book.uk.md"));

            assertThat(WholeBookRun.read(directory.resolve("Book.uk.md"))).contains("4. Нічний перевал");
            assertThat(provider.chatBodies()).hasSize(1).noneMatch(body -> body.contains(REPAIR_PROMPT_MARK));
        }
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_russianWordsInDraft_makesExactlyOneRepairCall(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.reply(
                            WholeBookRun.batchReply(List.of("Вони сказали, что ключ у шухляді.")), Duration.ZERO),
                    provider.target("Вони сказали, що ключ у шухляді.", Duration.ZERO),
                    provider.reply("{\"results\":[]}", Duration.ZERO)));
            final Path book = TestBooks.markdown(directory.resolve("Book.md"), "Они сказали, что ключ в ящике.");
            final WholeBookRun.Project project = WholeBookRun.project(book, QualityDial.BALANCED, "ru", "uk");

            report(project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}))
                    .run());

            // The Balanced reviewer still reads the repaired draft (until group 14), so the third request is its.
            assertThat(provider.chatBodies())
                    .filteredOn(body -> body.contains(PREVIOUS_ATTEMPT_MARK) && body.contains(REPAIR_PROMPT_MARK))
                    .hasSize(1);
            assertThat(provider.chatBodies()).hasSize(3);
        }
    }
}
