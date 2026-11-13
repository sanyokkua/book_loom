package ua.bookloom.pipeline.typography;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.glossary.TargetText;
import ua.bookloom.util.text.StressMarks;

/**
 * Removes stress marks from a candidate when the target language's file asks for it, before any check reads the text.
 * A language that does not ask (Bulgarian writes a grave as part of a letter) keeps every mark.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StressStrip {

    static Edit apply(final String text, final String targetLanguage) {
        if (!TargetText.stripsStress(targetLanguage)) {
            return new Edit(text, 0);
        }
        final int marks = StressMarks.count(text);
        return marks == 0 ? new Edit(text, 0) : new Edit(StressMarks.strip(text), marks);
    }
}
