package ua.bookloom.pipeline.qa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every check carries one level, the level of its findings at high severity (design D12 "Levels"). */
class CheckNameTest {

    @ParameterizedTest
    @CsvSource({
        "SCRIPT, BLOCK",
        "ECHO, BLOCK",
        "REPETITION, BLOCK",
        "LENGTH, BLOCK",
        "GLOSSARY, BLOCK",
        "REFUSAL, BLOCK",
        "PLACEHOLDER, BLOCK",
        "LOCKED_TERM, BLOCK",
        "KEPT_RUN, BLOCK",
        "SCRIPT_PURITY, BLOCK",
        "QUOTE_BALANCE, BLOCK",
        "LANGUAGE_IDENTITY, BLOCK",
        "SENTENCE_COUNT, BLOCK",
        "VOCATIVE, BLOCK",
        "PROTOCOL_LEAK, BLOCK",
        "NAME_SWAP, BLOCK",
        "IDENTIFIER, BLOCK",
        "LATIN_RUN, BLOCK",
        "ALPHABET, BLOCK",
        "NUMBER, BLOCK",
        "GENDER, REPAIR",
        "NAME_GENDER, REPAIR",
        "NAME_MISSING, REPAIR",
        "NAME_SPELLING, REPAIR",
        "ADDRESS_MIX, REPAIR",
        "FOREIGN_WORD, REPAIR",
        "DUPLICATE_WORD, NOTE",
        "SPACING, NOTE",
        "UNKNOWN_WORD, NOTE",
        "TYPOGRAPHY, NOTE"
    })
    void level_everyCheckName_hasOneLevel(final CheckName check, final CheckLevel expected) {
        assertThat(check.level()).isEqualTo(expected);
    }
}
