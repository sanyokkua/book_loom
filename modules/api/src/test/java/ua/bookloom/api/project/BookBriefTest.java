package ua.bookloom.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.pipeline.QualityDial;

/**
 * {@code BookBrief}'s defaults and balance validation.
 */
class BookBriefTest {

    @Test
    void defaults_noArguments_matchesTheBriefDefaults() {
        final BookBrief brief = BookBrief.defaults("en");

        assertThat(brief.sourceLanguage()).isEqualTo("en");
        assertThat(brief.targetLanguage()).isNull();
        assertThat(brief.genre()).isNull();
        assertThat(brief.register()).isEqualTo(Register.NEUTRAL);
        assertThat(brief.voiceEra()).isNull();
        assertThat(brief.audience()).isNull();
        assertThat(brief.names()).isEqualTo(NamePolicy.TRANSLITERATE);
        assertThat(brief.foreignPassages()).isEqualTo(ForeignPassagePolicy.KEEP);
        assertThat(brief.footnotes()).isEqualTo(FootnotePolicy.TRANSLATE);
        assertThat(brief.units()).isEqualTo(UnitPolicy.KEEP);
        assertThat(brief.balance()).isEqualTo(55);
        assertThat(brief.dial()).isEqualTo(QualityDial.BALANCED);
        assertThat(brief.alsoTranslate()).isEqualTo(new AlsoTranslate(true, true, true, false));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void constructor_balanceOutsideRange_throws(final int balance) {
        final var arg1 = AlsoTranslate.defaults();
        assertThatThrownBy(() -> new BookBrief(
                        "en",
                        null,
                        null,
                        Register.NEUTRAL,
                        null,
                        null,
                        NamePolicy.TRANSLITERATE,
                        ForeignPassagePolicy.KEEP,
                        FootnotePolicy.TRANSLATE,
                        UnitPolicy.KEEP,
                        balance,
                        arg1,
                        QualityDial.BALANCED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 100})
    void constructor_balanceAtBounds_isAccepted(final int balance) {
        final BookBrief brief = new BookBrief(
                "en",
                null,
                null,
                Register.NEUTRAL,
                null,
                null,
                NamePolicy.TRANSLITERATE,
                ForeignPassagePolicy.KEEP,
                FootnotePolicy.TRANSLATE,
                UnitPolicy.KEEP,
                balance,
                AlsoTranslate.defaults(),
                QualityDial.BALANCED);

        assertThat(brief.balance()).isEqualTo(balance);
    }

    @Test
    void defaults_noNarratorChosen_narratorIsUnspecified() {
        final Narrator narrator = BookBrief.defaults("en").narrator();

        assertThat(narrator).isEqualTo(Narrator.unspecified());
        assertThat(narrator.isSpecified()).isFalse();
        assertThat(narrator.hasCheckableGender()).isFalse();
    }

    @Test
    void withNarrator_firstPersonFemale_keepsEveryOtherChoiceAndSurvivesLanguageChange() {
        final BookBrief base = BookBrief.defaults("en").withLanguages("en", "uk");
        final Narrator chosen = new Narrator(NarratorPerson.FIRST, Gender.FEMALE);

        final BookBrief brief = base.withNarrator(chosen).withLanguages("en", "pl");

        assertThat(brief.narrator()).isEqualTo(chosen);
        assertThat(brief.targetLanguage()).isEqualTo("pl");
        assertThat(brief.withNarrator(Narrator.unspecified())).isEqualTo(base.withLanguages("en", "pl"));
    }

    @Test
    void hasCheckableGender_thirdPersonOrNeuter_isFalse() {
        assertThat(new Narrator(NarratorPerson.THIRD, Gender.FEMALE).hasCheckableGender())
                .isFalse();
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.NEUTER).hasCheckableGender())
                .isFalse();
        assertThat(new Narrator(NarratorPerson.FIRST, Gender.MALE).hasCheckableGender())
                .isTrue();
    }

    // One character means one code point: a pair of characters is not an apostrophe.
    @Test
    void withApostrophe_twoCharacters_isRejected() {
        final BookBrief base = BookBrief.defaults("en");

        assertThatThrownBy(() -> base.withApostrophe("''")).isInstanceOf(IllegalArgumentException.class);
    }

    // The policy keys survive a change of language and default to "the target file's default".
    @Test
    void withPolicyKeys_thenLanguages_keepsThemAndDefaultsAreEmpty() {
        final BookBrief base = BookBrief.defaults("en");
        final BookBrief declared = base.withApostrophe("\u2019")
                .withQuoteStyle(new QuoteStyle("\u00ab", "\u00bb", "\u201e", "\u201c"))
                .withDefaultAddress(new Address(AddressForm.INFORMAL, AddressForm.FORMAL))
                .withConventions(Map.of("hardG", "\u0433"))
                .withLikely(Set.of(BriefField.GENRE))
                .withLanguages("en", "uk");

        assertThat(declared.apostrophe()).isEqualTo("\u2019");
        assertThat(declared.quoteStyle()).isEqualTo(new QuoteStyle("\u00ab", "\u00bb", "\u201e", "\u201c"));
        assertThat(declared.defaultAddress()).isEqualTo(new Address(AddressForm.INFORMAL, AddressForm.FORMAL));
        assertThat(declared.conventions()).containsEntry("hardG", "\u0433");
        assertThat(declared.likely()).containsExactly(BriefField.GENRE);
        assertThat(base.apostrophe()).isNull();
        assertThat(base.quoteStyle()).isNull();
        assertThat(base.defaultAddress()).isNull();
        assertThat(base.conventions()).isEmpty();
        assertThat(base.likely()).isEmpty();
    }

    @Test
    void summariesOn_noAnalysis_true() {
        assertThat(BookBrief.defaults("en").summariesOn()).isTrue();
    }

    @Test
    void summariesOn_separateStoriesNotForced_false() {
        final BookBrief brief = BookBrief.defaults("en").withStoryShape(StoryShape.SEPARATE_STORIES);

        assertThat(brief.summariesOn()).isFalse();
    }

    @Test
    void summariesOn_forcedOnWithSeparateStories_true() {
        final BookBrief brief = BookBrief.defaults("en")
                .withStoryShape(StoryShape.SEPARATE_STORIES)
                .withChapterSummaries(true);

        assertThat(brief.summariesOn()).isTrue();
    }

    @Test
    void summariesOn_forcedOffWithOneStory_false() {
        final BookBrief brief =
                BookBrief.defaults("en").withStoryShape(StoryShape.ONE_STORY).withChapterSummaries(false);

        assertThat(brief.summariesOn()).isFalse();
    }

    @Test
    void withKeysOf_storyShapeAndSwitch_kept() {
        final BookBrief other = BookBrief.defaults("en")
                .withStoryShape(StoryShape.SEPARATE_STORIES)
                .withChapterSummaries(true);

        final BookBrief merged = BookBrief.defaults("uk").withGenre("Fable").withKeysOf(other);

        assertThat(merged.storyShape()).isEqualTo(StoryShape.SEPARATE_STORIES);
        assertThat(merged.chapterSummaries()).isTrue();
        assertThat(merged.genre()).isEqualTo("Fable");
    }

    @Test
    void withLanguages_storyShapeAndSwitch_kept() {
        final BookBrief brief = BookBrief.defaults("en")
                .withStoryShape(StoryShape.ONE_STORY)
                .withChapterSummaries(false)
                .withLanguages("en", "uk")
                .withApostrophe("\u2019");

        assertThat(brief.storyShape()).isEqualTo(StoryShape.ONE_STORY);
        assertThat(brief.chapterSummaries()).isFalse();
    }
}
