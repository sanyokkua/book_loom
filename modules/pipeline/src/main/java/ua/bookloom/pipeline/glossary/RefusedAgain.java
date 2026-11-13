package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Refusal;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Replies;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Standing;
import ua.bookloom.pipeline.glossary.SuggestionReplies.Suggestion;

/** What the one retry of a refused rendering settles. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RefusedAgain {

    /**
     * The retry's suggestions, plus a one-word place whose rendering the letter table refused and the retry gave
     * again unchanged: a model that insists on {@code Земля} for {@code Earth} knows an established word of the
     * target language that no letter table derives, whereas a mis-spelled sound-alike is changed on the retry.
     *
     * @param refused what the first answer had refused, by entry id; never null
     * @param retry the retry's reading; never null
     * @return the suggestions to keep; never null
     */
    static List<Suggestion> settle(final Map<String, Refusal> refused, final Replies retry) {
        final List<Suggestion> settled = new ArrayList<>(retry.kept());
        for (final Refusal again : retry.refused()) {
            final Refusal first = refused.get(again.entry().id());
            if (first != null && isEstablishedWord(first, again)) {
                log.debug(
                        "Entry {}: the rendering was given again after the letter check, kept",
                        first.entry().id());
                settled.add(new Suggestion(again.entry(), again.rendering()));
            }
        }
        return settled;
    }

    private static boolean isEstablishedWord(final Refusal first, final Refusal again) {
        return first.entry().type() == TermType.PLACE
                && first.entry().term().strip().indexOf(' ') < 0
                && first.why().equals(Standing.LETTERS.reason())
                && again.why().equals(Standing.LETTERS.reason())
                && first.rendering().equalsIgnoreCase(again.rendering());
    }

    static void warnLeftWithoutTarget(
            final int index, final Map<String, Refusal> refused, final List<Suggestion> retried) {
        final Set<String> answered = retried.stream().map(s -> s.entry().id()).collect(Collectors.toSet());
        refused.forEach((id, refusal) -> {
            if (!answered.contains(id)) {
                log.warn(
                        "Suggestion batch {}: entry {} is left without a target, refused: {}",
                        index,
                        id,
                        refusal.why());
            }
        });
    }
}
