package ua.bookloom.pipeline;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Rows long enough that the token budget, not a segment count, closes a chunk: a short book with a chunk boundary
 * inside it. A heavy row weighs about 240 estimated tokens (eight fit the 2000-token chunk, the ninth opens the next);
 * a weighty row about 310 (six of them and two short rows fit, the ninth row opens the next).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class HeavyRows {

    private static final String FILLER_EN = "The tide was rising slowly along the grey stones of the old harbour wall. "
            + "Nobody on the quay spoke while the lamps were lit one after another. "
            + "A thin mist lay over the water and hid the far end of the pier. "
            + "The bells of the town rang twice and then fell silent again. "
            + "Somewhere behind the warehouses a dog barked and was answered by another. "
            + "The nets hung drying in the wind beside the low wooden sheds. "
            + "Rain began to fall softly on the tarred roofs and the empty carts. "
            + "The last boat of the evening turned toward the narrow mouth of the bay. "
            + "Smoke rose straight from the chimneys into the cold still air. "
            + "Far out at sea a single light flickered and went out. "
            + "The road to the village was long and nobody walked it that night. "
            + "Everything seemed to wait for the morning and the first grey light.";
    private static final String EXTRA_EN = "A heron stood motionless in the shallows near the slipway. "
            + "The ferryman coiled his rope and looked up at the darkening sky. "
            + "Nothing else moved between the sea wall and the hills. "
            + "The harbour master closed his ledger and blew out the lamp.";
    private static final String EXTRA_UK = "Чапля нерухомо стояла на мілині біля спуску. "
            + "Поромник змотав линву й глянув на темніюче небо. "
            + "Більше ніщо не рухалося між морським муром і пагорбами. "
            + "Начальник порту закрив журнал і задув лампу.";
    private static final String FILLER_UK = "Приплив повільно піднімався вздовж сірого каміння старого портового муру. "
            + "Ніхто на набережній не казав ані слова, поки одна за одною запалювали ліхтарі. "
            + "Тонкий туман лежав над водою й ховав далекий кінець пристані. "
            + "Міські дзвони пролунали двічі й знову затихли надовго. "
            + "Десь за складами загавкав собака, і йому відповів інший. "
            + "Сіті висіли сохнути на вітрі біля низьких дощаних повітей. "
            + "На смолені дахи й порожні вози тихо почав падати дощ. "
            + "Останній вечірній човен повернув до вузького гирла затоки. "
            + "Дим здіймався просто з димарів у холодне нерухоме повітря. "
            + "Далеко в морі блимнуло єдине світло й погасло. "
            + "Дорога до села була довга, і тієї ночі ніхто нею не йшов. "
            + "Усе ніби чекало ранку й першого сірого світла.";

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
