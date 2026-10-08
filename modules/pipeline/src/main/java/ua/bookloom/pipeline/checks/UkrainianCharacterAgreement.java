package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;

/**
 * A woman's name in a Ukrainian text, held to two rules. A name that ends in a consonant does not decline when it is
 * a woman's (Хром, not Хрома, Хромові, Хромового), and the past-tense verb right after the name, in the same clause,
 * ends as a woman's verb does (-ла), not as a man's (-в) — nor is the pronoun «він» put there. Only the narrator's
 * words are read, quoted speech and dash dialogue blanked, since another voice may be talking about her wrongly. Both
 * are suffix rules and can misread a word that ends like a verb, so the findings are soft.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UkrainianCharacterAgreement {

    private static final String LANGUAGE = "uk";
    private static final String VOWELS = "аяіїюуеєио";
    private static final String VOWEL_CASES = "(?:[" + VOWELS + "]|ою|ею|єю)";
    private static final int MAX_CASE_ENDING = 5;
    private static final String NOT_AFTER_LETTER = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_BEFORE_LETTER = "(?![\\p{L}\\p{N}])";
    private static final Set<String> MAN_ENDINGS =
            Set.of("а", "у", "ові", "еві", "єві", "ом", "ем", "єм", "ого", "ому", "ового", "овому", "овим", "ів");

    static List<CheckFinding> find(final String target, final List<CharacterName> characters) {
        final String narration = QuotedSpans.narration(target, LANGUAGE);
        final List<CheckFinding> found = new ArrayList<>();
        for (final CharacterName character : characters) {
            if (character.gender() == Gender.FEMALE && !character.rendering().isBlank()) {
                found.addAll(findFor(character.rendering().strip(), narration, target));
            }
        }
        found.sort(Comparator.comparingInt(finding -> finding.span().start()));
        log.trace("Ukrainian character agreement: {} character(s), {} finding(s)", characters.size(), found.size());
        return List.copyOf(found);
    }

    private static List<CheckFinding> findFor(final String name, final String narration, final String target) {
        final boolean vowelEnding = VOWELS.indexOf(Character.toLowerCase(name.charAt(name.length() - 1))) >= 0;
        final Pattern pattern = vowelEnding ? vowelName(name) : consonantName(name);
        final List<CheckFinding> found = new ArrayList<>();
        final Matcher mention = pattern.matcher(narration);
        while (mention.find()) {
            final String ending = vowelEnding ? "" : mention.group(1).toLowerCase(Locale.ROOT);
            if (ending.isEmpty() || vowelEnding) {
                verbAfter(name, narration, mention.end(), target).ifPresent(found::add);
            } else if (MAN_ENDINGS.contains(ending)) {
                found.add(declined(name, mention, target));
            }
        }
        return found;
    }

    // Лізі, Лізу, Лізою: the stem and a case ending, nothing longer, so Лізард is another word.
    private static Pattern vowelName(final String name) {
        final String stem = name.substring(0, name.length() - 1);
        return Pattern.compile(NOT_AFTER_LETTER + Pattern.quote(stem) + VOWEL_CASES + NOT_BEFORE_LETTER);
    }

    private static Pattern consonantName(final String name) {
        return Pattern.compile(
                NOT_AFTER_LETTER + Pattern.quote(name) + "(\\p{L}{0," + MAX_CASE_ENDING + "})" + NOT_BEFORE_LETTER);
    }

    private static Optional<CheckFinding> verbAfter(
            final String name, final String narration, final int from, final String target) {
        return UkrainianGenderCheck.nextWord(narration, from).flatMap(word -> {
            final boolean isHe = word.lower().equals("він");
            final boolean masculine =
                    isHe || UkrainianGenderCheck.endingOf(word.lower()) == UkrainianGenderCheck.Ending.MASCULINE;
            if (!masculine) {
                return Optional.empty();
            }
            final String shown = target.substring(word.start(), word.end());
            log.debug("Character {} is a woman but the word at {} is masculine", name, word.start());
            log.trace("Masculine word {} after the woman's name {}", shown, name);
            return Optional.of(new CheckFinding(
                    FindingKind.NAME_GENDER,
                    new TextSpan(word.start(), word.end(), shown),
                    "«" + name + "» is a woman, but «" + shown
                            + "» after her name is masculine. Use the feminine form (-ла, or вона) of that word and "
                            + "change nothing else.",
                    false));
        });
    }

    private static CheckFinding declined(final String name, final Matcher mention, final String target) {
        final String shown = target.substring(mention.start(), mention.end());
        log.debug("Character {} is a woman but the name at {} is declined like a man's", name, mention.start());
        return new CheckFinding(
                FindingKind.NAME_GENDER,
                new TextSpan(mention.start(), mention.end(), shown),
                "«" + name + "» is a woman's name ending in a consonant, so it does not decline: «" + shown
                        + "» is a man's form. Write «" + name + "» in every case and change nothing else.",
                false);
    }
}
