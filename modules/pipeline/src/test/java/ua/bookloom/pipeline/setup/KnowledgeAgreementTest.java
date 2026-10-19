package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.pipeline.BriefField;
import ua.bookloom.pipeline.setup.Knowledge.Reason;

/** A cited knowledge counts only when the accepted answer's own field says the same as the synthesized value. */
class KnowledgeAgreementTest {

    private static Knowledge knows(final String genre, final String audience, final String era) {
        return Knowledge.known(new BookKnowledgeReply(
                true, "The Tin Orchard", "Pell Marrow", 1911, genre, audience, era, null, null, 0.9));
    }

    // IF "Historical romance" agreed with "historical novel", THEN every historical book would read as a romance.
    @ParameterizedTest
    @CsvSource({
        "Crime fiction, crime novel, true",
        "Mystery, mystery novel, true",
        "Horror, gothic horror, true",
        "Historical romance, historical novel, false",
        "Mystery, romance, false"
    })
    void agrees_genre_needsEveryTellingWordOfTheName(final String value, final String known, final boolean expected) {
        assertThat(KnowledgeAgreement.agrees(BriefField.GENRE, value, knows(known, "", "")))
                .isEqualTo(expected);
    }

    // IF "adult" were found in "young adult readers", THEN a book for teenagers would be told as one for adults.
    @ParameterizedTest
    @CsvSource({
        "adult, adult readers, true",
        "adult, young adult readers, false",
        "young adult, teenagers, true",
        "children, children aged eight to twelve, true",
        "academic, students of history, true",
        "general, general readers, true",
        "general, nobody in particular, false"
    })
    void agrees_audience_readsTheKnowledgeAsOneOfTheFive(
            final String value, final String known, final boolean expected) {
        assertThat(KnowledgeAgreement.agrees(BriefField.AUDIENCE, value, knows("", known, "")))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
        "'formal letters, late Victorian', 'late Victorian, formal', true",
        "terse and wry, late twentieth century, false"
    })
    void agrees_voice_sharesHalfTheShorterPhrase(final String value, final String known, final boolean expected) {
        assertThat(KnowledgeAgreement.agrees(BriefField.VOICE, value, knows("", "", known)))
                .isEqualTo(expected);
    }

    @Test
    void agrees_bookUnknown_isFalse() {
        assertThat(KnowledgeAgreement.agrees(BriefField.GENRE, "Mystery", Knowledge.unknown(Reason.NOT_KNOWN)))
                .isFalse();
    }

    @Test
    void agrees_knowledgeWithoutTheField_isFalse() {
        assertThat(KnowledgeAgreement.agrees(
                        BriefField.GENRE,
                        "Mystery",
                        Knowledge.known(new BookKnowledgeReply(
                                true, "The Tin Orchard", null, null, null, null, null, null, null, 0.9))))
                .isFalse();
    }
}
