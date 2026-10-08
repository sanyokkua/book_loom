package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;

/** The brief, call frame and segments a stage case stands on, made the way the other suites make theirs. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StageFrames {

    /** The eval's brief for a language pair, with the owner's settings from the environment. */
    static BookBrief brief(final String source, final String target) {
        return EvalBrief.of(source, target, System.getenv());
    }

    /** The frame every call of the case is sent under. */
    static CallFrame frame(final BookBrief brief, final String source, final String target) {
        return new CallFrame(source, target, StyleSheet.from(brief), brief.foreignPassages(), null, brief.narrator());
    }

    /** The case's lines as the body segments of the eval's one unit. */
    static List<Segment> segments(final List<String> sentences) {
        return IntStream.range(0, sentences.size())
                .mapToObj(order -> EvalProject.segment(order, sentences.get(order)))
                .toList();
    }
}
