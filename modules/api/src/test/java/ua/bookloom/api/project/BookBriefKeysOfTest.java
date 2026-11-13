package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BookBriefKeysOfTest {

    @Test
    void withKeysOf_otherBrief_takesItsKeysAndLikelyAndKeepsTheRest() {
        final BookBrief other = BookBrief.defaults("en")
                .withApostrophe("’")
                .withConventions(Map.of("dash", "long"))
                .withLikely(Set.of(BriefField.GENRE));
        final BookBrief base = BookBrief.defaults("uk").withGenre("Fable");

        final BookBrief merged = base.withKeysOf(other);

        assertThat(merged.apostrophe()).isEqualTo("’");
        assertThat(merged.conventions()).containsEntry("dash", "long");
        assertThat(merged.likely()).containsExactly(BriefField.GENRE);
        assertThat(merged.sourceLanguage()).isEqualTo("uk");
        assertThat(merged.genre()).isEqualTo("Fable");
    }
}
