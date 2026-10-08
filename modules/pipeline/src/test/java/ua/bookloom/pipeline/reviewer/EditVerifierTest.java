package ua.bookloom.pipeline.reviewer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link EditVerifier}: an edit changes the book only when the code can show it is safe. */
class EditVerifierTest {

    private static final EditVerifier VERIFIER = new EditVerifier();
    private static final CandidateChecker CLEAN = text -> Optional.of(Set.of());
    private static final Set<String> NO_BLOCKERS = Set.of();
    private static final String CANDIDATE = "Марія сказав, що ⟦g0⟧він⟦g1⟧ втомилась.";

    private static ReviewEdit edit(final String quote, final String replacement) {
        return new ReviewEdit(ReviewCriterion.INVENTED_WORD, quote, replacement);
    }

    @Test
    void verify_quotePresentOnce_appliesTheReplacementAtThatPlace() {
        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("Марія сказав,", "Марія сказала,"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Verified("Марія сказала, що ⟦g0⟧він⟦g1⟧ втомилась."));
    }

    @Test
    void verify_quoteAbsent_isIgnoredAsHallucinated() {
        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("Марія казала", "Марія сказала"), CLEAN, NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    @Test
    void verify_quoteTwice_failsAsAmbiguous() {
        final Verification result =
                VERIFIER.verify("Він ішов. Він ішов.", edit("Він ішов", "Вона йшла"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.AMBIGUOUS_QUOTE));
    }

    @Test
    void verify_quoteTwiceWhoseChangeDoesNotFitItsCriterion_isIgnoredNotAmbiguous() {
        final ReviewEdit shrinkingOmission = new ReviewEdit(ReviewCriterion.OMISSION, "Він ішов", "Ні");

        final Verification result = VERIFIER.verify("Він ішов. Він ішов.", shrinkingOmission, CLEAN, NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    @Test
    void verify_quoteOverlappingItself_failsAsAmbiguous() {
        final Verification result = VERIFIER.verify("ааа", edit("аа", "б"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.AMBIGUOUS_QUOTE));
    }

    @Test
    void verify_quoteWithDifferentSpacingAndApostrophe_isFoundAfterNormalisation() {
        final String candidate = "Це його  м’яч.";

        final Verification result =
                VERIFIER.verify(candidate, edit("Це його м'яч.", "Це її м'яч."), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Verified("Це її м'яч."));
    }

    @Test
    void verify_replacementDropsAToken_failsAsTokensChanged() {
        final Verification result = VERIFIER.verify(CANDIDATE, edit("⟦g0⟧він⟦g1⟧", "вона"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.TOKENS_CHANGED));
    }

    @Test
    void verify_replacementReordersTokens_failsAsTokensChanged() {
        final Verification result =
                VERIFIER.verify("⟦g0⟧а⟦g1⟧ ⟦g2⟧б⟦g3⟧", edit("⟦g0⟧а⟦g1⟧ ⟦g2⟧", "⟦g2⟧а⟦g1⟧ ⟦g0⟧"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.TOKENS_CHANGED));
    }

    @Test
    void verify_quoteCutsATokenInHalf_failsAsTokensChanged() {
        final Verification result = VERIFIER.verify(CANDIDATE, edit("⟦g0", "X"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.TOKENS_CHANGED));
    }

    @Test
    void verify_editMakesAChecksBlock_failsAsBlockersGrew() {
        final CandidateChecker unbalancing = text -> Optional.of(Set.of("quote-balance"));

        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("втомилась", "«втомилась"), unbalancing, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.BLOCKERS_GREW));
    }

    @Test
    void verify_editLeavesABlockerTheCandidateAlreadyHad_isVerified() {
        final CandidateChecker same = text -> Optional.of(Set.of("quote-balance"));

        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("Марія сказав,", "Марія сказала,"), same, Set.of("quote-balance"));

        assertThat(result).isInstanceOf(Verification.Verified.class);
    }

