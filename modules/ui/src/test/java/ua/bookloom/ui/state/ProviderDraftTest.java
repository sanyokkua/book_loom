package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.ui.i18n.MessageKey;

/** The text the Providers form edits, turned into a provider description and into findings. */
class ProviderDraftTest {

    private static ProviderDraft officeLm() {
        return ProviderDraft.blank(ProviderKind.OPENAI_COMPATIBLE)
                .withName("Office LM")
                .withAddress("http://10.0.0.5:1234/v1");
    }

    private static ProviderConfig config(final ProviderDraft draft, final String id) {
        return Objects.requireNonNull(draft.toConfig(id).data());
    }

    @Test
    void blank_ollama_prefillsLocalhost11434() {
        final ProviderDraft draft = ProviderDraft.blank(ProviderKind.OLLAMA);

        assertThat(draft.address()).isEqualTo("http://localhost:11434");
        assertThat(draft.kind()).isEqualTo(ProviderKind.OLLAMA);
        assertThat(draft.name()).isEmpty();
        assertThat(draft.connectSeconds()).isEqualTo("10");
        assertThat(draft.requestSeconds()).isEqualTo("180");
    }

    @Test
    void withKind_toOpenAi_setsPresetAndClearsOverrides() {
        final ProviderDraft draft = ProviderDraft.blank(ProviderKind.OLLAMA)
                .withModelsPath("/m")
                .withChatPath("/c")
                .withKind(ProviderKind.OPENAI_COMPATIBLE);

        assertThat(draft.address()).isEqualTo("http://localhost:1234/v1");
        assertThat(draft.modelsPath()).isEmpty();
        assertThat(draft.chatPath()).isEmpty();
    }

    @Test
    void withKind_customAddress_isKept() {
        final ProviderDraft draft = officeLm().withKind(ProviderKind.OLLAMA);

        assertThat(draft.address()).isEqualTo("http://10.0.0.5:1234/v1");
    }

