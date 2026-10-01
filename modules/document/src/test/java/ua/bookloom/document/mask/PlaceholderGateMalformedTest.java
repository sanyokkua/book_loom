package ua.bookloom.document.mask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.PlaceholderRepair;

/**
 * The gate's refusal of a reply whose tokens all match but which still carries a bracket glyph outside a whole token
 * (gemma4:e4b on the fixture book, ch4: a stray {@code ⟧} that reached export as text), and of a pair that moved off
 * the words it wrapped; plus the deterministic repair that strips the stray glyphs and places the pair again.
 */
class PlaceholderGateMalformedTest {

    private static final String INSTITUTE_SOURCE = "The ⟦g0⟧Meridian Survey Institute⟦g1⟧ sent its first gravity team"
            + " to Harrow Vale in the spring, and Dr. Eleanor Vance led it.";

    private static final List<PlaceholderPair> ONE_PAIR =
            List.of(new PlaceholderPair(Placeholders.token(0), Placeholders.token(1), null));

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            stray closing glyph after the pair (fixture ch4) | ⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування⟧ відправив \
            свою першу гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс.
            lone opening glyph | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧ ⟦відправив свою першу гравітаційну \
            команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс.
            token split by whitespace | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1 ⟧ відправив свою першу \
            гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс. ⟦g1⟧
            misspelt token | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧ відправив ⟦G1⟧ свою першу гравітаційну \
            команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс.
            """)
    void compare_bracketGlyphOutsideAToken_failsAsStrayBracket(String name, String target) {
        assertThat(PlaceholderGate.compare(INSTITUTE_SOURCE, target, ONE_PAIR, List.of())
                        .failedRule())
                .isEqualTo(GateRule.STRAY_BRACKET);
    }

    // The stray glyph removed, the pair still wraps one word of the three it wrapped in a long sentence.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            pair shrank to one word | ⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування відправив свою першу \
            гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс. | PAIR_SPAN
            pair kept on the name | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧ відправив свою першу \
            гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс. |
            """)
    void compare_pairSpan_failsOnlyWhenThePairLostMostOfItsWords(String name, String target, GateRule expected) {
        assertThat(PlaceholderGate.compare(INSTITUTE_SOURCE, target, ONE_PAIR, List.of())
                        .failedRule())
                .isEqualTo(expected);
    }

    // A short pair (a drop cap, one emphasised word) may change its share freely: the words around it decide.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            drop cap | ⟦g0⟧G⟦g1⟧ravity pulls every object toward every other object, near or far. \
            | ⟦g0⟧Г⟦g1⟧равітація притягує кожен предмет до кожного іншого предмета, близько чи далеко.
            one word grows | It was ⟦g0⟧not⟦g1⟧ there. | Його ⟦g0⟧зовсім не було⟦g1⟧ там.
            """)
    void compare_shortPair_passes(String name, String masked, String target) {
        assertThat(PlaceholderGate.compare(masked, target, ONE_PAIR, List.of()).matches())
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            stray glyph stripped, words untouched | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧⟧ відправив свою \
            першу гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс. \
            | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧ відправив свою першу гравітаційну команду до Гарроу-Вейл \
            навесні, і її очолила доктор Елеонора Венс.
            split token rejoined | ⟦g0⟧Інститут Мерідіанського Зондування⟦ g1 ⟧ відправив свою першу \
            гравітаційну команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс. \
            | ⟦g0⟧Інститут Мерідіанського Зондування⟦g1⟧ відправив свою першу гравітаційну команду до Гарроу-Вейл \
            навесні, і її очолила доктор Елеонора Венс.
            """)
    void repair_strayGlyph_returnsTheTargetWithoutIt(String name, String target, String expected) {
        assertThat(TokenRepair.repair(INSTITUTE_SOURCE, target, ONE_PAIR, List.of(), PlaceholderRepair.RESTORE_MISSING))
                .contains(expected);
    }

    // WHEN the fixture's reply is re-placed by position, THEN the pair wraps more than its first word again.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            fixture ch4 reply | ⟦g0⟧Інститут ⟦g1⟧Мерідіанського Зондування⟧ відправив свою першу гравітаційну \
            команду до Гарроу-Вейл навесні, і її очолила доктор Елеонора Венс.
            """)
    void repair_rewrapStrayGlyphReply_passesTheGate(String name, String target) {
        assertThat(TokenRepair.repair(INSTITUTE_SOURCE, target, ONE_PAIR, List.of(), PlaceholderRepair.REWRAP_ALL))
                .hasValueSatisfying(
                        repaired -> assertThat(PlaceholderGate.compare(INSTITUTE_SOURCE, repaired, ONE_PAIR, List.of())
                                        .failedRule())
                                .isNull());
    }
}
