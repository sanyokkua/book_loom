package ua.bookloom.pipeline.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The earlier segments that share the most of some terms with a point in the book, for relevant earlier pairs
 * (15i.T8). A segment scores one per distinct term it holds, whatever the letter case and however often the term is
 * repeated; segments before the given ordinal only; best score first, a tie going to the nearer segment.
 */
class BookIndexSharingTest {

    private static final List<String> TERMS = List.of("Ann", "Bea", "Cy");

    private static BookIndex book() {
        return BookIndex.of(IndexTestBooks.oneUnit(
                "Ann met Bea.", // 0: two terms
                "Cy was alone.", // 1: one
                "Ann and Bea and Cy sat down.", // 2: three
                "Nothing happened here.", // 3: none
                "Bea met Cy.", // 4: two
                "Ann ran. Ann sat.", // 5: one, repeated
                "Ann met Bea again.", // 6: two
                "Bea laughed.")); // 7: the point asked from
    }

    @Test
    void sharing_manyTerms_ranksBySharedTermsThenNearerFirst() {
        assertThat(book().sharing(TERMS, 7, 10)).containsExactly(2, 6, 4, 0, 5, 1);
    }

    @Test
    void sharing_limit_keepsTheBestOnly() {
        assertThat(book().sharing(TERMS, 7, 3)).containsExactly(2, 6, 4);
    }

    // IF the point asked from is ordinal 5, THEN that segment and everything after it are not candidates.
    @Test
    void sharing_beforeAnOrdinal_excludesThatOrdinalAndLater() {
        assertThat(book().sharing(TERMS, 5, 10)).containsExactly(2, 4, 0, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void sharing_noRoomForAnyLimit_isEmpty(final int limit) {
        assertThat(book().sharing(TERMS, 7, limit)).isEmpty();
    }

    @Test
    void sharing_beforeTheFirstSegment_isEmpty() {
        assertThat(book().sharing(TERMS, 0, 5)).isEmpty();
    }

    @Test
    void sharing_noTermsOrNoSharedTerm_isEmpty() {
        assertThat(book().sharing(List.of(), 7, 5)).isEmpty();
        assertThat(book().sharing(List.of("Zed"), 7, 5)).isEmpty();
    }

    @Test
    void sharing_termsInAnotherLetterCase_stillMatch() {
        assertThat(book().sharing(List.of("ann", "BEA"), 7, 2)).containsExactly(6, 2);
    }

    // IF a term has several words, THEN a segment holds it only when it holds the words together.
    @Test
    void sharing_aMultiWordTerm_matchesTheWordsTogetherOnly() {
        assertThat(book().sharing(List.of("Ann met Bea"), 7, 5)).containsExactly(6, 0);
    }
}
