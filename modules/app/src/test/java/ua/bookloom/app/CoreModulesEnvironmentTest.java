package ua.bookloom.app;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.EnvironmentVariables;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.app.bootstrap.ReviewModeResolver;
import ua.bookloom.app.env.OsEnvironment;
import ua.bookloom.persistence.InMemoryPersistenceModule;
import ua.bookloom.util.paths.AppEnvironment;
import ua.bookloom.util.paths.AppPaths;

/**
 * The composed graph uses one variable lookup for the request headers and the form's indicator: a key that only the
 * operating system's store holds (as after {@code launchctl setenv}) is both shown as set and sent.
 */
class CoreModulesEnvironmentTest {

    private static final String KEY_VARIABLE = "BL_OS_ONLY_KEY";

    @TempDir
    private Path dataDir;

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void startServer() {
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void graph_keyOnlyInTheOsStore_isSetInTheFormAndSentAsBearer() throws Exception {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson("{\"data\":[]}")));
        final OsEnvironment os = new OsEnvironment() {
            @Override
            public Optional<String> read(final String name) {
                return Optional.ofNullable(Map.of(KEY_VARIABLE, "sk-from-os").get(name));
            }

            @Override
            public String source() {
                return "fake";
            }
        };
        final Injector injector = Guice.createInjector(new CoreModules(
                startup(),
                new InMemoryPersistenceModule(),
                new SystemEnvironmentVariables(name -> null, os, System::nanoTime)));

        final boolean set = injector.getInstance(EnvironmentVariables.class).isSet(KEY_VARIABLE);
        final var report =
                injector.getInstance(ProviderVerifier.class).verify(provider(), "", VerificationPolicy.CONNECTION);

        assertThat(set).isTrue();
        assertThat(report.isOk()).as("report: " + report.error()).isTrue();
        server.verify(
                getRequestedFor(urlEqualTo("/v1/models")).withHeader("Authorization", equalTo("Bearer sk-from-os")));
    }

    private ProviderConfig provider() {
        return new ProviderConfig(
                        "remote",
                        ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + "/v1"),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef(KEY_VARIABLE);
    }

    private StartupContext startup() throws Exception {
        final Path logDir = Files.createDirectories(dataDir.resolve("logs"));
        return new StartupContext(
                AppPaths.of(dataDir, logDir),
                AppEnvironment.DEV,
                ReviewModeResolver.resolve(name -> null, name -> null));
    }
}
