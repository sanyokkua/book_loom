package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.LanguageAdvice;
import ua.bookloom.api.llm.LanguageState;
import ua.bookloom.api.llm.ModelNotes;
import ua.bookloom.api.llm.ModelReport;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.knowledge.LanguageAdvisor;
import ua.bookloom.llm.knowledge.ModelKnowledgeFiles;
import ua.bookloom.llm.knowledge.QuantAdvisor;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.profile.ProfileRows;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** The notes, the language advice and the facts date of the model report, at the HTTP seam. */
class ModelCapabilityAdviceTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final String SHOW_131072 =
            "{\"model_info\":{\"general.architecture\":\"gemma3\",\"gemma3.context_length\":131072}}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;
    private ModelCapabilityService capabilities;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        capabilities = new ModelCapabilityService(
                configs,
                new ProviderClientFactory(
                        new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime),
                new ModelProfiles(ProfileRows.NONE),
                new ModelSettingsCache(new FakeModelSettingsRepository()),
                new QuantAdvisor(new ModelKnowledgeFiles()),
                new LanguageAdvisor(new ModelKnowledgeFiles()));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void report_ollamaGemma_carriesNotesWithSizedLabelAndServerQuant() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"details\":{\"quantization_level\":\"Q8_0\"},\"model_info\":"
                        + "{\"general.architecture\":\"gemma3\",\"gemma3.context_length\":131072}}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));

        final ModelReport report =
                capabilities.report(new ModelSelection("ollama", "gemma4:e4b")).data();
        assertThat(report).isNotNull();
        final ModelNotes notes = report.notes();

        assertThat(notes).isNotNull();
        assertThat(notes.label()).isEqualTo("Gemma 4 E4B");
        assertThat(notes.familyLabel()).isEqualTo("Gemma 4");
        assertThat(notes.serverQuant()).isEqualTo("Q8_0");
        assertThat(notes.libraryTag()).isNull();
        assertThat(notes.quantMeasured()).isFalse();
    }

    @Test
    void factsDate_bundledTables_isTheDataVersion() {
        assertThat(capabilities.factsDate()).isEqualTo("2026-10-28");
    }

    @Test
    void languageAdvice_ruToUkOnLlama31_warnsForBothSides() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));

        final LanguageAdvice advice =
                capabilities.languageAdvice(new ModelSelection("ollama", "llama3.1:8b"), "ru", "uk");

        assertThat(advice.sourceState()).isEqualTo(LanguageState.NOT_DECLARED);
        assertThat(advice.targetState()).isEqualTo(LanguageState.NOT_DECLARED);
        assertThat(advice.familyLabel()).isEqualTo("Llama 3.1");
    }

    // IF advice read the server, THEN building a screen that shows it would call the provider nobody asked.
    @Test
    void languageAdvice_noFactsHeld_asksTheServerNothing() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show")).willReturn(okJson(SHOW_131072)));

        capabilities.languageAdvice(new ModelSelection("ollama", "llama3.1:8b"), "ru", "uk");

        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void heldReport_noFactsHeld_readsTheNameAndAsksNothing() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());

        final ModelReport report = capabilities
                .heldReport(new ModelSelection("ollama", "gemma4:e4b-Q4_K_M"))
                .data();

        assertThat(report).isNotNull();
        assertThat(report.notes()).isNotNull();
        assertThat(report.notes().label()).isEqualTo("Gemma 4 E4B");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    // One person's selection is one read of the server: what report() read is what the advice reads afterwards.
    @Test
    void heldReport_afterReport_usesTheHeldFactsWithNoSecondRead() {
        register("ollama", ProviderKind.OLLAMA, server.baseUrl());
        server.stubFor(post(urlEqualTo("/api/show"))
                .willReturn(okJson("{\"details\":{\"quantization_level\":\"Q8_0\"},\"model_info\":"
                        + "{\"general.architecture\":\"gemma3\",\"gemma3.context_length\":131072}}")));
        server.stubFor(get(urlEqualTo("/api/ps")).willReturn(okJson("{\"models\":[]}")));
        final ModelSelection gemma = new ModelSelection("ollama", "gemma4:e4b");

        capabilities.report(gemma);
        final ModelReport held = capabilities.heldReport(gemma).data();
        capabilities.languageAdvice(gemma, "en", "uk");

        assertThat(held).isNotNull();
        assertThat(held.contextLength()).isEqualTo(131072);
        assertThat(server.getAllServeEvents())
                .as("one /api/show and one /api/ps, from report() alone")
                .hasSize(2);
    }

    @Test
    void heldReport_unknownProvider_isValidationError() {
        assertThat(capabilities.heldReport(new ModelSelection("nobody", "m")).isErr())
                .isTrue();
    }

    @Test
    void languageAdvice_unknownProvider_isNeutral() {
        assertThat(capabilities.languageAdvice(new ModelSelection("nobody", "m"), "ru", "uk"))
                .isEqualTo(LanguageAdvice.NEUTRAL);
    }

    private void register(final String id, final ProviderKind kind, final String baseUrl) {
        assertThat(configs.save(new ProviderConfig(id, kind, URI.create(baseUrl), TIMEOUT, TIMEOUT))
                        .isOk())
                .isTrue();
    }
}
