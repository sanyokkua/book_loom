package ua.bookloom.llm.engine;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** The recorded servers, each stubbed with its own reply bodies; every other path answers WireMock's 404. */
enum RecordedServers {
    OLLAMA {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/api/version")).willReturn(json(fixture("ollama-version.json"))));
            server.stubFor(get(urlEqualTo("/api/tags")).willReturn(json(fixture("ollama-tags.json"))));
            server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(fixture("ollama-v1-models.json"))));
        }
    },
    OLLAMA_WITHOUT_V1 {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/api/version")).willReturn(json(fixture("ollama-version.json"))));
            server.stubFor(get(urlEqualTo("/api/tags")).willReturn(json(fixture("ollama-tags.json"))));
        }
    },
    KOBOLDCPP {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/api/version")).willReturn(json("{\"version\":\"1.2.3\"}")));
            server.stubFor(get(urlEqualTo("/api/tags")).willReturn(json(fixture("koboldcpp-tags.json"))));
        }
    },
    LOCALAI {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/api/version")).willReturn(json(fixture("ollama-version.json"))));
            server.stubFor(get(urlEqualTo("/api/tags")).willReturn(json(fixture("ollama-tags.json"))));
            server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(fixture("localai-v1-models.json"))));
            server.stubFor(get(urlEqualTo("/engines/llama.cpp/v1/models"))
                    .willReturn(json(fixture("localai-v1-models.json"))));
        }
    },
    LM_STUDIO {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(anyUrl()).atPriority(10).willReturn(json(fixture("lmstudio-unexpected-endpoint.json"))));
            server.stubFor(get(urlEqualTo("/api/v0/models"))
                    .atPriority(1)
                    .willReturn(json(fixture("lmstudio-v0-models.json"))));
        }
    },
    LLAMA_SERVER {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(
                    get(urlEqualTo("/api/version")).willReturn(aResponse().withStatus(404)));
            server.stubFor(get(urlEqualTo("/health"))
                    .willReturn(json(fixture("llama-health.json")).withHeader("Server", "llama.cpp")));
            server.stubFor(get(urlEqualTo("/props")).willReturn(json(fixture("llama-props.json"))));
        }
    },
    LLAMA_ROUTER {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/health")).willReturn(json(fixture("llama-health.json"))));
            server.stubFor(get(urlEqualTo("/props")).willReturn(json(fixture("llama-props-router.json"))));
        }
    },
    UNSLOTH {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/api/health")).willReturn(json(fixture("unsloth-health.json"))));
        }
    },
    GPT4ALL {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(urlEqualTo("/v1/models")).willReturn(json(GPT4ALL_MODELS)));
        }
    },
    ERROR_BODY_EVERYWHERE {
        @Override
        void stub(final WireMockServer server) {
            server.stubFor(get(anyUrl()).willReturn(json(fixture("lmstudio-unexpected-endpoint.json"))));
        }
    },
    NOTHING_RECOGNISABLE {
        @Override
        void stub(final WireMockServer server) {
            // Every path answers WireMock's own 404.
        }
    };

    static final String GPT4ALL_MODELS =
            "{\"object\":\"list\",\"data\":[{\"id\":\"Llama 3 8B Instruct\",\"object\":\"model\",\"owned_by\":\"humanity\"}]}";
    private static final String FIXTURES = "/ua/bookloom/llm/engine/";

    abstract void stub(WireMockServer server);

    static ResponseDefinitionBuilder json(final String body) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(body);
    }

    static String fixture(final String name) {
        try (InputStream in = RecordedServers.class.getResourceAsStream(FIXTURES + name)) {
            return new String(Objects.requireNonNull(in, name).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
