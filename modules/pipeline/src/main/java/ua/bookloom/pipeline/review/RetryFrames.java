package ua.bookloom.pipeline.review;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.checks.WordValidator;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.RequestDefaults;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** Builds the call frame a retry replays: the run's own frame, with the style sheet the snapshot stored. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RetryFrames {

    static CallFrame of(
            final BookBrief brief,
            final ContextSnapshot snapshot,
            final Document document,
            final @Nullable BookIndex index,
            final RequestDefaults requestDefaults) {
        return CallFrame.forRun(
                brief,
                StyleSheet.ofText(snapshot.styleSheet()),
                document,
                CallFrame.scriptOf(index),
                WordValidator.none(),
                requestDefaults);
    }
}
