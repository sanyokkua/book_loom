package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.glossary.ReadingCounts.Read;
import ua.bookloom.pipeline.glossary.ReadingVotes.GenderCall;
import ua.bookloom.pipeline.glossary.ReadingVotes.VoteMode;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReading.Usage;
import ua.bookloom.pipeline.glossary.TermReading.WindowReading;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The code decides a glossary term from its readings (15i.N5): kind and type by the majority of the window votes, the
 * gender of its cluster from the verified votes against the book's pronoun tally, a removal only on quoted ordinary-word
 * uses of a word the book also writes in lower case; a tiebreak call where the readings oppose a dominant tally, and at
 * Max a summary call where a term's calls disagree. The model only reads; every rule that turns its readings into a
 * change is here, in code. How far the gender votes count is the model's {@link ReadingVotes.VoteMode}: until a model
 * passes the coreference gate its votes only break ties.
 */
@Slf4j
final class ReadingVerdict {

    /** A name needs this many windows that read it as one, except one window of one at Fast. */
    static final int MIN_NAME_USES = 2;

    /** A removal needs this many ordinary-word uses whose quote stands in its own window. */
    static final int MIN_WORD_USES = 2;

    /** At Max a term read in this many calls, or mentioned {@link #SUMMARY_MIN_MENTIONS} times, may get a summary. */
    static final int SUMMARY_MIN_CALLS = 2;

    static final int SUMMARY_MIN_MENTIONS = 30;

    private final VerdictCalls asked;
    private final VoteMode mode;

    /**
     * Builds the verdict service.
     *
     * @param templates the prompt templates of the tiebreak and summary calls; never null
     * @param mapper the mapper their replies are read with; never null
     * @param mode what the readings' votes may do for the model they came from ({@link ReadingVotes#modeFor}); never
     *     null
     */
    ReadingVerdict(final PromptTemplates templates, final ObjectMapper mapper, final VoteMode mode) {
        this.asked = new VerdictCalls(
                Objects.requireNonNull(templates, "templates"), Objects.requireNonNull(mapper, "mapper"));
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /**
     * One term-reading call: the windows it showed and what the reading kept of the reply.
     *
     * @param windows the windows shown, window {@code n} as number {@code n + 1}
     * @param reading what the reading kept of the reply; every window number names a window shown
     */
    record ReadCall(List<Window> windows, Reading reading) {

        /** Copies the windows and rejects an answer about a window the call did not show. */
        ReadCall {
            windows = List.copyOf(windows);
            Objects.requireNonNull(reading, "reading");
            final int shown = windows.size();
            if (reading.windows().stream().anyMatch(answer -> answer.number() < 1 || answer.number() > shown)) {
                throw new IllegalArgumentException("A reading answers a window the call did not show");
            }
        }
    }

    /**
     * What the code decides one term from.
     *
     * @param entry the entry as held
     * @param calls the entry's own reading calls, in order
     * @param memberCalls the reading calls of the other members of its cluster (15i.N2); empty when it stands alone
     * @param evidence the review's windows of the entry, each with the pronoun the code gives it there, and the
     *     pronoun tally the code read after the entry, pooled over its cluster
     * @param lowerShare the share of the term's uses the book's running text writes in lower case (the scan text:
     *     {@code WordCounts} over {@code BookIndex.scanReads}), from 0 to 1
     * @param dial the quality dial of the project
     * @param analysisLanguage the language the code reads the book in; null under Auto
     */
    record Facts(
            GlossaryEntry entry,
            List<ReadCall> calls,
            List<ReadCall> memberCalls,
            NameWindows.Evidence evidence,
            double lowerShare,
            QualityDial dial,
            @Nullable String analysisLanguage) {

        /** Copies the calls. */
        Facts {
            Objects.requireNonNull(entry, "entry");
            calls = List.copyOf(calls);
            memberCalls = List.copyOf(memberCalls);
            Objects.requireNonNull(evidence, "evidence");
            Objects.requireNonNull(dial, "dial");
        }

        /** The entry's own calls and its cluster members', whose votes count for one person. */
        List<ReadCall> clusterCalls() {
            return Stream.concat(calls.stream(), memberCalls.stream()).toList();
        }
    }

    /**
     * What the code decided of one term.
     *
     * @param kind a name, a term, not a name, or no opinion
     * @param type the type the readings show; {@link TermType#OTHER} when they show none
     * @param gender the gender decided; {@link Gender#UNKNOWN} when none, or while a tiebreak is still to be asked
     * @param origin {@link GenderOrigin#VERIFIED} or {@link GenderOrigin#SUGGESTED}; null exactly when the gender is
     *     unknown
     * @param needsTiebreak whether the readings' majority opposes a dominant code tally, so the gender waits for one
     *     tiebreak call
     * @param quote the ordinary-word use a removal stands on; empty for any other decision
     */
    record Decision(
            Kind kind,
            TermType type,
            Gender gender,
            @Nullable GenderOrigin origin,
            boolean needsTiebreak,
            String quote) {

        /** The decision with the gender the tiebreak settled: verified, or unknown when nothing bore it out. */
        Decision settled(final Gender settled) {
            return new Decision(
                    kind, type, settled, settled == Gender.UNKNOWN ? null : GenderOrigin.VERIFIED, false, quote);
        }
    }

    /**
     * Decides a term from its readings alone, with no call.
     *
     * @param facts the readings and what the code knows; never null
     * @param mode what the readings' votes may do; never null
     * @return the decision; never null
     */
    static Decision decide(final Facts facts, final VoteMode mode) {
        return decide(facts, null, mode);
    }

    /**
     * Decides a term from its readings, the summary's reading replacing the windows' majority where there is one.
     *
     * @param facts the readings and what the code knows; never null
     * @param summary the reading a summary call settled on; null when none was asked or it was not borne out
     * @param mode what the readings' votes may do; never null
     * @return the decision; never null
     */
    static Decision decide(final Facts facts, @Nullable final Read summary, final VoteMode mode) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(mode, "mode");
        final List<WindowReading> own = ReadingCounts.readings(facts.calls());
        final Read read = summary != null ? summary : ReadingCounts.majority(own);
        final long names = ReadingCounts.uses(own, Usage.NAME);
        final List<String> wordQuotes = ReadingCounts.standingWordQuotes(facts.calls());
        log.debug(
                "Reading verdict for entry {}: {} windows answered, majority {}, summary {}, {} name uses, {} standing"
                        + " word uses, lower-case share {}",
                facts.entry().id(),
                own.size(),
                read,
                summary != null,
                names,
                wordQuotes.size(),
                facts.lowerShare());
        if (isNotAName(facts, names, wordQuotes.size())) {
            final Decision removed =
                    new Decision(Kind.NOT_A_NAME, TermType.OTHER, Gender.UNKNOWN, null, false, wordQuotes.getFirst());
            log.debug("Reading verdict for entry {}: not a name", facts.entry().id());
            return removed;
        }
        return typed(facts, read, names, mode);
    }

