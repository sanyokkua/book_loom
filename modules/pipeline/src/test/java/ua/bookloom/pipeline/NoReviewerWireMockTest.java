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
 * Arm 7a settled that no per-chunk reviewer reads a draft (design D24): a Balanced run on either dialect sends a draft
 * and, for a defect, one repair, and no request carries the reviewer's response format.
 */
@Tag("slow")
class NoReviewerWireMockTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_balanced_sendsNoReviewerResponseFormat(final ProviderKind kind) {
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubSequence(List.of(
                    provider.target("Вони сказали, что ключ у шухляді.", Duration.ZERO),
                    provider.target("Вони сказали, що ключ у шухляді.", Duration.ZERO)));
            final Path book = TestBooks.markdown(directory.resolve("Book.md"), "Они сказали, что ключ в ящике.");
            final WholeBookRun.Project project = WholeBookRun.project(book, QualityDial.BALANCED, "ru", "uk");

            report(project.job(ReviewMode.UNATTENDED, provider.model(REQUEST_TIMEOUT, ignored -> {}))
                    .run());

            assertThat(provider.chatBodies())
                    .isNotEmpty()
                    .noneMatch(body -> body.contains("edits"))
                    .noneMatch(body -> body.contains("criterion"));
        }
    }
}
