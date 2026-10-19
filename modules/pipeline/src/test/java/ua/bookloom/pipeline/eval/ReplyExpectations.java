package ua.bookloom.pipeline.eval;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.eval.ReplyCase.Expect;

/**
 * What production does with the two reply defects that plan tasks B2 and B3 turned from refused to repaired. One place
 * to flip, so a corpus case is never edited to follow the code; a variant that cannot be repaired is a plain
 * {@code REFUSED} case.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplyExpectations {

    /**
     * A target with U+001C..U+001F where « » belong. The marks are mapped back by position (plan task B2) and the reply
     * is {@link Expect#ACCEPTED}; a code with no clear place stays {@link Expect#REFUSED}.
     */
    static final Expect CONTROL_CHARS = Expect.ACCEPTED;

    /**
     * Closers (a brace and a bracket) after the reply's JSON object. The tail is cut (plan task B3) and the reply is
     * {@link Expect#ACCEPTED}.
     */
    static final Expect TRAILING_CLOSERS = Expect.ACCEPTED;
}
