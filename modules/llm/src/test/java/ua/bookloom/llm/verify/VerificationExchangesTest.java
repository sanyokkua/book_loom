package ua.bookloom.llm.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationStage;

/** An address saved before user names were refused never puts them into the test dialog or its curl line. */
class VerificationExchangesTest {

    @ParameterizedTest
    @CsvSource({
        "http://user:pass@127.0.0.1:11434,http://127.0.0.1:11434/api/version",
        "http://user@127.0.0.1:11434/,http://127.0.0.1:11434/api/version",
        "http://127.0.0.1:11434,http://127.0.0.1:11434/api/version"
    })
    void connection_storedAddressWithUserInfo_showsTheUrlWithoutIt(final String address, final String shown) {
        final ProviderConfig config = new ProviderConfig(
                "old",
                ProviderKind.OLLAMA,
                URI.create(address),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

        final String url = VerificationExchanges.connection(
                        config, new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null))
                .url();

        assertThat(url).isEqualTo(shown);
    }
}