    @Test
    void verify_aHardGateRefusesTheEditedText_failsAsChecksRefused() {
        final CandidateChecker refusing = text -> Optional.empty();

        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("Марія сказав,", "Марія сказала,"), refusing, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Failed(Verification.FailureReason.CHECKS_REFUSED));
    }

    @Test
    void verify_replacementEmpty_deletesTheQuote() {
        final Verification result =
                VERIFIER.verify("Він ішов абракадабра далі.", edit(" абракадабра", ""), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Verified("Він ішов далі."));
    }

    @Test
    void verify_replacementEqualToQuote_isIgnoredAsNoChange() {
        final Verification result = VERIFIER.verify(CANDIDATE, edit("Марія", "Марія"), CLEAN, NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    // IF an agreement edit could drop "би", THEN a reviewer would change the mood of a sentence it was only checking.
    @Test
    void verify_agreementEditDroppingAFunctionWord_isIgnored() {
        final ReviewEdit dropsBy =
                new ReviewEdit(ReviewCriterion.AGREEMENT, "міг би пройти їхню охорону.", "міг пройти їхню охорону.");

        final Verification result = VERIFIER.verify(
                "Він міг би пройти їхню охорону.", dropsBy, CLEAN, NO_BLOCKERS, List.of(), Set.of("би", "не"));

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    // IF a reply's control characters were written into the book, THEN the quote marks would be gone.
    @Test
    void verify_replacementWithControlCharacters_isIgnored() {
        final Verification result =
                VERIFIER.verify(CANDIDATE, edit("Марія сказав,", "\u001eМарія сказала\u001d,"), CLEAN, NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    // IF an edit could never keep a control character the book's own text holds (an old TXT's page break), THEN no
    // segment holding one could ever be fixed.
    @Test
    void verify_replacementKeepingTheQuotesControlCharacter_isVerified() {
        final Verification result = VERIFIER.verify(
                "Марія сказав,\fі пішла.", edit("Марія сказав,\fі", "Марія сказала,\fі"), CLEAN, NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Verified("Марія сказала,\fі пішла."));
    }

    @Test
    void occursIn_quoteThatOnlyMatchesAfterNormalising_isTrue() {
        assertThat(EditVerifier.occursIn("м’яч", "м'яч")).isTrue();
    }

    @Test
    void verify_omissionEditThatShortensItsQuote_isIgnoredAsAParaphrase() {
        final Verification result = VERIFIER.verify(
                CANDIDATE,
                new ReviewEdit(ReviewCriterion.OMISSION, "що ⟦g0⟧він⟦g1⟧ втомилась", "що"),
                CLEAN,
                NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    @Test
    void verify_languageEditOnWordsOfTheCandidatesOwnScript_isIgnored() {
        final Verification result = VERIFIER.verify(
                "Мені все одно, хоч як.",
                new ReviewEdit(ReviewCriterion.LANGUAGE, "хоч як", "все ж"),
                CLEAN,
                NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    @Test
    void verify_languageEditOnALatinLetterInACyrillicWord_isVerified() {
        final Verification result = VERIFIER.verify(
                "Він наполегливо навчaвся.",
                new ReviewEdit(ReviewCriterion.LANGUAGE, "навчaвся", "навчався"),
                CLEAN,
                NO_BLOCKERS);

        assertThat(result).isEqualTo(new Verification.Verified("Він наполегливо навчався."));
    }

    @Test
    void verify_terminologyEditWithANameTheCandidateAlreadyUses_isVerified() {
        final Verification result = VERIFIER.verify(
                "Чарівник підняв посох. Фокусник заговорив.",
                new ReviewEdit(ReviewCriterion.TERMINOLOGY, "Фокусник заговорив", "Чарівник заговорив"),
                CLEAN,
                NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Verified.class);
    }

    @Test
    void verify_terminologyEditWithAWordNobodyUses_isIgnoredUnlessTheGlossaryHasIt() {
        final ReviewEdit edit = new ReviewEdit(ReviewCriterion.TERMINOLOGY, "посів трон", "взяв трон");

        assertThat(VERIFIER.verify("Син посів трон.", edit, CLEAN, NO_BLOCKERS))
                .isInstanceOf(Verification.Ignored.class);
        assertThat(VERIFIER.verify("Син посів трон.", edit, CLEAN, NO_BLOCKERS, java.util.List.of("взяв")))
                .isInstanceOf(Verification.Verified.class);
    }

    @Test
    void verify_meaningEditThatDeletesMostOfItsQuote_isIgnored() {
        final Verification result = VERIFIER.verify(
                "Вони пояснили імпортну причину.",
                new ReviewEdit(ReviewCriterion.MEANING, "імпортну причину", "імпорт"),
                CLEAN,
                NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }

    @Test
    void verify_quotesEditThatTouchesNoQuoteMark_isIgnored() {
        final Verification result = VERIFIER.verify(
                "Він пішов.", new ReviewEdit(ReviewCriterion.QUOTES, "пішов", "ішов"), CLEAN, NO_BLOCKERS);

        assertThat(result).isInstanceOf(Verification.Ignored.class);
    }
}
