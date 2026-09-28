package ua.bookloom.pipeline.prompt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Today's fixed style line, until the Book Brief derives a style sheet (task 7.2). */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DefaultStyleSheet {

    static final String TEXT = "Neutral, faithful literary prose: keep the author's register, "
            + "sentence rhythm and paragraph breaks; use the standard modern orthography of the target language.";
}
