package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Origin precedence (design D3): the person always wins; an automatic value never replaces a higher one. */
class OriginPrecedenceTest {

    // Proves every one of the 4 x 4 (incoming, held) pairs against a hard-coded outcome.
    @ParameterizedTest
    @CsvSource({
        "USER,USER,true",
        "USER,BOOK,true",
        "USER,RULE,true",
        "USER,LIKELY,true",
        "BOOK,USER,false",
        "BOOK,BOOK,true",
        "BOOK,RULE,true",
        "BOOK,LIKELY,true",
        "RULE,USER,false",
        "RULE,BOOK,false",
        "RULE,RULE,true",
        "RULE,LIKELY,true",
        "LIKELY,USER,false",
        "LIKELY,BOOK,false",
        "LIKELY,RULE,false",
        "LIKELY,LIKELY,true"
    })
    void replace_automaticLowerOriginOverHigher_isRefused(
            final Origin incoming, final Origin held, final boolean expected) {
        assertThat(incoming.mayReplace(held)).isEqualTo(expected);
    }

    // Proves that the person's edit replaces a value the book proved, and that an empty slot takes any value.
    @Test
    void edit_personOverBookValue_isAccepted() {
        assertThat(Origin.USER.mayReplace(Origin.BOOK)).isTrue();
        assertThat(Origin.LIKELY.mayReplace(null)).isTrue();
    }

    // Proves that only the person's and the book's values are enforced by the checks.
    @ParameterizedTest
    @CsvSource({"USER,true", "BOOK,true", "RULE,false", "LIKELY,false"})
    void isEnforced_eachOrigin_onlyUserAndBook(final Origin origin, final boolean expected) {
        assertThat(origin.isEnforced()).isEqualTo(expected);
    }
}
