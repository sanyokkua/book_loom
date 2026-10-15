package ua.bookloom.pipeline.glossary;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;

/**
 * How a person's gender follows from the readings' verified votes and the code's pronoun tally (15i.N5). No model's
 * votes passed the coreference probe (15i.N4: 26b 10 %, e4b 20 % wrong votes against 5 %), so the votes only break
 * ties: the code's tally decides wherever it is clear, verified where the votes agree. A mode in which a model's votes
 * decide, with its tiebreak call, returns with the first model that passes the probe (15i review: removed as dead).
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

    /**
     * With no clear tally and too few votes, this many pieces of evidence (verified votes and pronouns together), all
     * one gender's, still suggest it.
     */
    static final int MIN_ONE_SIDED = 2;

    /**
     * A gender decision: the gender and where it came from.
     *
     * @param gender the gender; {@link Gender#UNKNOWN} when none
     * @param origin verified or suggested; null exactly when the gender is unknown
     * @param noEvidence whether the book gave this person no evidence at all: a pronoun tally of 0/0 and no verified
     *     vote; only for an unknown gender
     */
    record GenderCall(Gender gender, @Nullable GenderOrigin origin, boolean noEvidence) {

        static final GenderCall NONE = new GenderCall(Gender.UNKNOWN, null, false);

        static final GenderCall NO_EVIDENCE = new GenderCall(Gender.UNKNOWN, null, true);

        static GenderCall of(final Gender gender, final GenderOrigin origin) {
            return new GenderCall(gender, origin, false);
        }
    }

    /**
     * The gender of a person from its cluster's verified votes and pooled tally.
     *
     * @param facts the readings and what the code knows; the entry is a person whose gender the person did not set
     * @return the gender call; never null
     */
    static GenderCall genderOf(final Facts facts) {
        final List<ReadCall> cluster = facts.clusterCalls();
        final int male = ReadingCounts.votes(cluster, Gender.MALE);
        final int female = ReadingCounts.votes(cluster, Gender.FEMALE);
        final PronounGender.Evidence tally = facts.evidence().pronouns();
        log.debug(
                "Reading votes for entry {}: male={} female={} tally female={} male={}",
                facts.entry().id(),
                male,
                female,
                tally.female(),
                tally.male());
        final Gender majority = male == female ? Gender.UNKNOWN : male > female ? Gender.MALE : Gender.FEMALE;
        final boolean enough = isEnough(Math.max(male, female), male + female, facts.analysisLanguage() == null);
        return breakingTies(majority, enough, tally, male, female);
    }

    private static boolean isEnough(final int count, final int votes, final boolean auto) {
        final boolean enough = votes > 0
                && count >= (auto ? MIN_VERIFIED_VOTES_AUTO : MIN_VERIFIED_VOTES)
                && (double) count / votes >= (auto ? VERIFIED_SHARE_AUTO : VERIFIED_SHARE);
        log.debug("Reading votes: {} of {} votes (auto {}) reach the thresholds: {}", count, votes, auto, enough);
        return enough;
    }

    private static GenderCall breakingTies(
            final Gender majority,
            final boolean enough,
            final PronounGender.Evidence tally,
            final int male,
            final int female) {
        final Gender clear = TermReviewReplies.favouredBy(tally);
        if (clear != null) {
            // The tally reads the first pronoun after each mention, which often belongs to a neighbour; it stands
            // verified only where the readings' verified votes point the same way and none points the other way. A
            // dominant tally with no vote stays a suggestion: a demon written "it" had a neighbour's "he" four times.
            final int against = clear == Gender.MALE ? female : male;
            final boolean agreed = majority == clear && against == 0;
            log.debug(
                    "Reading votes: the tally is clear for {}, votes for {}: {}",
                    clear,
                    majority,
                    agreed ? "verified" : "suggested");
            return GenderCall.of(clear, agreed ? GenderOrigin.VERIFIED : GenderOrigin.SUGGESTED);
        }
        if (majority != Gender.UNKNOWN && enough) {
            log.debug("Reading votes: no clear tally, {} by the votes, suggested", majority);
            return GenderCall.of(majority, GenderOrigin.SUGGESTED);
        }
        final Gender oneSided = oneSided(male + tally.male(), female + tally.female());
        log.debug("Reading votes: no clear tally and too few votes; votes and pronouns together point to {}", oneSided);
        if (oneSided != Gender.UNKNOWN) {
            return GenderCall.of(oneSided, GenderOrigin.SUGGESTED);
        }
        final boolean nothing = male + female + tally.total() == 0;
        log.debug("Reading votes: no gender decided, the book gave no evidence at all: {}", nothing);
        return nothing ? GenderCall.NO_EVIDENCE : GenderCall.NONE;
    }

    // Two or more pieces of evidence, all one gender's: a hint the person may confirm, never more.
    private static Gender oneSided(final int male, final int female) {
        if (male >= MIN_ONE_SIDED && female == 0) {
            return Gender.MALE;
        }
        return female >= MIN_ONE_SIDED && male == 0 ? Gender.FEMALE : Gender.UNKNOWN;
    }
}
