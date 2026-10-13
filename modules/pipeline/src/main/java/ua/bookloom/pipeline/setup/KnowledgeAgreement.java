package ua.bookloom.pipeline.setup;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.BriefField;

/**
 * Whether what the model knows of the book, or what one window read, says the same as a synthesized value (15i.B4).
 * A cited knowledge proves
 * nothing by itself: it counts only when the accepted answer's own field holds the value. A genre agrees when every
 * telling word of the listed name stands in the knowledge ("Crime fiction" in "crime novel"); an audience when both
 * read as the same of the five audiences; a voice when half the words of the shorter phrase are shared.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KnowledgeAgreement {

    private static final int MIN_WORD = 3;
    private static final Set<String> FILLER = Set.of("and", "the", "with", "for", "from", "that", "this", "its");
    // Words every genre name may carry without telling one genre from another.
    private static final Set<String> GENERIC = Set.of("fiction", "novel", "novels", "literature", "book", "stories");
    // Checked in this order, so "young adult readers" is not read as adult.
    private static final List<Cue> AUDIENCE_CUES = List.of(
            new Cue("young adult", List.of("young adult", "young-adult", "teen")),
            new Cue("children", List.of("child", "kids", "middle grade")),
            new Cue("academic", List.of("academic", "scholar", "student")),
            new Cue("adult", List.of("adult", "grown")),
            new Cue("general", List.of("general", "all ages", "everyone")));

    /**
     * The words that mark an answer as one of the five audiences.
     *
     * @param audience the audience as {@link BriefSynthesisSchema#AUDIENCES} names it
     * @param words the lower-case words that mark it
     */
    private record Cue(String audience, List<String> words) {}

    /**
     * Whether the accepted knowledge holds the value.
     *
     * @param field genre, audience or voice
     * @param value the synthesized value, already one the brief can hold
     * @param knowledge the verdict on what the model knows of the book
     * @return {@code true} if the book is known and its answer's field says the same, {@code false} otherwise
     */
    static boolean agrees(final BriefField field, final String value, final Knowledge knowledge) {
        final BookKnowledgeReply reply = knowledge.reply();
        final String known = reply == null
                ? null
                : switch (field) {
                    case GENRE -> reply.genre();
                    case AUDIENCE -> reply.audience();
                    case VOICE -> reply.era();
                    case REGISTER, NARRATOR, NARRATOR_GENDER -> null;
                };
        if (known == null) {
            log.debug("Knowledge has no {} to agree with", field);
            return false;
        }
        final boolean agrees = saysSame(field, value, known);
        log.debug("Knowledge {} agrees with the synthesis: {}", field, agrees);
        log.trace("Knowledge {} '{}' against '{}'", field, known, value);
        return agrees;
    }

    /**
     * Whether a text says the same as a synthesized value, by the field's own matcher.
     *
     * @param field genre, audience or voice; any other field never agrees
     * @param value the synthesized value, already one the brief can hold
     * @param text what a window's reading or the model's knowledge says of the field
     * @return {@code true} if the text says the value, {@code false} otherwise
     */
    static boolean saysSame(final BriefField field, final String value, final String text) {
        return switch (field) {
            case GENRE -> genreAgrees(value, text);
            case AUDIENCE -> value.equals(audienceOf(text));
            case VOICE -> voiceAgrees(value, text);
            case REGISTER, NARRATOR, NARRATOR_GENDER -> false;
        };
    }

    private static boolean genreAgrees(final String genre, final String known) {
        final Set<String> telling = new HashSet<>(words(genre));
        telling.removeAll(GENERIC);
        return !telling.isEmpty() && words(known).containsAll(telling);
    }

    private static @Nullable String audienceOf(final String known) {
        final String text = known.toLowerCase(Locale.ROOT);
        return AUDIENCE_CUES.stream()
                .filter(cue -> cue.words().stream().anyMatch(text::contains))
                .map(Cue::audience)
                .findFirst()
                .orElse(null);
    }

    private static boolean voiceAgrees(final String voice, final String known) {
        final Set<String> left = words(voice);
        final Set<String> right = words(known);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        final long shared = left.stream().filter(right::contains).count();
        return 2 * shared >= Math.min(left.size(), right.size());
    }

    private static Set<String> words(final String phrase) {
        return Arrays.stream(phrase.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= MIN_WORD && !FILLER.contains(word))
                .collect(Collectors.toSet());
    }
}
