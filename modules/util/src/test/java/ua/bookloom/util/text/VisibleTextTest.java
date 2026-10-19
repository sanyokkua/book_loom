package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What a reader would see: only separators, controls and format characters count as nothing. */
class VisibleTextTest {

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\r\n", " ", "​", "﻿", "­", "⁠", "　", "  ", "  ​﻿­ "})
    void isBlank_onlyInvisibleCharacters_isTrue(final String text) {
        assertThat(VisibleText.isBlank(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " 2", "*", "​Ж", "﻿Hello", "­x"})
    void isBlank_anyVisibleCharacter_isFalse(final String text) {
        assertThat(VisibleText.isBlank(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {" X​I﻿V­", " XIV "})
    void visible_mixedText_keepsOnlyTheVisibleCharacters(final String text) {
        assertThat(VisibleText.visible(text)).isEqualTo("XIV");
    }

    @ParameterizedTest
    @ValueSource(strings = {"  ", ""})
    void visible_invisibleOnly_isEmpty(final String text) {
        assertThat(VisibleText.visible(text)).isEmpty();
    }

    @Test
    void isBlank_nullText_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> VisibleText.isBlank(nullText()))
                .withMessage("text");
    }

    @Test
    void visible_nullText_isRejected() {
        assertThatNullPointerException()
                .isThrownBy(() -> VisibleText.visible(nullText()))
                .withMessage("text");
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"})
    private static String nullText() {
        return null;
    }
}
