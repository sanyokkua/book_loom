package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A Russian book's capitalised sentence starters are not names: the scan reads the Russian function-word file. */
class NamesScanRussianTest {

    private static final int THRESHOLD = 3;
    private static final List<String> PARAGRAPHS = List.of(
            "Она ушла рано. Но Мирослава вернулась поздно.",
            "Тогда он сказал, что Мирослава права. Она кивнула.",
            "Но никто не ответил. Тогда пришла Мирослава.",
            "Она посмотрела в окно. Тогда стало тихо.",
            "Но дождь не кончился.");

    @Test
    void scan_inventedRussianParagraphs_proposesNoSentenceStarter() {
        final BookSlice book = GlossaryTestSegments.of(PARAGRAPHS);
        final List<String> terms = FrequencyScan.candidates(book.index(), book.ordinals(), THRESHOLD, "ru").stream()
                .map(NameCandidate::term)
                .toList();
        assertThat(terms).contains("Мирослава").doesNotContain("Она", "Но", "Тогда");
    }
}
