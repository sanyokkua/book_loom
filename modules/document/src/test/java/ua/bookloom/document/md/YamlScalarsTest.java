package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What a YAML reader makes of an unquoted scalar, form by form, and that a long value is read promptly. */
class YamlScalarsTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "~",
                "Null",
                "YES",
                "off",
                "12",
                "-3.5",
                "1_000",
                "1e5",
                "+.5e-3",
                "0x1F",
                "0o17",
                "0b101",
                "-.inf",
                ".NaN",
                "2024-03-05",
                "2024-3-5",
                "2024-03-05T10:30",
                "2024-03-05 10:30:15.25",
                "2024-03-05T10:30:15Z",
                "2024-03-05 10:30 +02:00",
                "2024-03-05t10:30:15 -0500"
            })
    void isTyped_dataForms_isTrue(final String plain) {
        assertThat(YamlScalars.isTyped(plain)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Dawn",
                "12ab",
                "0x",
                "2024-03",
                "2024-03-05x",
                "2024-03-123",
                "2024-03-05T10",
                "2024-03-05 10:30:5",
                "2024-03-05T10:30 Zulu",
                "1.2.3",
                ".inf5"
            })
    void isTyped_proseOrNearMisses_isFalse(final String plain) {
        assertThat(YamlScalars.isTyped(plain)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.org/a b", "WWW.example.org", "mailto:x@y.z", "reader@example.org"})
    void isAddress_addresses_isTrue(final String text) {
        assertThat(YamlScalars.isAddress(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.org", "see https://example.org", "a@b", "two words@x.y"})
    void isAddress_otherText_isFalse(final String text) {
        assertThat(YamlScalars.isAddress(text)).isFalse();
    }

    @Test
    void isTyped_veryLongValues_finishPromptly() {
        final String digits = "1" + "_".repeat(200_000) + "x";
        final String date = "2024-03-05" + " ".repeat(200_000) + "x";

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertThat(YamlScalars.isTyped(digits)).isFalse();
            assertThat(YamlScalars.isTyped(date)).isFalse();
            assertThat(YamlScalars.isAddress("@".repeat(200_000))).isFalse();
        });
    }
}
