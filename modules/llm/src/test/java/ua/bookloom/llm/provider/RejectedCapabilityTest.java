package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The stored name of a refused setting is stable and maps back to the same capability. */
class RejectedCapabilityTest {

    // THEN every capability's stored name finds the capability again.
    @ParameterizedTest
    @EnumSource(RejectedCapability.class)
    void fromSettingName_nameOfEveryCapability_roundTrips(final RejectedCapability capability) {
        assertThat(RejectedCapability.fromSettingName(capability.settingName())).hasValue(capability);
    }

    // The names are written to the database, so they are fixed strings, not enum names.
    @ParameterizedTest
    @CsvSource({
        "TEMPERATURE,temperature,omitted",
        "TOP_P,top_p,omitted",
        "TOP_K,top_k,omitted",
        "MIN_P,min_p,omitted",
        "REPEAT_PENALTY,repeat_penalty,omitted",
        "SEED,seed,omitted",
        "NUM_CTX,num_ctx,omitted",
        "REASONING_CONTROL,reasoning,omitted",
        "STRUCTURED_OUTPUT,response_format,omitted",
        "OUTPUT_FIELD,max_tokens,max_completion_tokens"
    })
    void settingName_eachCapability_isTheWireNameAndInsteadCode(
            final RejectedCapability capability, final String name, final String instead) {
        assertThat(capability.settingName()).isEqualTo(name);
        assertThat(capability.insteadDid()).isEqualTo(instead);
    }

    @Test
    void fromSettingName_unknownName_isEmpty() {
        assertThat(RejectedCapability.fromSettingName("frequency_penalty")).isEmpty();
    }
}
