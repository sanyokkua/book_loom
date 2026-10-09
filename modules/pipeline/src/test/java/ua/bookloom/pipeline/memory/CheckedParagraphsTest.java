package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CheckedParagraphsTest {

    private final CheckedParagraphs checked = new CheckedParagraphs();

    @Test
    void has_nothingRemembered_isFalse() {
        assertThat(checked.has("p", "s1", "h1")).isFalse();
    }

    @Test
    void has_sameStateRemembered_isTrue() {
        checked.remember("p", "s1", "h1");

        assertThat(checked.has("p", "s1", "h1")).isTrue();
    }

    @Test
    void has_stateChangedSinceRemembered_isFalse() {
        checked.remember("p", "s1", "h1");

        assertThat(checked.has("p", "s1", "h2")).isFalse();
    }

    @Test
    void has_otherProjectOrSegment_isFalse() {
        checked.remember("p", "s1", "h1");

        assertThat(checked.has("q", "s1", "h1")).isFalse();
        assertThat(checked.has("p", "s2", "h1")).isFalse();
    }
}
