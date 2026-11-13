package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.pipeline.glossary.PronounReader.Mention;
import ua.bookloom.pipeline.glossary.PronounReader.Pronoun;

/**
 * The running lists of one name's pronouns and verbs while {@link PronounGender} reads its mentions.
 *
 * @param female the feminine subject pronouns
 * @param male the masculine subject pronouns
 * @param femaleObject the feminine object or possessive forms
 * @param maleObject the masculine object or possessive forms
 * @param femaleVerbs the feminine past-tense verbs after the name
 * @param maleVerbs the masculine past-tense verbs after the name
 */
record PronounTally(
        List<String> female,
        List<String> male,
        List<String> femaleObject,
        List<String> maleObject,
        List<String> femaleVerbs,
        List<String> maleVerbs) {

    PronounTally() {
        this(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>());
    }

    void add(final Mention mention, final PastTenseReader.@Nullable Vote verb) {
        if (verb != null) {
            (verb.gender() == Gender.FEMALE ? femaleVerbs : maleVerbs).add(verb.word());
        }
        final Pronoun title = mention.title();
        if (title != null) {
            (title.gender() == Gender.FEMALE ? female : male).add(title.word());
            return;
        }
        final Pronoun subject = mention.subject();
        final Pronoun object = mention.object();
        if (subject != null) {
            (subject.gender() == Gender.FEMALE ? female : male).add(subject.word());
        }
        if (object != null) {
            (object.gender() == Gender.FEMALE ? femaleObject : maleObject).add(object.word());
        }
    }

    PronounGender.Evidence evidence() {
        return new PronounGender.Evidence(female, male, femaleObject, maleObject, femaleVerbs, maleVerbs);
    }
}
