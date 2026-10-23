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

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "LMS_KEY|BEARER|true",
                "_key1|API_KEY|true",
                "sk-test-123|BEARER|false",
                "1KEY|BEARER|false",
                "MY KEY|API_KEY|false",
                "${env:LMS_KEY}|BEARER|false",
                "sk-test-123|NONE|false"
            })
    void check_credentialRef_mustBeAnEnvironmentVariableName(
            final String reference, final String auth, final boolean accepted) {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.valueOf(auth))
                .withApiKeyHeader("X-Api-Key")
                .withCredentialRef(reference);

        assertThat(ProviderConfigRules.check(config).isValid()).isEqualTo(accepted);
    }

    @Test
    void check_refusedCredentialRef_isValidationAndDoesNotEchoTheValue() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef("sk-test-123");

        final ProviderConfigRules.Check check = ProviderConfigRules.check(config);

        assertThat(check.errors()).extracting("code").containsExactly(ErrorCode.validation);
        assertThat(check.errors().toString()).doesNotContain("sk-test-123");
    }

    @Test
    void check_noAuth_needsNoCredential() {
        assertThat(ProviderConfigRules.check(config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE))
                        .isValid())
                .isTrue();
    }

    @Test
    void findings_badBaseUrl_isBaseUrlInvalid() {
        assertThat(ProviderConfigRules.check(config("ftp://host/v1", ProviderKind.OLLAMA))
                        .findings())
                .containsExactly(new ProviderConfigRules.Finding(ProviderConfigRules.Rule.BASE_URL_INVALID, null));
    }

    @Test
    void findings_nonPositiveTimeout_isTimeoutNotPositive() {
        final ProviderConfig config = new ProviderConfig(
                "p", ProviderKind.OLLAMA, URI.create("http://h:1"), Duration.ZERO, Duration.ofSeconds(5));

        assertThat(ProviderConfigRules.check(config).findings())
                .extracting(ProviderConfigRules.Finding::rule)
                .containsExactly(ProviderConfigRules.Rule.TIMEOUT_NOT_POSITIVE);
    }

    @Test
    void findings_ollamaChatOverride_isOllamaChatPath() {
        final ProviderConfig config =
                config("http://host:11434", ProviderKind.OLLAMA).withChatPath("/x");

        assertThat(ProviderConfigRules.check(config).findings())
                .extracting(ProviderConfigRules.Finding::rule)
                .containsExactly(ProviderConfigRules.Rule.OLLAMA_CHAT_PATH);
    }

    @Test
    void findings_credentialHeaderLiteral_namesTheHeaderNotTheValue() {
        final ProviderConfig config =
                config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE).withHeaders(Map.of("X-Api-Key", "sk-1"));

        assertThat(ProviderConfigRules.check(config).findings())
                .containsExactly(new ProviderConfigRules.Finding(
                        ProviderConfigRules.Rule.CREDENTIAL_HEADER_LITERAL, "X-Api-Key"));
    }

    @Test
    void findings_referenceNotAName_isCredentialRefNotAName() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.BEARER)
                .withCredentialRef("sk-test-123");

        assertThat(ProviderConfigRules.check(config).findings())
                .containsExactly(
                        new ProviderConfigRules.Finding(ProviderConfigRules.Rule.CREDENTIAL_REF_NOT_A_NAME, null));
    }

    @Test
    void findings_bearerWithoutReference_isCredentialRefMissing() {
        final ProviderConfig config =
                config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE).withAuth(ProviderAuth.BEARER);

        assertThat(ProviderConfigRules.check(config).findings())
                .extracting(ProviderConfigRules.Finding::rule)
                .containsExactly(ProviderConfigRules.Rule.CREDENTIAL_REF_MISSING);
    }

    @Test
    void findings_apiKeyWithoutHeader_isApiKeyHeaderMissing() {
        final ProviderConfig config = config("http://host/v1", ProviderKind.OPENAI_COMPATIBLE)
                .withAuth(ProviderAuth.API_KEY)
                .withCredentialRef("LMS_KEY");

        assertThat(ProviderConfigRules.check(config).findings())
                .extracting(ProviderConfigRules.Finding::rule)
                .containsExactly(ProviderConfigRules.Rule.API_KEY_HEADER_MISSING);
    }

    @Test
    void findings_openAiWithoutV1_isV1SuffixMissingAndStillValid() {
        final ProviderConfigRules.Check check =
                ProviderConfigRules.check(config("http://host:1234", ProviderKind.OPENAI_COMPATIBLE));

        assertThat(check.isValid()).isTrue();
        assertThat(check.findings())
                .containsExactly(new ProviderConfigRules.Finding(ProviderConfigRules.Rule.V1_SUFFIX_MISSING, null));
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
