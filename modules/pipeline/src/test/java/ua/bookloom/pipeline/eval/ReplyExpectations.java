package ua.bookloom.pipeline.eval;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.eval.ReplyCase.Expect;

/**
 * What production is expected to do with the two reply defects that a planned change turns from refused to repaired.
 * One place to flip when the change lands, so a corpus case is never edited to follow the code.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReplyExpectations {

    /**
     * A target with U+001C..U+001F where « » belong. Today the reply parser refuses it whole; after plan task B2 the
     * marks are mapped back and the reply is {@link Expect#ACCEPTED}.
     */
    static final Expect CONTROL_CHARS = Expect.REFUSED;

    /**
     * Closers (a brace and a bracket) after the reply's JSON object. Today the parser refuses it whole; after plan task B3 the
     * tail is cut and the reply is {@link Expect#ACCEPTED}.
     */
    static final Expect TRAILING_CLOSERS = Expect.REFUSED;
}
