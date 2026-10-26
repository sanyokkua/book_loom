package ua.bookloom.persistence.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;

/** Behaviour every {@link ProviderConfigs} adapter must show, whatever it starts with. */
public abstract class ProviderConfigsContractTest {

    protected abstract ProviderConfigs configs();

    protected static ProviderConfig sample(final String id) {
        return new ProviderConfig(
                id,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://localhost:1234/v1"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }

    @Test
    void save_newId_isFoundAndListed() {
        final ProviderConfig saved = sample("contract-new");

        assertThat(configs().save(saved).data()).isEqualTo(saved);

        assertThat(configs().find("contract-new")).contains(saved);
        assertThat(configs().all()).contains(saved);
    }

    @Test
    void save_existingId_replacesIt() {
        configs().save(sample("contract-same"));
        final ProviderConfig replacement = new ProviderConfig(
                "contract-same",
                ProviderKind.OLLAMA,
                URI.create("http://10.0.0.5:11434"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(30));
        final int before = configs().all().size();

        assertThat(configs().save(replacement).data()).isEqualTo(replacement);

        assertThat(configs().find("contract-same")).contains(replacement);
        assertThat(configs().all()).hasSize(before);
    }

    @Test
    void save_fullyDescribedProvider_isReturnedUnchanged() {
        final ProviderConfig full = sample("contract-full")
                .withDisplayName("Work server")
                .withAuth(ProviderAuth.API_KEY)
                .withApiKeyHeader("X-Api-Key")
                .withCredentialRef("LMS_KEY")
                .withModelsPath("/models")
                .withChatPath("/chat")
                .withHeaders(Map.of("X-Team", "a"))
                .withCustomModels(java.util.List.of("m1", "m2"));

        configs().save(full);

        assertThat(configs().find("contract-full")).contains(full);
    }

    @Test
    void find_unknownId_returnsEmpty() {
        assertThat(configs().find("gemini")).isEmpty();
    }

    @Test
    void delete_knownId_removesItAndReportsTrue() {
        configs().save(sample("contract-del"));

        assertThat(configs().delete("contract-del").data()).isTrue();

        assertThat(configs().find("contract-del")).isEmpty();
    }

    @Test
    void delete_unknownId_reportsFalseAndChangesNothing() {
        final int before = configs().all().size();

        assertThat(configs().delete("gemini").data()).isFalse();
        assertThat(configs().all()).hasSize(before);
    }

    @Test
    void save_relativeBaseUrl_isRefusedAndNotStored() {
        assertRefused(new ProviderConfig(
                "relative",
                ProviderKind.OLLAMA,
                URI.create("localhost:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT));
    }

    @Test
    void save_zeroTimeout_isRefusedAndNotStored() {
        assertRefused(new ProviderConfig(
                "zero-timeout",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(2),
                Duration.ZERO));
    }

    @Test
    void save_apiKeyWithoutCredentialRef_returnsMissingCredential() {
        final ProviderConfig invalid = sample("keyed").withAuth(ProviderAuth.BEARER);

        assertThat(Objects.requireNonNull(configs().save(invalid).error()).code())
                .isEqualTo(ErrorCode.missingCredential);
        assertThat(configs().find("keyed")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "X-Api-Key", "x-secret-token"})
    void save_literalKeyInCredentialLikeHeader_isRefusedAndNotStored(final String header) {
        final ProviderConfig literal = sample("literal").withHeaders(Map.of(header, "sk-live-123"));

        final Result<ProviderConfig> result = configs().save(literal);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(String.valueOf(result.error())).doesNotContain("sk-live-123");
        assertThat(configs().find("literal")).isEmpty();
    }

    @Test
    void save_envReferenceInCredentialLikeHeader_isAccepted() {
        final ProviderConfig referenced = sample("referenced").withHeaders(Map.of("Authorization", "${env:LMS_KEY}"));

        assertThat(configs().save(referenced).isOk()).isTrue();

        assertThat(configs().find("referenced")).contains(referenced);
    }

    @Test
    void save_literalSecretAsCredentialRef_isRefusedNotStoredAndNeverWrittenOut() {
        final ProviderConfig literal =
                sample("literal-ref").withAuth(ProviderAuth.BEARER).withCredentialRef("sk-test-123");

        final Result<ProviderConfig> result;
        final String logged;
        try (LogCapture logs = LogCapture.start()) {
            result = configs().save(literal);
            logged = logs.text();
        }

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(String.valueOf(result.error())).doesNotContain("sk-test-123");
        assertThat(logged).doesNotContain("sk-test-123");
        assertThat(configs().find("literal-ref")).isEmpty();
    }

    // A key whose characters happen to make a valid variable name is still a key; the store refuses it on its own,
    // even when no form judged it first.
    @ParameterizedTest
    @ValueSource(strings = {"sk_live_51HabcDEF123ghiJKL456", "ghp_a1B2c3D4e5F6g7H8i9J0", "AKIAIOSFODNN7EXAMPLE"})
    void save_keyShapedCredentialRef_isRefusedAndNotStored(final String key) {
        assertRefused(sample("key-shaped").withAuth(ProviderAuth.BEARER).withCredentialRef(key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://user:pass@127.0.0.1:11434", "http://127.0.0.1:0", "http://127.0.0.1:65536"})
    void save_addressWithCredentialsOrBadPort_isRefusedAndNotStored(final String address) {
        assertRefused(sample("bad-address").withBaseUrl(URI.create(address)));
    }

    @Test
    void save_environmentVariableNameAsCredentialRef_isAccepted() {
        final ProviderConfig referenced =
                sample("named-ref").withAuth(ProviderAuth.BEARER).withCredentialRef("LMS_KEY");

        assertThat(configs().save(referenced).isOk()).isTrue();

        assertThat(configs().find("named-ref")).contains(referenced);
    }

    private void assertRefused(final ProviderConfig invalid) {
        final Result<ProviderConfig> result = configs().save(invalid);

        assertThat(Objects.requireNonNull(result.error()).code()).isEqualTo(ErrorCode.validation);
        assertThat(configs().find(invalid.id())).isEmpty();
    }
}
