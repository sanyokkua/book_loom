package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.BriefField;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.ui.BookFixtures;

/** An edit of a likely field confirms it: the field leaves the likely set and nothing else does. */
class BookBriefViewModelLikelyTest extends BookBriefViewModelTestBase {

    private static final Set<BriefField> ALL = Set.of(
            BriefField.GENRE, BriefField.VOICE_ERA, BriefField.AUDIENCE, BriefField.REGISTER, BriefField.NARRATOR);

    @BeforeEach
    void openWithEveryFieldLikely() {
        openBook(BookFixtures.frankensteinImport());
        onFx(() -> {
            current.replaceBrief(current.brief().get().withLikely(ALL));
            return null;
        });
    }

    private Set<BriefField> likely() {
        return current.brief().get().likely();
    }

    @Test
    void setGenre_likelyGenre_leavesTheLikelySet() {
        change(() -> brief.setGenre("Horror"));

        assertThat(likely())
                .containsExactlyInAnyOrder(
                        BriefField.VOICE_ERA, BriefField.AUDIENCE, BriefField.REGISTER, BriefField.NARRATOR);
    }

    @Test
    void setVoiceEra_and_setAudience_andRegister_leaveTheLikelySet() {
        change(() -> brief.setVoiceEra("Epistolary, 18th century"));
        change(() -> brief.setAudience("Adults"));
        change(() -> brief.setRegister(Register.CASUAL));

        assertThat(likely()).containsExactlyInAnyOrder(BriefField.GENRE, BriefField.NARRATOR);
    }

    @Test
    void narratorSetters_leaveTheLikelySetOnlyOfTheNarrator() {
        change(() -> brief.setNarratorGender(Gender.FEMALE));

        assertThat(likely()).doesNotContain(BriefField.NARRATOR).contains(BriefField.GENRE);
    }

    @Test
    void setFirstPersonNarrator_and_setNarratorPerson_leaveTheLikelySet() {
        change(() -> brief.setNarratorPerson(NarratorPerson.THIRD));
        assertThat(likely()).doesNotContain(BriefField.NARRATOR);
    }

    @Test
    void adoptNarrator_settledNarrator_leavesTheLikelySet() {
        change(() -> brief.styleSuggestion().adoptNarrator(new Narrator(NarratorPerson.FIRST, Gender.MALE)));

        assertThat(likely()).doesNotContain(BriefField.NARRATOR).contains(BriefField.GENRE);
    }

    // IF an unrelated edit cleared the marks, THEN a guess about the genre would read as confirmed.
    @Test
    void setBalance_unrelatedField_keepsTheLikelySet() {
        change(() -> brief.setBalance(10));

        assertThat(likely()).isEqualTo(ALL);
    }
}
