package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TranslationJobTestSupport.report;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * Each engine's timing reaches the final run-summary line where the engine reports it and shows as a dash where it
 * does not, so a missing figure never reads as zero (design D23).
 */
@Tag("slow")
class EngineTimingsRunSummaryTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SOURCE = "Они сказали, что ключ в ящике.";
    private static final String TARGET = "Вони сказали, що ключ у шухляді.";
    private static final Pattern DRAFT = Pattern.compile("DRAFT=[^\\]]*");

    @TempDir
    Path directory;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(RunSummaryLogger.class);
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(null);
        appender.stop();
    }

    @Test
    void lmStudioStats_ttft210() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LM_STUDIO)) {
            provider.stubAlwaysAt(
                    "/api/v0/chat/completions",
                    provider.replyWithFields(
                            TranslationJobTestSupport.targetReply(TARGET),
                            Map.of(
                                    "stats",
                                    Map.of(
                                            "time_to_first_token", 0.21,
                                            "tokens_per_second", 41.2,
                                            "generation_time", 1.9))));

            assertThat(draftFragment(provider)).contains("ttftMs:210");
        }
    }

    @Test
    void ollamaViaV1_noTiming_dashNeverZero() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.OLLAMA)) {
            provider.stubAlways(provider.target(TARGET, Duration.ZERO));

            assertThat(draftFragment(provider)).contains("ttftMs:–").doesNotContain("ttftMs:0");
        }
    }

    @Test
    void llamaServerTimings_promptMs() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LLAMA_CPP)) {
            provider.stubAlways(provider.replyWithFields(
                    TranslationJobTestSupport.targetReply(TARGET),
                    Map.of("timings", Map.of("prompt_ms", 180, "predicted_per_second", 38.4, "cache_n", 1840))));

            assertThat(draftFragment(provider)).contains("ttftMs:180");
        }
    }

    /** Runs one paragraph through a Balanced run and returns the DRAFT part of the final summary line. */
    private String draftFragment(final WireMockProvider provider) {
        final Path book = TestBooks.markdown(directory.resolve("Book.md"), SOURCE);
        final WholeBookRun.Project project = WholeBookRun.project(book, QualityDial.BALANCED, "ru", "uk");

        report(project.job(ReviewMode.UNATTENDED, provider.modelIn(project.injector(), REQUEST_TIMEOUT))
                .run());

        final String line = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("run summary final"))
                .findFirst()
                .orElse("");
        final Matcher draft = DRAFT.matcher(line);
        return draft.find() ? draft.group() : "no DRAFT fragment in: " + line;
    }
}
