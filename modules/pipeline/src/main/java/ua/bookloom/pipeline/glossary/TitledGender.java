package ua.bookloom.pipeline.glossary;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;

/**
 * The gender a form of address gives a name ({@code Mrs. Whitmore}), from the language files' {@code femaleTitles} and
 * {@code maleTitles}. One use is enough for a suggestion, where pronouns need three, because the form is the writer's
 * own statement of the gender; it stays a suggestion ("likely") and never decides over a pronoun.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TitledGender {

    /**
     * The gender the evidence decides, else the one the forms of address before the name show.
     *
     * @param term the glossary term
     * @param texts the visible texts of the book
     * @param evidence the pronouns and verbs read after the name
     * @return the dominant gender, or the titled one, or empty
     */
    static Optional<Gender> dominant(
            final String term, final List<String> texts, final PronounGender.Evidence evidence) {
        return evidence.dominant().or(() -> of(term, texts, evidence));
    }

    /**
     * The gender of the forms of address written before a name.
     *
     * @param term the glossary term
     * @param texts the visible texts of the book
     * @param pronouns what the pronouns already say; a name whose pronouns name the other gender is left to them
     * @return the one gender every titled use shows, or empty when there is none, a family's two, or the pronouns
     *     contradict it
     */
    static Optional<Gender> of(final String term, final List<String> texts, final PronounGender.Evidence pronouns) {
        final Set<Gender> shown = EnumSet.noneOf(Gender.class);
        for (final String text : texts) {
            for (final Mention use : PronounReader.titledUses(term, text)) {
                final Pronoun title = use.title();
                if (title != null) {
                    shown.add(title.gender());
                }
            }
        }
        log.debug("Forms of address before {} show {}", term, shown);
        return shown.stream()
                .filter(gender -> shown.size() == 1)
                .filter(gender -> (gender == Gender.FEMALE ? pronouns.male() : pronouns.female()) == 0)
                .findFirst();
    }
}
