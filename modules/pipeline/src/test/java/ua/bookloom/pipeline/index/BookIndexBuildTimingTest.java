package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;

/**
 * Measures the index of a generated book of about 80,000 words: how long the build takes (target under 300 ms) and how
 * big the result is. The time is logged, never asserted, so a slow machine cannot fail {@code check}.
 */
// `slow`: builds a book of 80,000 words.
@Slf4j
@Tag("slow")
class BookIndexBuildTimingTest {

    private static final int CHAPTERS = 20;
    private static final int PARAGRAPHS_PER_CHAPTER = 200;
    private static final int WORDS_PER_PARAGRAPH = 20;
    private static final long SEED = 15L;
    private static final String[] SYLLABLES = {"ba", "ro", "mi", "len", "tor", "sa", "vi", "dun", "ek", "o"};
    private static final String[] NAMES = {"Ivo", "Ann", "Bea", "Cy", "Garrick Ashdown", "Ansel Brack"};

    @Test
    void of_aBookOfEightyThousandWords_isBuiltAndItsTimeAndSizeAreLogged() {
        final Document book = generatedBook();

        final long started = System.nanoTime();
        final BookIndex index = BookIndex.of(book);
        final long millis = (System.nanoTime() - started) / 1_000_000L;

        log.info(
                "book index of a generated book: segments={} words~={} build={} ms (target under 300 ms, not asserted)",
                index.size(),
                CHAPTERS * PARAGRAPHS_PER_CHAPTER * WORDS_PER_PARAGRAPH,
                millis);
        assertThat(index.size()).isEqualTo(CHAPTERS * PARAGRAPHS_PER_CHAPTER);
        assertThat(index.stats("ivo").total()).isPositive();
        assertThat(index.mentions(List.of("Garrick Ashdown"), Case.SENSITIVE)).isNotEmpty();
    }

    private static Document generatedBook() {
        final Random random = new Random(SEED);
        final List<Unit> units = new ArrayList<>();
        for (int chapter = 0; chapter < CHAPTERS; chapter++) {
            final String[] paragraphs = new String[PARAGRAPHS_PER_CHAPTER];
            for (int paragraph = 0; paragraph < PARAGRAPHS_PER_CHAPTER; paragraph++) {
                paragraphs[paragraph] = sentence(random);
            }
            units.add(IndexTestBooks.unit("ch" + chapter, UnitRole.BODY, paragraphs));
        }
        return IndexTestBooks.document(units.toArray(Unit[]::new));
    }

    private static String sentence(final Random random) {
        final StringBuilder text = new StringBuilder();
        for (int word = 0; word < WORDS_PER_PARAGRAPH; word++) {
            text.append(word == 0 ? "" : " ");
            text.append(word % 7 == 3 ? NAMES[random.nextInt(NAMES.length)] : syllables(random));
            text.append(word == WORDS_PER_PARAGRAPH / 2 - 1 ? "." : "");
        }
        return text.append('.').toString();
    }

    private static String syllables(final Random random) {
        return SYLLABLES[random.nextInt(SYLLABLES.length)]
                + SYLLABLES[random.nextInt(SYLLABLES.length)]
                + SYLLABLES[random.nextInt(SYLLABLES.length)];
    }
}
