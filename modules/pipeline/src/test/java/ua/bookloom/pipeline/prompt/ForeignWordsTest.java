package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ForeignWordsTest {

    @Test
    void of_ukrainian_mapsEachRussianWordToItsReplacement() {
        assertThat(ForeignWords.of("uk")).containsEntry("тоже", "теж").containsEntry("чтобы", "щоб");
    }

    @Test
    void of_languageWithoutAList_isEmpty() {
        assertThat(ForeignWords.of("en")).isEmpty();
    }
}
