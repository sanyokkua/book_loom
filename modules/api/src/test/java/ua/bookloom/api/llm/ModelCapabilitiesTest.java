package ua.bookloom.api.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

class ModelCapabilitiesTest {

    private static final ModelSelection SELECTION = new ModelSelection("ollama", "gemma");

    @Test
    void detectedTokens_providerReportsALength_returnsIt() {
        final ModelCapabilities capabilities = (provider, model) -> Result.ok(new ContextLength(4096));

        assertThat(capabilities.detectedTokens(SELECTION)).contains(4096);
    }

    @Test
    void detectedTokens_providerSaysNothing_isEmpty() {
        final ModelCapabilities capabilities = (provider, model) -> Result.ok(ContextLength.unknown());

        assertThat(capabilities.detectedTokens(SELECTION)).isEmpty();
    }

    @Test
    void detectedTokens_detectionFails_isEmptyNotAnError() {
        final ModelCapabilities capabilities = (provider, model) ->
                Result.err(AppError.of(ErrorCode.validation, "Unknown provider", "The provider is not registered."));

        assertThat(capabilities.detectedTokens(SELECTION)).isEmpty();
    }

    @Test
    void report_default_carriesContextLengthAndNoLoaded() {
        final ModelCapabilities capabilities = (provider, model) -> Result.ok(new ContextLength(4096));

        final Result<ModelReport> report = capabilities.report(SELECTION);

        assertThat(report.data()).isEqualTo(ModelReport.windowsOnly(4096, null));
        assertThat(report.data()).extracting(ModelReport::verdict).isEqualTo("unknown model");
    }

    // A capability that cannot tell says the model needs no load of its own, so Start never shows a line by guess.
    @Test
    void loadsWithOwnSettings_default_isFalse() {
        final ModelCapabilities capabilities = (provider, model) -> Result.ok(ContextLength.unknown());

        assertThat(capabilities.loadsWithOwnSettings(SELECTION)).isFalse();
    }
}
