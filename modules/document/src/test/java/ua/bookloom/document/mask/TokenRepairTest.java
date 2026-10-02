package ua.bookloom.document.mask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.PlaceholderRepair;

/**
 * The deterministic placeholder repair over the shapes a small model produces: one token of a pair dropped (the two
 * Bartimaeus paragraphs that reached export broken), a token repeated or invented, and a placement so wrong that only
 * re-placing every token passes. A pair is written {@code open-close} by index, pairs separated by {@code ;}.
 */
class TokenRepairTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            drop cap, closing token dropped | ⟦g0⟧“A⟦g1⟧bove all,” said his master. | ⟦g0⟧«Понад усе», — сказав господар. \
            | 0-1 | RESTORE_MISSING | ⟦g0⟧«П⟦g1⟧онад усе», — сказав господар.
            drop cap, opening token dropped | ⟦g0⟧“A⟦g1⟧bove all,” said his master. | «П⟦g1⟧онад усе», — сказав господар. \
            | 0-1 | RESTORE_MISSING | ⟦g0⟧«П⟦g1⟧онад усе», — сказав господар.
            drop cap, both tokens folded out | ⟦g0⟧“A⟦g1⟧bove all,” said his master. | «Понад усе», — сказав господар. \
            | 0-1 | RESTORE_MISSING | ⟦g0⟧«П⟦g1⟧онад усе», — сказав господар.
            italic before a quote, closing dropped | “Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice. \
            | «Пам'ятай ⟦g0⟧це», — сказав він тихим голосом. | 0-1 | RESTORE_MISSING \
            | «Пам'ятай ⟦g0⟧це»,⟦g1⟧ — сказав він тихим голосом.
            italic before a quote, opening dropped | “Remember ⟦g0⟧this,”⟦g1⟧ he said in a soft voice. \
            | «Пам'ятай це»,⟦g1⟧ — сказав він тихим голосом. | 0-1 | RESTORE_MISSING \
            | «Пам'ятай ⟦g0⟧це»,⟦g1⟧ — сказав він тихим голосом.
            repeated closing token | ⟦g0⟧old⟦g1⟧ door | ⟦g0⟧старі⟦g1⟧ двері ⟦g1⟧ | 0-1 | RESTORE_MISSING \
            | ⟦g0⟧старі⟦g1⟧ двері
            invented token glued to a word | ⟦g0⟧old⟦g1⟧ door | ⟦g0⟧старі⟦g1⟧ ⟦g7⟧двері | 0-1 | RESTORE_MISSING \
            | ⟦g0⟧старі⟦g1⟧ двері
            invented token glued with a stray bracket | Reyes said so. | ⟦g0⟧Рейєс⟧ так сказала. | "" | REWRAP_ALL \
            | Рейєс так сказала.
            swapped pair re-placed | See ⟦g0⟧this⟦g1⟧ now. | Дивись ⟦g1⟧це⟦g0⟧ зараз. | 0-1 | REWRAP_ALL \
            | Дивись ⟦g0⟧це⟦g1⟧ зараз.
            """)
    void repair_brokenTarget_returnsTheRepairedTarget(
            String name, String masked, String target, String pairs, PlaceholderRepair mode, String expected) {
        assertThat(TokenRepair.repair(masked, target, pairsOf(pairs), List.of(), mode))
                .contains(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            nothing to wrap | ⟦g0⟧old⟦g1⟧ door | "" | 0-1 | REWRAP_ALL
            swapped pair kept as placed | See ⟦g0⟧this⟦g1⟧ now. | Дивись ⟦g1⟧це⟦g0⟧ зараз. | 0-1 | RESTORE_MISSING
            """)
    void repair_noPlacementPassesTheGate_returnsEmpty(
            String name, String masked, String target, String pairs, PlaceholderRepair mode) {
        assertThat(TokenRepair.repair(masked, target, pairsOf(pairs), List.of(), mode))
                .isEmpty();
    }

    // A token the text never had, standing where a word stood, replaced that word — a name the model hid behind a
    // token it invented. Dropping it would delete the word, so no deterministic repair is offered.
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
            name between two spaces | and Vance never let | і ⟦g1⟧ ніколи не дозволяв | RESTORE_MISSING
            name before a full stop | with the help of Nell. | за допомогою ⟦g3⟧. | RESTORE_MISSING
            name at the start | Vance smiled. | ⟦g0⟧ усміхнулася. | RESTORE_MISSING
            name re-placed by position | and Vance never let | і ⟦g1⟧ ніколи не дозволяв | REWRAP_ALL
            """)
    void repair_inventedTokenStandsForAWord_returnsEmpty(
            String name, String masked, String target, PlaceholderRepair mode) {
        assertThat(TokenRepair.repair(masked, target, List.of(), List.of(), mode))
                .isEmpty();
    }

    private static List<PlaceholderPair> pairsOf(String spec) {
        if (spec.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(spec.split(";"))
                .map(pair -> pair.split("-"))
                .map(ends -> new PlaceholderPair(
                        Placeholders.token(Integer.parseInt(ends[0])),
                        Placeholders.token(Integer.parseInt(ends[1])),
                        null))
                .toList();
    }
}
