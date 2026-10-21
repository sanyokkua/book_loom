package ua.bookloom.llm.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.ErrorCode;

/** Which discovery failures reach the caller with their own code. */
class DiscoveryErrorsTest {

    // IF discovery fails for a reason the person can act on, THEN its own code is kept, not degraded.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"auth", "unreachable", "timeout", "cancelled", "missingCredential"})
    void preserve_actionableCode_isKept(ErrorCode code) {
        assertThat(DiscoveryErrors.preserve(code)).isTrue();
    }

    // IF discovery fails with any other code, THEN it degrades to discoveryFailed.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"rateLimited", "modelNotFound", "upstream", "validation", "internal", "busy"})
    void preserve_otherCode_degrades(ErrorCode code) {
        assertThat(DiscoveryErrors.preserve(code)).isFalse();
    }
}
