package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/** Ports that predate draft checks answer them with a typed error, so older implementations need no change. */
class DraftPortDefaultsTest {

    private static final ProviderConfig DRAFT = new ProviderConfig(
            "draft",
            ProviderKind.OLLAMA,
            URI.create("http://localhost:11434"),
            Duration.ofSeconds(2),
            Duration.ofSeconds(3));

    // IF a verifier does not implement draft verification, THEN the default returns an internal error instead of
    // throwing.
    @Test
    void verifyDraft_defaultOnPortWithoutOverride_returnsTypedError() {
        final ProviderVerifier verifier = new ProviderVerifier() {
            @Override
            public Result<VerificationReport> verify(ModelSelection selection, VerificationPolicy policy) {
                return Result.err(AppError.of(ErrorCode.internal, "t", "m"));
            }

            @Override
            public Result<VerificationReport> verifyConnection(String providerId) {
                return Result.err(AppError.of(ErrorCode.internal, "t", "m"));
            }
        };

        final Result<VerificationReport> result = verifier.verify(DRAFT, "model-a", VerificationPolicy.PREFLIGHT);

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.internal);
    }

    // IF a catalog does not implement draft listing, THEN the default returns an internal error instead of throwing.
    @Test
    void listModelsDraft_defaultOnPortWithoutOverride_returnsTypedError() {
        final ModelCatalog catalog = providerId -> Result.ok(List.of());

        final Result<List<ModelInfo>> result = catalog.listModels(DRAFT);

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.internal);
    }

    // IF a caller builds an exchange without request headers, THEN the headers default to an empty string.
    @Test
    void verificationExchange_eightArgumentForm_defaultsRequestHeadersToEmpty() {
        final VerificationExchange exchange =
                new VerificationExchange("GET", "http://localhost/x", "", "", 200, null, null, false);

        assertThat(exchange.requestHeaders()).isEmpty();
    }

    // IF a caller supplies masked request headers, THEN the exchange keeps them.
    @Test
    void verificationExchange_withRequestHeaders_keepsThem() {
        final VerificationExchange exchange = new VerificationExchange(
                "GET", "http://localhost/x", "", "", 200, null, null, false, "Authorization: Bearer ••••");

        assertThat(exchange.requestHeaders()).isEqualTo("Authorization: Bearer ••••");
    }
}
