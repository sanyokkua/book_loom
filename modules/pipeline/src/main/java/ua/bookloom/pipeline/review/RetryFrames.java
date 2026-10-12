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
import ua.bookloom.pipeline.prompt.StyleSheet;

/** Builds the call frame a retry replays: the run's own language pair and style sheet, as the snapshot stored them. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RetryFrames {

    static CallFrame of(
            final BookBrief brief,
            final String target,
            final ContextSnapshot snapshot,
            final Document document,
            final @Nullable BookIndex index) {
        return new CallFrame(
                brief.sourceLanguage(),
                target,
                StyleSheet.ofText(snapshot.styleSheet()),
                brief.foreignPassages(),
                CallFrame.bookLanguageOf(document),
                brief.narrator(),
                WordValidator.none(),
                index == null ? null : index.majorityScript().orElse(null));
    }
}
