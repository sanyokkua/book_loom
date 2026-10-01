package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BookFixtures;
import ua.bookloom.ui.FakeProviderConfigs;
import ua.bookloom.ui.SessionInfo;

/**
 * What the window tells the detailed log about its work: the opened book by file name, the run's provider, endpoint
 * host, model and brief — and nothing a shared log must not carry (a folder, a URL's user part or query).
 */
class SessionReporterTest {

    private final SessionInfo session = new SessionInfo();
    private final CurrentProject current = new CurrentProject();
    private final FakeProviderConfigs configs = new FakeProviderConfigs();

    @Test
    void bookOpened_aBookInAPrivateFolder_recordsItsFileNameFormatAndSizeOnly() {
        new SessionReporter(session, current, configs);
        final ImportedBook imported = BookFixtures.frankensteinImport();

        current.open(new OpenedBook(
                "p1",
                Path.of("/Users/someone/private/Frankenstein.epub"),
                imported.inspection(),
                imported.profile(),
                BookBrief.defaults("en")));

        assertThat(session.snapshot())
                .containsExactly(
                        Map.entry("book", "Frankenstein.epub"),
                        Map.entry("format", "EPUB"),
                        Map.entry("segments", "9"),
                        Map.entry("words", "0"));
    }

    @Test
    void runStarting_aPresetProvider_recordsProviderHostModelAndBrief() {
        final SessionReporter reporter = new SessionReporter(session, current, configs);

        reporter.runStarting(
                new RunContext(
                        "p1",
                        "Frankenstein.epub",
                        ReviewMode.ASSISTED,
                        QualityDial.BALANCED,
                        new ModelSelection("ollama", "gemma4:e4b")),
                BookBrief.defaults("en").withLanguages("en", "uk"));

        assertThat(SessionInfo.format(session.snapshot()))
                .isEqualTo("provider=ollama providerKind=OLLAMA endpointHost=localhost:11434 model=gemma4:e4b"
                        + " dial=BALANCED reviewMode=ASSISTED sourceLanguage=en targetLanguage=uk register=NEUTRAL"
                        + " names=TRANSLITERATE foreignPassages=KEEP footnotes=TRANSLATE units=KEEP balance=55");
    }

    @Test
    void runStarting_anEndpointWithUserPartPathAndQuery_recordsTheHostAndPortOnly() {
        configs.register(new ProviderConfig(
                "custom",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("https://user:pass@llm.example.org:8443/v1?key=abc"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
        final SessionReporter reporter = new SessionReporter(session, current, configs);

        reporter.runStarting(
                new RunContext(
                        "p1", "Book.md", ReviewMode.UNATTENDED, QualityDial.FAST, new ModelSelection("custom", "m")),
                BookBrief.defaults("en"));

        assertThat(session.snapshot()).containsEntry("endpointHost", "llm.example.org:8443");
        assertThat(SessionInfo.format(session.snapshot()))
                .doesNotContain("pass")
                .doesNotContain("key=abc");
    }
}
