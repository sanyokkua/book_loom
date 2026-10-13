package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;

/**
 * How a person's gender follows from the readings' verified votes and the code's pronoun tally (15i.N5), in one of two
 * modes. A model whose votes the coreference probe (15i.N4) found right decides with them; any other model's votes only
 * break ties, the code's tally deciding wherever it is clear.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ReadingVotes {

    /** A gender is verified from this many verified votes making {@link #VERIFIED_SHARE} of them. */
    static final int MIN_VERIFIED_VOTES = 2;

    static final double VERIFIED_SHARE = 0.75;

    /** Under Auto the code reads no pronoun of its own, so a verified gender needs more votes and a larger share. */
    static final int MIN_VERIFIED_VOTES_AUTO = 3;

    static final double VERIFIED_SHARE_AUTO = 0.8;

    /** What the readings' votes may do. */
    enum VoteMode {
        /**
         * The votes decide: verified at the thresholds unless a tally favours the other gender, and a majority against
         * a dominant tally earns one tiebreak call.
         */
        DECIDE,
        /**
         * The votes only break ties: a clear code tally decides and is written verified; with none, votes at the
         * thresholds give a suggestion, never a verified gender, and no tiebreak call is made.
         */
        TIEBREAK
    }

    /**
     * A gender decision: the gender, where it came from, and whether it waits for a tiebreak.
     *
     * @param gender the gender; {@link Gender#UNKNOWN} when none
     * @param origin verified or suggested; null exactly when the gender is unknown
     * @param needsTiebreak whether the gender waits for one tiebreak call
     */
    record GenderCall(Gender gender, @Nullable GenderOrigin origin, boolean needsTiebreak) {

        static final GenderCall NONE = new GenderCall(Gender.UNKNOWN, null, false);

        static GenderCall of(final Gender gender, final GenderOrigin origin) {
            return new GenderCall(gender, origin, false);
        }
    }

    /**
     * The mode a model's votes are read in.
     *
     * @param modelId the model the readings came from; null when the caller does not know it, as the glossary review
     *     does not today
     * @return {@link VoteMode#TIEBREAK} for every model today
     */
    static VoteMode modeFor(@Nullable final String modelId) {
        // A model enters DECIDE only after passing the coref gate (15i.N4: at most 5 % wrong votes). None has: on
        // 2026-10-13 26b gave 10 % and e4b 20 % wrong votes, so there is no allowlist yet.
        log.debug("Reading votes for model {}: votes only break ties", modelId);
        return VoteMode.TIEBREAK;
    }

    /**
     * The gender of a person from its cluster's verified votes and pooled tally.
     *
     * @param facts the readings and what the code knows; the entry is a person whose gender the person did not set
     * @param mode what the votes may do
     * @return the gender call; never null
     */
    static GenderCall genderOf(final Facts facts, final VoteMode mode) {
        final List<ReadCall> cluster = facts.clusterCalls();
        final int male = ReadingCounts.votes(cluster, Gender.MALE);
        final int female = ReadingCounts.votes(cluster, Gender.FEMALE);
        final PronounGender.Evidence tally = facts.evidence().pronouns();
        log.debug(
                "Reading votes for entry {} ({}): male={} female={} tally female={} male={}",
                facts.entry().id(),
                mode,
                male,
                female,
                tally.female(),
                tally.male());
        final Gender majority = male == female ? Gender.UNKNOWN : male > female ? Gender.MALE : Gender.FEMALE;
        final boolean enough = isEnough(Math.max(male, female), male + female, facts.analysisLanguage() == null);
        return switch (mode) {
            case DECIDE -> deciding(majority, enough, tally);
            case TIEBREAK -> breakingTies(majority, enough, tally);
        };
    }

    private static boolean isEnough(final int count, final int votes, final boolean auto) {
        final boolean enough = votes > 0
                && count >= (auto ? MIN_VERIFIED_VOTES_AUTO : MIN_VERIFIED_VOTES)
                && (double) count / votes >= (auto ? VERIFIED_SHARE_AUTO : VERIFIED_SHARE);
        log.debug("Reading votes: {} of {} votes (auto {}) reach the thresholds: {}", count, votes, auto, enough);
        return enough;
    }

    private static GenderCall deciding(
            final Gender majority, final boolean enough, final PronounGender.Evidence tally) {
        if (majority == Gender.UNKNOWN) {
            return tally.isStrong()
                    ? GenderCall.of(tally.dominant().orElseThrow(), GenderOrigin.VERIFIED)
                    : GenderCall.NONE;
        }
        final Optional<Gender> dominant = tally.dominant();
        if (dominant.isPresent() && dominant.get() != majority) {
            log.debug("Reading votes: {} against a dominant tally of {}: a tiebreak", majority, dominant.get());
            return new GenderCall(Gender.UNKNOWN, null, true);
        }
        final Gender favoured = TermReviewReplies.favouredBy(tally);
        if (favoured != null && favoured != majority) {
            log.debug("Reading votes: {} against a tally favouring {}: the tally's, suggested", majority, favoured);
            return GenderCall.of(favoured, GenderOrigin.SUGGESTED);
        }
        return GenderCall.of(majority, enough ? GenderOrigin.VERIFIED : GenderOrigin.SUGGESTED);
    }

    private static GenderCall breakingTies(
            final Gender majority, final boolean enough, final PronounGender.Evidence tally) {
        final Gender clear = TermReviewReplies.favouredBy(tally);
        if (clear != null) {
            log.debug("Reading votes: the tally is clear for {}, votes for {} only break ties", clear, majority);
            return GenderCall.of(clear, GenderOrigin.VERIFIED);
        }
        if (majority != Gender.UNKNOWN && enough) {
            log.debug("Reading votes: no clear tally, {} by the votes, suggested", majority);
            return GenderCall.of(majority, GenderOrigin.SUGGESTED);
        }
        return GenderCall.NONE;
    }
}
