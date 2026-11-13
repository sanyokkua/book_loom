package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Rows long enough that the token budget, not a segment count, closes a chunk: a short book with a chunk boundary
 * inside it. A chunk holds the small tier's current part, 600 estimated tokens: a heavy row weighs 68 (eight fit, the
 * ninth opens the next chunk); a weighty row about 84 (six of them and two short rows fit, the ninth row opens the
 * next).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HeavyRows {

    private static final String FILLER_EN = "The tide was rising slowly along the grey stones of the old harbour wall. "
            + "Nobody on the quay spoke while the lamps were lit one after another. "
            + "A thin mist lay over the water and hid the far end of the pier.";
    private static final String EXTRA_EN = "A heron stood motionless in the shallows near the slipway.";
    private static final String EXTRA_UK = "Чапля нерухомо стояла на мілині біля спуску.";
    private static final String FILLER_UK = "Приплив повільно піднімався вздовж сірого каміння старого портового муру. "
            + "Ніхто на набережній не казав ані слова, поки одна за одною запалювали ліхтарі. "
            + "Тонкий туман лежав над водою й ховав далекий кінець пристані.";

    static String heavy(final String source) {
        return source + " " + FILLER_EN;
    }

    static String heavyT(final String target) {
        return target + " " + FILLER_UK;
    }

    static String weighty(final String source) {
        return heavy(source) + " " + EXTRA_EN;
    }

    static String weightyT(final String target) {
        return heavyT(target) + " " + EXTRA_UK;
    }
}
