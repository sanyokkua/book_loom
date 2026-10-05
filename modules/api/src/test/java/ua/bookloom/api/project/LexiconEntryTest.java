package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The conflict policy of {@link LexiconEntry#established()} and the counting of {@link LexiconEntry#seen}. */
class LexiconEntryTest {

    private static LexiconEntry entry(
            final String renderings, @Nullable final String chosen, @Nullable final String suggested) {
        final List<LexiconEntry.Rendering> held = renderings.isEmpty()
                ? List.of()
                : Arrays.stream(renderings.split(","))
                        .map(pair -> pair.split(":"))
                        .map(parts -> new LexiconEntry.Rendering(parts[0], Integer.parseInt(parts[1])))
                        .toList();
        return new LexiconEntry("p1", "master", held, chosen, suggested, null);
    }

    // The conflict policy table: the person's choice, then the majority, the first seen on a tie, then the suggestion.
    @ParameterizedTest(name = "[{index}] renderings={0} chosen={1} suggested={2} -> {3}")
    @CsvSource(
            value = {
                "'господар:3,учитель:1',NULL,NULL,господар",
                "'учитель:1,господар:3',NULL,NULL,господар",
                "'господар:2,учитель:2',NULL,NULL,господар",
                "'учитель:2,господар:2',NULL,NULL,учитель",
                "'господар:1',NULL,'пан',господар",
                "'',NULL,'пан',пан",
                "'господар:5',учитель,'пан',учитель",
                "'',учитель,NULL,учитель",
                "'',NULL,NULL,NULL"
            },
            nullValues = "NULL")
    void established_conflictPolicyTable_picksTheDocumentedWinner(
            final String renderings,
            @Nullable final String chosen,
            @Nullable final String suggested,
            @Nullable final String expected) {
        assertThat(entry(renderings, chosen, suggested).established().orElse(null))
                .isEqualTo(expected);
    }

    @Test
    void seen_sameRenderingInAnotherCase_countsWithTheFirstSpelling() {
        final LexiconEntry seen = entry("господар:1", null, null).seen("Господар");

        assertThat(seen.renderings()).containsExactly(new LexiconEntry.Rendering("господар", 2));
    }

    @Test
    void seen_newRendering_isAddedAfterTheOnesAlreadySeen() {
        final LexiconEntry seen = entry("господар:2", null, null).seen("учитель");

        assertThat(seen.renderings().stream().map(LexiconEntry.Rendering::text)).containsExactly("господар", "учитель");
        assertThat(seen.distinctRenderings()).isEqualTo(2);
    }

    @Test
    void withChosen_blankText_clearsTheChoice() {
        assertThat(entry("господар:1", "учитель", null).withChosen("  ").chosen())
                .isNull();
    }

    @Test
    void rendering_countBelowOne_isRefused() {
        assertThatThrownBy(() -> new LexiconEntry.Rendering("господар", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void established_learnedRendering_outranksReportedAndSuggestedButNotTheChoice() {
        final LexiconEntry learned =
                entry("учитель:4", null, "пан").withLearned(new LexiconEntry.Learned("господар", 5, 6));

        assertThat(learned.established()).contains("господар");
        assertThat(learned.withChosen("володар").established()).contains("володар");
        assertThat(learned.withLearned(null).established()).contains("учитель");
    }

    @Test
    void seen_entryWithLearnedRendering_keepsIt() {
        final LexiconEntry learned = entry("", null, null).withLearned(new LexiconEntry.Learned("господар", 3, 3));

        assertThat(learned.seen("учитель").learned()).isEqualTo(new LexiconEntry.Learned("господар", 3, 3));
    }

    @Test
    void learned_supportAboveOccurrences_isRefused() {
        assertThatThrownBy(() -> new LexiconEntry.Learned("господар", 4, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
