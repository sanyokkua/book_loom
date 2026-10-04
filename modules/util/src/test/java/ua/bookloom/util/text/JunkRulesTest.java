package ua.bookloom.util.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** What the junk rules drop from a scan, what they only mark, and what they never touch. */
class JunkRulesTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"CROYDON", "NASA", "OK", "T-shirt", "X-ray", "Yellow Pages", "Hyperion Books", "Random House"})
    void isJunk_printingNoteAllCapsOrInitialCompound_isDropped(final String term) {
        assertThat(JunkRules.isJunk(term)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Nathaniel",
                "Simon Lovelace",
                "Al-Arish",
                "Harrow Vale",
                "Meridian Survey Institute",
                "Amulet",
                "I",
                "Bull-head",
                "Nat",
                "Ємельян"
            })
    void isJunk_realNameOrTerm_isKept(final String term) {
        assertThat(JunkRules.isJunk(term)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"Bull-head,1", "Room 101,1", "Nat,0", "Al,1", "Nathaniel,0", "CROYDON,2", "T-shirt,2"})
    void likelihood_term_scoresByItsShape(final String term, final int score) {
        assertThat(JunkRules.likelihood(term)).isEqualTo(score);
    }

    @Test
    void likelihood_allCapsOnlyAtTwoLetters_aLoneCapitalIsNotShouting() {
        assertThat(JunkRules.likelihood("A")).isLessThan(JunkRules.DROP_SCORE);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Text copyright © 2003 by Jonathan Stroud",
                "All rights reserved. No part of this book may be reproduced.",
                "Visit www.disneyhyperionbooks.com",
                "ISBN 978-1-4231-0000-0",
                "Printed in CROYDON by CPI Books",
                "By Jonathan Stroud",
                "First published in Great Britain in 2003"
            })
    void isPrintingNote_rightsImprintAddressOrCredit_isANote(final String line) {
        assertThat(JunkRules.isPrintingNote(line)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "He walked by the river and saw nothing at all.",
                "“Nathaniel,” said Underwood, “come here.”",
                "She copied the page by hand."
            })
    void isPrintingNote_storySentence_isNotANote(final String line) {
        assertThat(JunkRules.isPrintingNote(line)).isFalse();
    }
}