    // A removal stands on two quoted ordinary-word uses, no name use, a word the book also writes in lower case and an
    // entry nobody typed or gave a gender (the review's removal rule, ReviewCommit).
    private static boolean isNotAName(final Facts facts, final long names, final int wordUses) {
        final GlossaryEntry entry = facts.entry();
        final boolean untouched = entry.type() == TermType.OTHER && entry.gender() == Gender.UNKNOWN;
        final boolean removes = untouched
                && names == 0
                && wordUses >= MIN_WORD_USES
                && facts.lowerShare() >= FrequencyScan.LOWER_SHARE_LIMIT;
        log.debug(
                "Reading verdict removal check for entry {}: untouched={} names={} wordUses={} share={} removes={}",
                entry.id(),
                untouched,
                names,
                wordUses,
                facts.lowerShare(),
                removes);
        return removes;
    }

    private static Decision typed(final Facts facts, @Nullable final Read read, final long names, final VoteMode mode) {
        final boolean enough = names >= MIN_NAME_USES
                || (facts.dial() == QualityDial.FAST && names == 1 && ReadingCounts.shown(facts.calls()) == 1);
        final Usage usage = read == null ? Usage.UNSURE : read.usage();
        final long titles = ReadingCounts.uses(ReadingCounts.readings(facts.calls()), Usage.TITLE);
        final Kind kind;
        final TermType type;
        if (usage == Usage.NAME && enough) {
            kind = Kind.NAME;
            type = typeOf(Objects.requireNonNull(read, "read").kind());
        } else if (usage == Usage.TITLE && titles >= MIN_NAME_USES) {
            kind = Kind.TERM;
            type = TermType.TITLE;
        } else {
            kind = Kind.UNSURE;
            type = TermType.OTHER;
        }
        final GenderCall gender = genderOf(facts, type, mode);
        return logged(
                facts, new Decision(kind, type, gender.gender(), gender.origin(), gender.needsTiebreak(), ""), enough);
    }

