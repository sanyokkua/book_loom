package ua.bookloom.pipeline;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Invented first-person and third-person paragraphs for the narrator-inference runs (15i.T5): every first-person
 * paragraph comes with its masculine and its feminine Ukrainian rendering, each of a length the checks accept, and
 * none repeats another, so the translation memory never answers for a draft.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NarratorInferenceBooks {

    /** One first-person paragraph and how a man and a woman would tell it. */
    record First(String source, String masculine, String feminine) {}

    static final List<First> FIRST = List.of(
            new First(
                    "I walked home through the cold rain.",
                    "Я пішов додому крізь холодний дощ.",
                    "Я пішла додому крізь холодний дощ."),
            new First(
                    "I told the old clerk the truth.",
                    "Я сказав старому клерку правду.",
                    "Я сказала старому клерку правду."),
            new First("I saw a light in the window.", "Я побачив світло у вікні.", "Я побачила світло у вікні."),
            new First(
                    "I opened the heavy door slowly.",
                    "Я відчинив важкі двері повільно.",
                    "Я відчинила важкі двері повільно."),
            new First(
                    "I took the key from the table.",
                    "Я взяв ключ зі столу біля вікна.",
                    "Я взяла ключ зі столу біля вікна."),
            new First("I paid the driver at once.", "Я заплатив водієві одразу ж.", "Я заплатила водієві одразу ж."),
            new First("I knew the road very well.", "Я знав цю дорогу дуже добре.", "Я знала цю дорогу дуже добре."),
            new First("I wrote the letter that night.", "Я написав листа тієї ночі.", "Я написала листа тієї ночі."),
            new First("I heard steps on the stairs.", "Я почув кроки на сходах.", "Я почула кроки на сходах."),
            new First(
                    "I lifted the bag upstairs.",
                    "Я підняв сумку на другий поверх.",
                    "Я підняла сумку на другий поверх."),
            new First("I waited for an hour.", "Я чекав цілу годину.", "Я чекала цілу годину."),
            new First("I closed the old book.", "Я закрив стару книжку.", "Я закрила стару книжку."),
            new First("I asked the way to the station.", "Я спитав дорогу до станції.", "Я спитала дорогу до станції."),
            new First("I counted the coins twice.", "Я порахував монети двічі.", "Я порахувала монети двічі."),
            new First("I felt tired and cold.", "Я відчув утому та холод.", "Я відчула утому та холод."),
            new First("I left the lamp burning.", "Я залишив лампу запаленою.", "Я залишила лампу запаленою."),
            new First("I turned the page.", "Я перегорнув сторінку.", "Я перегорнула сторінку."),
            new First("I read until midnight.", "Я читав до самої півночі.", "Я читала до самої півночі."),
            new First("I slept badly that night.", "Я спав погано тієї ночі.", "Я спала погано тієї ночі."),
            new First("I found the letter again.", "Я знайшов листа знову.", "Я знайшла листа знову."),
            new First("I shut the window.", "Я зачинив вікно.", "Я зачинила вікно."),
            new First("I stood in the hall.", "Я стояв у холодному залі.", "Я стояла у холодному залі."),
            new First("I gave him my last coin.", "Я віддав йому останню монету.", "Я віддала йому останню монету."),
            new First("I watched the road.", "Я стежив за дорогою.", "Я стежила за дорогою."),
            new First("I answered at last.", "Я відповів нарешті.", "Я відповіла нарешті."));

    /** Third-person paragraphs: {@code {source, target}}, holding no «я» at all. */
    static final List<List<String>> THIRD = List.of(
            List.of("The clerk looked up from the ledger.", "Клерк підвів очі від великої книги."),
            List.of("A cart rolled past the gate.", "Повз браму прокотився візок."),
            List.of("The lamp flickered twice.", "Лампа двічі блимнула."),
            List.of("Rain ran down the glass.", "Дощ стікав по склу вікна."),
            List.of("The stairs creaked under him.", "Сходи гучно скрипіли під ним."),
            List.of("He spoke in a low voice.", "Він говорив тихим голосом."),
            List.of("The room smelled of smoke.", "У кімнаті пахло димом."),
            List.of("A dog barked far away.", "Десь далеко гавкав собака."),
            List.of("She set the cup down gently.", "Вона обережно поставила чашку."),
            List.of("The clock struck nine.", "Годинник пробив дев'яту."),
            List.of("Snow lay on the roofs.", "На дахах лежав сніг."),
            List.of("The road bent toward the hills.", "Дорога звернула до пагорбів."),
            List.of("The porter carried the trunks.", "Швейцар переносив великі валізи."),
            List.of("Smoke rose from the chimney.", "З комина повільно здіймався дим."),
            List.of("Nobody answered the bell.", "На дзвінок ніхто не відповів."));

    /** The book as Markdown text: the sources of the given paragraphs, in order. */
    static String book(final List<String> sources) {
        return String.join("\n\n", sources);
    }

    /** The first {@code count} first-person sources. */
    static List<String> firstSources(final int count) {
        return FIRST.stream().limit(count).map(First::source).toList();
    }

    /**
     * The first {@code count} first-person renderings, feminine at the given indexes and masculine elsewhere.
     */
    static List<String> firstTargets(final int count, final Set<Integer> feminine) {
        return IntStream.range(0, count)
                .mapToObj(index -> feminine.contains(index)
                        ? FIRST.get(index).feminine()
                        : FIRST.get(index).masculine())
                .toList();
    }

    /** The masculine rendering of one first-person paragraph, which a revision of its feminine slip answers with. */
    static String masculine(final int index) {
        return FIRST.get(index).masculine();
    }

    /** The feminine rendering of one first-person paragraph. */
    static String feminine(final int index) {
        return FIRST.get(index).feminine();
    }
}
