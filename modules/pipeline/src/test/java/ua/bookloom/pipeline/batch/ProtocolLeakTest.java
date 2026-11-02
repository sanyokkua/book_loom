package ua.bookloom.pipeline.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolLeakTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Ти мусиш наполягати на своєму.» «terms»: {«old»: «старий», «words»: «слова»}}, {",
                "Це кінець. \"terms\": {\"old\": \"старий\"}}, {",
                "Це кінець. “terms”: {“old”: “старий”}",
                "Це кінець.\"}, {\"id\": \"7\", \"target\": \"Інше речення.",
                "Це кінець.»}, {«id»: «7»",
                "Це кінець. \"items\": [",
                "Це кінець.\n```",
                "Це кінець. {«old»: «старий»}"
            })
    void leaks_structuralProtocolTail_isTrue(final String target) {
        assertThat(ProtocolLeak.leaks(target)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Він сказав: «Умови (terms) договору прості».",
                "Вона написала слово «terms» на дошці.",
                "— Дивись, — сказав він, — {ось це} мій знак.",
                "Він прошепотів: «{» — і замовк.",
                "Двері зачинилися. «Id» — це що? — спитала вона."
            })
    void leaks_legitimateTextWithTheWordOrBraces_isFalse(final String target) {
        assertThat(ProtocolLeak.leaks(target)).isFalse();
    }

    @Test
    void leaks_idKeyAfterAClosedBrace_isFalse() {
        assertThat(ProtocolLeak.leaks("{x} \"id\": 1")).isFalse();
        assertThat(ProtocolLeak.leaks("{x \"id\": 1")).isTrue();
    }

    @Test
    void leaks_longRunOfOpenBraces_isLinear() {
        final String text = "{".repeat(300_000) + " ".repeat(300_000);

        assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> assertThat(ProtocolLeak.leaks(text)).isFalse());
    }
}
