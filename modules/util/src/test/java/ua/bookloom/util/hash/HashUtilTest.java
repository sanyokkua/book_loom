package ua.bookloom.util.hash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * {@code HashUtil}'s two hash functions.
 */
class HashUtilTest {

    // the system SHALL compute a SHA-256 hash over the imported source file.
    @Test
    void sha256Hex_knownBytes_matchesKnownDigest() {
        final String hex = HashUtil.sha256Hex("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(hex).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void sha256Hex_sameBytesTwice_isIdentical() {
        final byte[] bytes = "same content".getBytes(StandardCharsets.UTF_8);

        assertThat(HashUtil.sha256Hex(bytes)).isEqualTo(HashUtil.sha256Hex(bytes));
    }

    @Test
    void sha256Hex_oneByteDifferent_isADifferentDigest() {
        assertThat(HashUtil.sha256Hex("a".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(HashUtil.sha256Hex("b".getBytes(StandardCharsets.UTF_8)));
    }

    // a separate SHA-256 hash is computed over each segment's pre-mask inner content.
    @Test
    void sha256OfNfcText_decomposedAndPrecomposedForms_hashIdentically() {
        final String precomposed = "é"; // é
        final String decomposed = "é"; // e + combining acute accent

        assertThat(HashUtil.sha256OfNfcText(precomposed)).isEqualTo(HashUtil.sha256OfNfcText(decomposed));
    }

    @Test
    void sha256OfNfcText_differentText_isADifferentDigest() {
        assertThat(HashUtil.sha256OfNfcText("Hello.")).isNotEqualTo(HashUtil.sha256OfNfcText("Hello!"));
    }

    @SuppressWarnings("NullAway")
    @Test
    void sha256Hex_nullBytes_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> HashUtil.sha256Hex(null));
    }

    @SuppressWarnings("NullAway")
    @Test
    void sha256OfNfcText_nullText_isRejected() {
        assertThatNullPointerException().isThrownBy(() -> HashUtil.sha256OfNfcText(null));
    }
}