    private static Decision logged(final Facts facts, final Decision decision, final boolean enough) {
        log.debug(
                "Reading verdict for entry {}: {} {} gender={} origin={} tiebreak={} (name uses enough: {})",
                facts.entry().id(),
                decision.kind(),
                decision.type(),
                decision.gender(),
                decision.origin(),
                decision.needsTiebreak(),
                enough);
        return decision;
    }

    // Places, organisations, things and titles carry no gender, whatever pronoun follows them; the person's own gender
    // is never decided again. One person, one gender: the votes of every spelling count together.
    private static GenderCall genderOf(final Facts facts, final TermType decided, final VoteMode mode) {
        final GlossaryEntry entry = facts.entry();
        final boolean person =
                decided == TermType.CHARACTER || (decided == TermType.OTHER && entry.type() == TermType.CHARACTER);
        if (!person || entry.genderOrigin() == GenderOrigin.PERSON) {
            log.debug("Reading verdict for entry {}: no gender decided (person {})", entry.id(), person);
            return GenderCall.NONE;
        }
        return ReadingVotes.genderOf(facts, mode);
    }

    private static TermType typeOf(final TermReading.Kind kind) {
        return switch (kind) {
            case PERSON -> TermType.CHARACTER;
            case PLACE -> TermType.PLACE;
            case ORGANISATION, THING -> TermType.TERM;
            case OTHER -> TermType.OTHER;
        };
    }

    /**
     * Decides a term from its readings, asking a summary call at Max where its calls disagree and a tiebreak call where
     * the readings oppose a dominant code tally.
     *
     * @param facts the readings and what the code knows; never null
     * @param frame the run's language pair and analysis language; never null
     * @param calls the seam every model call goes through; never null
     * @return the decision; or the failed call's error
     */
    Result<Decision> verdict(final Facts facts, final CallFrame frame, final ModelCalls calls) {
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(calls, "calls");
        final List<Read> reads = ReadingCounts.callReads(facts.calls());
        final boolean summarise = needsSummary(facts, reads);
        log.debug(
                "Reading verdict for entry {}: {} calls, {} member calls, dial {}, votes {}, summary {}",
                facts.entry().id(),
                facts.calls().size(),
                facts.memberCalls().size(),
                facts.dial(),
                mode,
                summarise);
        final Result<Optional<Read>> summary =
                summarise ? asked.summary(facts, reads, frame, calls) : Result.ok(Optional.empty());
        if (summary.isErr()) {
            return Result.err(Objects.requireNonNull(summary.error(), "error"));
        }
        final Decision decided =
                decide(facts, Objects.requireNonNull(summary.data(), "summary").orElse(null), mode);
        if (!decided.needsTiebreak()) {
            return Result.ok(decided);
        }
        return tiebreak(facts, decided, frame, calls);
    }

    private Result<Decision> tiebreak(
            final Facts facts, final Decision decided, final CallFrame frame, final ModelCalls calls) {
        final List<ReadCall> cluster = facts.clusterCalls();
        final Gender readers = ReadingCounts.votes(cluster, Gender.MALE) > ReadingCounts.votes(cluster, Gender.FEMALE)
                ? Gender.MALE
                : Gender.FEMALE;
        final Gender code = facts.evidence().pronouns().dominant().orElseThrow();
        final Result<Gender> settled = asked.tiebreak(facts, readers, code, frame, calls);
        if (settled.isErr()) {
            final AppError error = Objects.requireNonNull(settled.error(), "error");
            log.debug(
                    "Reading verdict for entry {}: the tiebreak failed code={}",
                    facts.entry().id(),
                    error.code());
            return Result.err(error);
        }
        final Gender gender = Objects.requireNonNull(settled.data(), "settled");
        log.debug(
                "Reading verdict for entry {}: the tiebreak settled {}",
                facts.entry().id(),
                gender);
        return Result.ok(decided.settled(gender));
    }

    // Only at Max, only for a term read in two or more calls or mentioned often, and only when its calls disagree.
    private static boolean needsSummary(final Facts facts, final List<Read> reads) {
        final boolean large =
                facts.calls().size() >= SUMMARY_MIN_CALLS || facts.evidence().count() >= SUMMARY_MIN_MENTIONS;
        final boolean disagree = reads.stream().distinct().count() >= SUMMARY_MIN_CALLS;
        log.debug(
                "Reading verdict summary check for entry {}: dial {} large={} disagree={} ({})",
                facts.entry().id(),
                facts.dial(),
                large,
                disagree,
                reads);
        return facts.dial() == QualityDial.MAX && large && disagree;
    }
}
