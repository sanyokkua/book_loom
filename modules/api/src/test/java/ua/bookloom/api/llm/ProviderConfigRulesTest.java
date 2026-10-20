package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.ErrorCode;

/** The provider-description rules: endpoint, timeouts, overrides, headers and sign-in. */
class ProviderConfigRulesTest {

    @ParameterizedTest
    @CsvSource({"relative:11434", "ftp://host/v1", "http:///nohost"})
    void check_badBaseUrl_isValidationError(final String url) {
        final ProviderConfigRules.Check check = ProviderConfigRules.check(config(url, ProviderKind.OLLAMA));

        assertThat(check.errors()).extracting("code").containsExactly(ErrorCode.validation);
    }

    @ParameterizedTest
    @CsvSource({"0,10", "-1,10", "10,0", "10,-5"})
    void check_nonPositiveTimeout_isValidationError(final long connect, final long request) {
        final ProviderConfig config = new ProviderConfig(
                "p",
                ProviderKind.OLLAMA,
                URI.create("http://localhost:11434"),
                Duration.ofSeconds(connect),
                Duration.ofSeconds(request));

        assertThat(ProviderConfigRules.check(config).errors()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"http://host:1234,true", "http://host:1234/v1,false", "http://host/v1/,false", "https://h/api/v1,false"
    })
    void check_openAiBaseUrl_warnsOnlyWithoutV1(final String url, final boolean warns) {
        final ProviderConfigRules.Check check = ProviderConfigRules.check(config(url, ProviderKind.OPENAI_COMPATIBLE));

        assertThat(check.isValid()).isTrue();
        assertThat(check.warnings().isEmpty()).isEqualTo(!warns);
    }

    @Test
    void check_ollamaWithoutV1_doesNotWarn() {
        assertThat(ProviderConfigRules.check(config("http://host:11434", ProviderKind.OLLAMA))
                        .warnings())
                .isEmpty();
    }

    @Test
    void check_ollamaChatOverride_isValidationError() {
        final ProviderConfig config =
                config("http://host:11434", ProviderKind.OLLAMA).withChatPath("/x");

        assertThat(ProviderConfigRules.check(config).errors())
                .extracting("code")
                .containsExactly(ErrorCode.validation);
    }

    @Test
    void check_openAiChatOverride_isAccepted() {
        final ProviderConfig config =
                config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE).withChatPath("/custom/chat");

        assertThat(ProviderConfigRules.check(config).isValid()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "X-Api-Key|sk-1|false",
                "X-Api-Key|${env:LMS_KEY}|true",
                "X-Monkey|abc|false",
                "Authorization|Bearer abc|false",
                "authorization|${env:TOKEN_A}|true",
                "X-Auth-Token|${env:}|false",
                "X-Secret|x${env:A}|false",
                "X-Trace|abc|true",
                "Accept|application/json|true"
            })
    void check_header_requiresEnvReferenceForCredentialLikeNames(
            final String name, final String value, final boolean accepted) {
        final ProviderConfig config =
                config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE).withHeaders(Map.of(name, value));

        assertThat(ProviderConfigRules.check(config).isValid()).isEqualTo(accepted);
    }

    @Test
    void check_refusedHeader_doesNotEchoTheValue() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withHeaders(Map.of("X-Api-Key", "sk-live-123"));

        assertThat(ProviderConfigRules.check(config).errors().toString()).doesNotContain("sk-live-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BEARER", "API_KEY"})
    void check_signedInWithoutCredentialRef_isMissingCredential(final String auth) {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.valueOf(auth))
                .withApiKeyHeader("X-Api-Key");

        assertThat(ProviderConfigRules.check(config).errors())
                .extracting("code")
                .containsExactly(ErrorCode.missingCredential);
    }

    @Test
    void check_apiKeyWithoutHeaderName_isMissingCredential() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.API_KEY)
                .withCredentialRef("LMS_KEY");

        assertThat(ProviderConfigRules.check(config).errors())
                .extracting("code")
                .containsExactly(ErrorCode.missingCredential);
    }

    @Test
    void check_bearerWithEnvName_isValid() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef("LMS_KEY");

        assertThat(ProviderConfigRules.check(config).isValid()).isTrue();
    }

    @Test
    void check_noAuth_needsNoCredential() {
        assertThat(ProviderConfigRules.check(config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE))
                        .isValid())
                .isTrue();
    }

    private static ProviderConfig config(final String url, final ProviderKind kind) {
        return new ProviderConfig(
                "p",
                kind,
                URI.create(url),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
    }
}
