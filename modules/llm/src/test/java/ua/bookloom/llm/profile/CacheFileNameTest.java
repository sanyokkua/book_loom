package ua.bookloom.llm.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** A download cache's file name splits into owner and repository ending in -GGUF, and the rest; long names are prompt. */
class CacheFileNameTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "unsloth_gemma-3-12b-it-GGUF_gemma-3-12b-it-Q4_K_M~unsloth/gemma-3-12b-it-GGUF~gemma-3-12b-it-Q4_K_M",
                "org.x_my_model-gguf_a_GGUF_b~org.x/my_model-gguf~a_GGUF_b",
                "o_r-GGUF_f~o/r-GGUF~f"
            })
    void parse_cacheName_givesRepoAndFile(final String name, final String repo, final String file) {
        assertThat(CacheFileName.parse(name)).hasValue(new CacheFileName(repo, file));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '~',
            value = {
                "plain-model-Q4_K_M",
                "_r-GGUF_f",
                "o_r-GGUF_",
                "o_r-GGUF",
                "o_r-GGUFf",
                "o_r!-GGUF_f",
                "'o r_x-GGUF_f'"
            })
    void parse_otherShapes_giveNothing(final String name) {
        assertThat(CacheFileName.parse(name)).isEmpty();
    }

    @Test
    void parse_lineBreakInTheFile_givesNothing() {
        assertThat(CacheFileName.parse("o_r-GGUF_f\ng")).isEmpty();
    }

    @Test
    void parse_manyRepoEndsAndALineBreakAtTheEnd_isPrompt() {
        final String name = "o_" + "-GGUF_".repeat(100_000) + "\n";

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(CacheFileName.parse(name)).isEmpty());
    }
}
