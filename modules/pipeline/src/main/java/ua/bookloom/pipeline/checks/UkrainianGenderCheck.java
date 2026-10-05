package ua.bookloom.pipeline.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.Gender;

/**
 * The Ukrainian gender check: the past tense of a verb shows its subject's gender, so the first verb after the
 * narrator's «я» must end as the narrator's gender does ({@code -в} masculine, {@code -ла} feminine, {@code -ло}
 * neuter, which never fits «я»). Only the narrator's own words are read — quoted speech and dash dialogue are blanked
 * first, since a character of the other gender says «я зачинила» correctly. The rule is a suffix rule, so it reads
 * a handful of words that end like a past tense and are not one from an exceptions list, and it only looks at the
 * word that follows «я», past a few particles and adverbs, never at a word behind punctuation.
 */
@Slf4j
final class UkrainianGenderCheck implements GenderCheck {

    private static final String LANGUAGE = "uk";
    private static final int MIN_VERB_LENGTH = 3;
    private static final int MAX_SKIPPED = 3;
    private static final Pattern PRONOUN = Pattern.compile("(?<![\\p{L}\\p{M}'’ʼ-])[Яя](?![\\p{L}\\p{M}'’ʼ-])");
    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");
    private static final Pattern BREAK = Pattern.compile("[,.;:!?…()—–―]");
    private static final Set<String> SKIPPED = Set.of(
            "не", "ні", "б", "би", "ж", "же", "й", "і", "вже", "ще", "лише", "тільки", "тоді", "потім", "тихо",
            "швидко", "просто", "одразу", "знову", "теж", "також", "нарешті", "ніколи", "завжди", "майже", "там", "тут",
            "ось", "тепер", "швидше", "якось", "навіть", "зовсім", "аж", "все");
    private static final Set<String> NOT_PAST = Set.of(
            "мало", "чимало", "число", "село", "тіло", "діло", "світло", "скло", "зло", "весло", "крило", "дно", "лев",
            "любов", "кров", "церков", "морков", "львів", "нів", "дарів");

    /** The gender a past-tense ending shows. */
    private enum Ending {
        MASCULINE(Gender.MALE),
        FEMININE(Gender.FEMALE),
        NEUTER(Gender.NEUTER),
        NONE(Gender.UNKNOWN);

        private final Gender gender;

        Ending(final Gender gender) {
            this.gender = gender;
        }
    }

    @Override
    public List<CheckFinding> find(final String target, final Gender narrator) {
        final String narration = QuotedSpans.narration(target, LANGUAGE);
        final List<CheckFinding> findings = new ArrayList<>();
        final Matcher pronoun = PRONOUN.matcher(narration);
        while (pronoun.find()) {
            wordAfter(narration, pronoun.end(), narrator, target).ifPresent(findings::add);
        }
        log.debug("Ukrainian gender check narrator={} findings={}", narrator, findings.size());
        return List.copyOf(findings);
    }

    private static Optional<CheckFinding> wordAfter(
            final String narration, final int from, final Gender narrator, final String target) {
        int position = from;
        for (int skipped = 0; skipped <= MAX_SKIPPED; skipped++) {
            final Matcher word = WORD.matcher(narration);
            if (!word.find(position)
                    || BREAK.matcher(narration.substring(position, word.start()))
                            .find()) {
                return Optional.empty();
            }
            final String lower = word.group().toLowerCase(Locale.ROOT);
            if (!SKIPPED.contains(lower)) {
                return finding(lower, word, narrator, target);
            }
            position = word.end();
        }
        return Optional.empty();
    }

    private static Optional<CheckFinding> finding(
            final String lower, final Matcher word, final Gender narrator, final String target) {
        final Ending ending = endingOf(lower);
        if (ending == Ending.NONE || ending.gender == narrator) {
            return Optional.empty();
        }
        final String shown = target.substring(word.start(), word.end());
        log.debug("Narrator {} but the word at {} is {}", narrator, word.start(), ending);
        log.trace("Wrong-gender word {}", shown);
        final String wanted = narrator == Gender.MALE ? "masculine (-в)" : "feminine (-ла)";
        return Optional.of(new CheckFinding(
                FindingKind.GENDER,
                new TextSpan(word.start(), word.end(), shown),
                "The narrator is " + narrator.name().toLowerCase(Locale.ROOT)
                        + ", but this past-tense word after «я» is "
                        + ending.name().toLowerCase(Locale.ROOT) + ". Use the " + wanted
                        + " form of that word and change nothing else.",
                false));
    }

    private static Ending endingOf(final String word) {
        if (word.length() < MIN_VERB_LENGTH || NOT_PAST.contains(word)) {
            return Ending.NONE;
        }
        if (word.endsWith("ла") || word.endsWith("лась") || word.endsWith("лася")) {
            return Ending.FEMININE;
        }
        if (word.endsWith("ло") || word.endsWith("лось") || word.endsWith("лося")) {
            return Ending.NEUTER;
        }
        return word.endsWith("в") || word.endsWith("вся") ? Ending.MASCULINE : Ending.NONE;
    }
}