    @Test
    void toConfig_officeLm_describesTheProvider() {
        final ProviderConfig config = config(officeLm().withConnectSeconds("5"), "office-lm");

        assertThat(config.id()).isEqualTo("office-lm");
        assertThat(config.displayName()).isEqualTo("Office LM");
        assertThat(config.kind()).isEqualTo(ProviderKind.OPENAI_COMPATIBLE);
        assertThat(config.baseUrl()).isEqualTo(URI.create("http://10.0.0.5:1234/v1"));
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofMinutes(3));
        assertThat(config.auth()).isEqualTo(ProviderAuth.NONE);
        assertThat(config.headers()).isEmpty();
    }

    @Test
    void of_thenToConfig_roundTripsEveryField() {
        final ProviderConfig original = new ProviderConfig(
                "x",
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create("http://h:1/v1"),
                Duration.ofSeconds(7),
                Duration.ofSeconds(240),
                "Mine",
                ProviderAuth.API_KEY,
                "X-Api-Key",
                "MY_KEY",
                "/models",
                "/chat",
                java.util.Map.of("X-Team", "blue", "X-Auth", "${env:TEAM_TOKEN}"),
                List.of("custom-a"));

        assertThat(config(ProviderDraft.of(original, "m"), "x")).isEqualTo(original);
        assertThat(ProviderDraft.of(original, "m").model()).isEqualTo("m");
    }

    @Test
    void toConfig_customModelsAndHeaderRows_dropBlanksAndDuplicates() {
        final ProviderDraft draft = officeLm()
                .withCustomModels(List.of("a", " ", "a", "b"))
                .withHeaders(
                        List.of(new ProviderDraft.HeaderRow("X-Team", "blue"), new ProviderDraft.HeaderRow("", "")));

        final ProviderConfig config = config(draft, "p");

        assertThat(config.customModels()).containsExactly("a", "b");
        assertThat(config.headers()).containsOnlyKeys("X-Team");
    }

    @Test
    void findings_openAiWithoutV1_warnsButDoesNotBlock() {
        final ProviderDraft draft = officeLm().withAddress("http://10.0.0.5:1234");

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_FORM_ADDRESS_V1_WARNING, List.of(), ProviderDraft.Field.ADDRESS, false));
        assertThat(draft.toConfig("p").isOk()).isTrue();
    }

    @Test
    void findings_ollamaWithoutV1_hasNone() {
        assertThat(ProviderDraft.blank(ProviderKind.OLLAMA).withName("Local").findings())
                .isEmpty();
    }

    @Test
    void findings_credentialHeaderLiteral_blocksNamingTheHeaderOnly() {
        final ProviderDraft draft = officeLm().withHeaders(List.of(new ProviderDraft.HeaderRow("X-Api-Key", "sk-1")));

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_HEADER_LITERAL,
                        List.of("X-Api-Key"),
                        ProviderDraft.Field.HEADERS,
                        true));
        final AppError error = Objects.requireNonNull(draft.toConfig("p").error());
        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.toString()).doesNotContain("sk-1");
    }

    @Test
    void findings_envReferenceHeader_isAccepted() {
        final ProviderDraft draft =
                officeLm().withHeaders(List.of(new ProviderDraft.HeaderRow("X-Api-Key", "${env:LMS_KEY}")));

        assertThat(draft.findings()).isEmpty();
    }

    @Test
    void findings_nonCredentialLiteralHeader_isAccepted() {
        final ProviderDraft draft = officeLm().withHeaders(List.of(new ProviderDraft.HeaderRow("X-Team", "blue")));

        assertThat(draft.findings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-3", "1.5", ""})
    void findings_badTimeout_blocksOnTheTimeoutField(final String text) {
        final ProviderDraft draft = officeLm().withRequestSeconds(text);

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_NUMBER, List.of(), ProviderDraft.Field.REQUEST_TIMEOUT, true));
        assertThat(draft.toConfig("p").isErr()).isTrue();
    }

    // IF a timeout of years could be saved, THEN the request's timeout would overflow when the call is made.
    @ParameterizedTest
    @ValueSource(strings = {"3601", "99999999999"})
    void findings_timeoutOverAnHour_blocksOnTheTimeoutField(final String text) {
        final ProviderDraft draft = officeLm().withConnectSeconds(text);

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_TIMEOUT_TOO_LONG,
                        List.of(),
                        ProviderDraft.Field.CONNECT_TIMEOUT,
                        true));
    }

    @Test
    void findings_timeoutOfExactlyAnHour_isAccepted() {
        assertThat(officeLm().withRequestSeconds("3600").findings()).isEmpty();
    }

    // IF a header name with a space or a colon could be saved, THEN every request would fail as a transport error.
    @ParameterizedTest
    @ValueSource(strings = {"X Team", "X-Team:"})
    void findings_headerNameNotAToken_blocksNamingIt(final String name) {
        final ProviderDraft draft = officeLm().withHeaders(List.of(new ProviderDraft.HeaderRow(name, "blue")));

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_HEADER_NAME_INVALID,
                        List.of(name),
                        ProviderDraft.Field.HEADERS,
                        true));
    }

    @Test
    void findings_keyHeaderNameWithASpace_blocksOnTheKeyHeaderField() {
        final ProviderDraft draft = officeLm()
                .withAuth(ProviderAuth.API_KEY)
                .withCredentialRef("LMS_KEY")
                .withApiKeyHeader("X Api Key");

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_HEADER_NAME_INVALID,
                        List.of("X Api Key"),
                        ProviderDraft.Field.KEY_HEADER,
                        true));
    }

    @Test
    void findings_blankName_blocksOnTheNameField() {
        assertThat(officeLm().withName("  ").findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_NAME, List.of(), ProviderDraft.Field.NAME, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not a url", "ftp://host/v1", "relative:11434"})
    void findings_badAddress_blocksOnTheAddressField(final String address) {
        assertThat(officeLm().withAddress(address).findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_ADDRESS, List.of(), ProviderDraft.Field.ADDRESS, true));
    }

    @Test
    void findings_bearerWithoutVariable_blocksOnTheVariableField() {
        final ProviderDraft draft = officeLm().withAuth(ProviderAuth.BEARER);

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_REF_MISSING, List.of(), ProviderDraft.Field.KEY_VARIABLE, true));
    }

    @Test
    void findings_keyPastedAsVariable_blocksWithoutEchoingIt() {
        final ProviderDraft draft = officeLm().withAuth(ProviderAuth.BEARER).withCredentialRef("sk-123");

        assertThat(draft.findings())
                .extracting(ProviderDraft.FormFinding::key)
                .containsExactly(MessageKey.SETTINGS_ERROR_REF_NOT_NAME);
        assertThat(draft.findings().toString()).doesNotContain("sk-123");
    }

    // IF a shell reference were refused only as "not a name" (LAN-5), THEN the person would not see what to type.
    @ParameterizedTest
    @ValueSource(strings = {"$LMS_KEY", "${LMS_KEY}", "${env:LMS_KEY}", "%LMS_KEY%"})
    void findings_variableWrittenAsAReference_blocksNamingTheBareName(final String typed) {
        final ProviderDraft draft = officeLm().withAuth(ProviderAuth.BEARER).withCredentialRef(typed);

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_REF_REFERENCE,
                        List.of("LMS_KEY"),
                        ProviderDraft.Field.KEY_VARIABLE,
                        true));
    }

    // IF the API-key header started empty (LAN-3), THEN the form would block until the person guessed a name.
    @Test
    void withSignIn_apiKeyWithNoHeader_defaultsTheHeaderToXApiKey() {
        assertThat(officeLm().withSignIn(ProviderAuth.API_KEY).apiKeyHeader()).isEqualTo("X-Api-Key");
    }

    @Test
    void withSignIn_apiKeyWithAHeader_keepsIt() {
        final ProviderDraft draft = officeLm().withApiKeyHeader("api-key").withSignIn(ProviderAuth.API_KEY);

        assertThat(draft.apiKeyHeader()).isEqualTo("api-key");
        assertThat(draft.auth()).isEqualTo(ProviderAuth.API_KEY);
    }

    @Test
    void withSignIn_bearer_leavesTheHeaderAlone() {
        assertThat(officeLm().withSignIn(ProviderAuth.BEARER).apiKeyHeader()).isEmpty();
    }

    @Test
    void findings_apiKeyWithoutHeader_blocksOnTheHeaderField() {
        final ProviderDraft draft = officeLm().withAuth(ProviderAuth.API_KEY).withCredentialRef("LMS_KEY");

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_KEY_HEADER_MISSING, List.of(), ProviderDraft.Field.KEY_HEADER, true));
    }

    @Test
    void findings_ollamaChatPath_blocksOnTheChatPathField() {
        final ProviderDraft draft =
                ProviderDraft.blank(ProviderKind.OLLAMA).withName("L").withChatPath("/x");

        assertThat(draft.findings())
                .containsExactly(new ProviderDraft.FormFinding(
                        MessageKey.SETTINGS_ERROR_OLLAMA_CHAT_PATH, List.of(), ProviderDraft.Field.CHAT_PATH, true));
    }

    @Test
    void credentialStatus_followsSignInAndEnvironment() {
        final ProviderDraft bearer = officeLm().withAuth(ProviderAuth.BEARER).withCredentialRef("LMS_KEY");

        assertThat(bearer.credentialStatus(name -> true)).isEqualTo(ProviderDraft.CredentialStatus.SET);
        assertThat(bearer.credentialStatus(name -> false)).isEqualTo(ProviderDraft.CredentialStatus.NOT_SET);
        assertThat(officeLm().credentialStatus(name -> true)).isEqualTo(ProviderDraft.CredentialStatus.NONE);
    }

    // IF an unparsable address hid the header checks, THEN a key typed into a header would show only after the address.
    @Test
    void findings_addressUnparsableAndHeaderLooksLikeAKey_reportsBoth() {
        final ProviderDraft draft = officeLm()
                .withAddress("http://bad host")
                .withHeaders(List.of(new ProviderDraft.HeaderRow("X-Api-Key", "sk-1")));

        assertThat(draft.findings())
                .extracting(ProviderDraft.FormFinding::key)
                .contains(MessageKey.SETTINGS_ERROR_ADDRESS, MessageKey.SETTINGS_ERROR_HEADER_LITERAL);
    }

    // IF two rows of one header were stored, THEN the later would silently replace the earlier.
    @Test
    void findings_sameHeaderTwice_blocksNamingIt() {
        final ProviderDraft draft = officeLm()
                .withHeaders(List.of(
                        new ProviderDraft.HeaderRow("X-Team", "a"), new ProviderDraft.HeaderRow("x-team", "b")));

        assertThat(draft.findings())
                .filteredOn(ProviderDraft.FormFinding::blocking)
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.key()).isEqualTo(MessageKey.SETTINGS_ERROR_HEADER_DUPLICATE);
                    assertThat(finding.args()).containsExactly("x-team");
                });
    }

    // IF a value with no header name were dropped quietly, THEN a person would think it was saved.
    @Test
    void findings_valueWithoutAName_blocks() {
        final ProviderDraft draft = officeLm().withHeaders(List.of(new ProviderDraft.HeaderRow("", "abc")));

        assertThat(draft.findings())
                .extracting(ProviderDraft.FormFinding::key)
                .containsExactly(MessageKey.SETTINGS_ERROR_HEADER_NAME_MISSING);
    }
}
