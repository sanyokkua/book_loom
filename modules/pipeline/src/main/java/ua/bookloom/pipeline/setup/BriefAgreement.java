package ua.bookloom.pipeline.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.api.pipeline.BriefSuggestion;
import ua.bookloom.api.pipeline.FieldEvidence;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.NarratorPerson;
import ua.bookloom.api.project.Register;
import ua.bookloom.pipeline.setup.BriefReplies.Answer;

/**
 * Keeps a brief field only when every sample of the book gave the same supported answer; a field the samples disagree
 * on, or one a sample could not support, stays neutral or unknown. The same answer means: the same word for a field
 * with a closed vocabulary (register, narrator, gender); for the genre and the audience, one phrase's words all in the
 * other's ("science fiction" and "cyberpunk science fiction"); for the voice, at least half of the shorter phrase's
 * words in the other. A kept phrase is the shorter one, which is what both samples say.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BriefAgreement {

    private static final int MIN_WORD = 3;
    private static final Set<String> FILLER = Set.of("and", "the", "with", "for", "from", "that", "this", "its");

    /**
     * Combines the samples' answers into one suggestion.
     *
     * @param answers each sample's supported answers, in sample order; null for a sample whose reply was unreadable
     * @param samples how many samples the agreement counts, at least {@code answers.size()}; a sample that could not be
     *     taken counts as one that gave no answer
     * @return the suggestion with the evidence of every field
     */
    static BriefSuggestion combine(final List<@Nullable Map<BriefField, Answer>> answers, final int samples) {
        Objects.requireNonNull(answers, "answers");
        final Map<BriefField, String> values = new EnumMap<>(BriefField.class);
        final Map<BriefField, FieldEvidence> evidence = new EnumMap<>(BriefField.class);
        for (final BriefField field : BriefField.values()) {
            final List<@Nullable Answer> given = new ArrayList<>();
            for (int sample = 0; sample < samples; sample++) {
                final Map<BriefField, Answer> read = sample < answers.size() ? answers.get(sample) : null;
                given.add(read == null ? null : read.get(field));
            }
            final Answer kept = kept(field, given);
            values.put(field, kept == null ? "" : kept.value());
            evidence.put(field, evidenceOf(field, given, kept, samples));
        }
        return new BriefSuggestion(
                blankToNull(values.get(BriefField.GENRE)),
                register(values.get(BriefField.REGISTER)),
                blankToNull(values.get(BriefField.VOICE)),
                blankToNull(values.get(BriefField.AUDIENCE)),
                narrator(values.get(BriefField.NARRATOR)),
                gender(values.get(BriefField.NARRATOR_GENDER)),
                evidence);
    }

    private static @Nullable Answer kept(final BriefField field, final List<@Nullable Answer> given) {
        final Answer first = given.getFirst();
        if (first == null || given.stream().anyMatch(answer -> answer == null || !agree(field, first, answer))) {
            return null;
        }
        Answer shortest = first;
        for (final Answer answer : given) {
            if (words(Objects.requireNonNull(answer, "answer").value()).size()
                    < words(shortest.value()).size()) {
                shortest = answer;
            }
        }
        return shortest;
    }

    private static FieldEvidence evidenceOf(
            final BriefField field,
            final List<@Nullable Answer> given,
            @Nullable final Answer kept,
            final int samples) {
        if (kept != null) {
            final List<String> quotes = given.stream()
                    .map(answer -> Objects.requireNonNull(answer, "answer").quote())
                    .filter(quote -> !quote.isBlank())
                    .distinct()
                    .toList();
            log.debug("Brief field {} kept: all {} samples agree", field, samples);
            return new FieldEvidence(quotes, samples, samples);
        }
        int most = 0;
        for (final Answer answer : given) {
            final Answer one = answer;
            final int same = one == null
                    ? 0
                    : (int) given.stream()
                            .filter(other -> other != null && agree(field, one, other))
                            .count();
            most = Math.max(most, same);
        }
        log.warn("Brief field {} is uncertain: {} of {} samples agree", field, most, samples);
        return new FieldEvidence(List.of(), most, samples);
    }

    static boolean agree(final BriefField field, final Answer one, final Answer other) {
        if (one.value().equalsIgnoreCase(other.value())) {
            return true;
        }
        final Set<String> left = words(one.value());
        final Set<String> right = words(other.value());
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return switch (field) {
            case REGISTER, NARRATOR, NARRATOR_GENDER -> false;
            case GENRE, AUDIENCE -> left.containsAll(right) || right.containsAll(left);
            case VOICE -> 2 * overlap(left, right) >= Math.min(left.size(), right.size());
        };
    }

    private static long overlap(final Set<String> left, final Set<String> right) {
        return left.stream().filter(right::contains).count();
    }

    private static Set<String> words(final String phrase) {
        return Arrays.stream(phrase.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= MIN_WORD && !FILLER.contains(word))
                .collect(Collectors.toSet());
    }

    private static @Nullable String blankToNull(@Nullable final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static Register register(@Nullable final String value) {
        return switch (value == null ? "" : value) {
            case "formal" -> Register.FORMAL_LITERARY;
            case "casual" -> Register.CASUAL;
            default -> Register.NEUTRAL;
        };
    }

    private static NarratorPerson narrator(@Nullable final String value) {
        return switch (value == null ? "" : value) {
            case "first" -> NarratorPerson.FIRST;
            case "third" -> NarratorPerson.THIRD;
            default -> NarratorPerson.UNSPECIFIED;
        };
    }

    private static Gender gender(@Nullable final String value) {
        return switch (value == null ? "" : value) {
            case "male" -> Gender.MALE;
            case "female" -> Gender.FEMALE;
            default -> Gender.UNKNOWN;
        };
    }
}
