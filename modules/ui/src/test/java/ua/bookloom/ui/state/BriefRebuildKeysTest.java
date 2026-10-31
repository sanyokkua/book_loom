package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;

/** A rebuilt brief keeps the policy keys and the likely marks it is not about. */
class BriefRebuildKeysTest {

    private final BookBrief held = BriefKeysFixtures.withKeys(BookBrief.defaults("en"));

    // IF a rebuild dropped the keys, THEN changing a name policy would silently reset the declared quote marks.
    @Test
    void of_anyChange_keepsApostropheQuoteStyleAddressConventionsAndLikely() {
        final BookBrief rebuilt =
                BriefRebuild.of(held, Register.CASUAL, NamePolicy.KEEP_ORIGINAL, null, null, null, null, null);

        assertThat(rebuilt.register()).isEqualTo(Register.CASUAL);
        assertThat(rebuilt.apostrophe()).isEqualTo("’");
        assertThat(rebuilt.quoteStyle()).isEqualTo(BriefKeysFixtures.QUOTES);
        assertThat(rebuilt.defaultAddress()).isEqualTo(BriefKeysFixtures.ADDRESS);
        assertThat(rebuilt.conventions()).isEqualTo(BriefKeysFixtures.CONVENTIONS);
        assertThat(rebuilt.likely()).isEqualTo(BriefKeysFixtures.LIKELY);
    }
}
