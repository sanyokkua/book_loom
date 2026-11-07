package ua.bookloom.pipeline.eval;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.project.BookBrief;

/**
 * The chapter-summaries switch an eval run forces on its brief (arm 6), read from {@value #ENV}: {@code on} or
 * {@code off} forces the switch as the person does on the Book Brief; unset or blank leaves the brief to follow its
 * story shape, which the mini and sequence briefs leave unread, so summaries are on. Only the eval reads this variable.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalChapterSummaries {

    static final String ENV = "BOOKLOOM_EVAL_CHAPTER_SUMMARIES";

    /**
     * The brief with the switch the environment asks for.
     *
     * @param brief the non-null brief the run would import the book with
     * @param env the non-null environment
     * @return {@code brief} with the switch forced, or {@code brief} itself when {@value #ENV} is unset or blank
     * @throws IllegalArgumentException when the value is neither {@code on} nor {@code off}, so a typo never runs the
     *     default side silently
     */
    static BookBrief apply(final BookBrief brief, final Map<String, String> env) {
        Objects.requireNonNull(brief, "brief");
        final String asked =
                Objects.requireNonNull(env, "env").getOrDefault(ENV, "").strip().toLowerCase(Locale.ROOT);
        final BookBrief chosen =
                switch (asked) {
                    case "" -> brief;
                    case "on" -> brief.withChapterSummaries(true);
                    case "off" -> brief.withChapterSummaries(false);
                    default -> throw new IllegalArgumentException(ENV + " takes on or off, not " + asked);
                };
        log.debug("Eval chapter summaries asked={} summariesOn={}", asked, chosen.summariesOn());
        return chosen;
    }
}
