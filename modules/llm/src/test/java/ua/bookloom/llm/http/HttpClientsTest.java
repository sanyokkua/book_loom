package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import com.google.inject.Injector;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import ua.bookloom.llm.LlmModule;

/** Verifies that connection-timeout variants are reused without sharing the wrong configuration. */
class HttpClientsTest {

    @Test
    void forConnectTimeout_sameTimeoutReusesConfiguredClient() {
        final HttpClients clients = new HttpClients();
        final Duration timeout = Duration.ofMillis(731);

        final var first = clients.forConnectTimeout(timeout);
        final var second = clients.forConnectTimeout(timeout);

        assertThat(second).isSameAs(first);
        assertThat(first.connectTimeout()).contains(timeout);
    }

    @Test
    void forConnectTimeout_differentTimeoutsBuildDistinctConfiguredClients() {
        final HttpClients clients = new HttpClients();
        final Duration firstTimeout = Duration.ofMillis(731);
        final Duration secondTimeout = Duration.ofMillis(947);

        final var first = clients.forConnectTimeout(firstTimeout);
        final var second = clients.forConnectTimeout(secondTimeout);

        assertThat(second).isNotSameAs(first);
        assertThat(first.connectTimeout()).contains(firstTimeout);
        assertThat(second.connectTimeout()).contains(secondTimeout);
    }

    @Test
    void llmModule_httpClientsProvidesOneSharedHolder() {
        final Injector injector = Guice.createInjector(new LlmModule());

        assertThat(injector.getInstance(HttpClients.class)).isSameAs(injector.getInstance(HttpClients.class));
    }
}
