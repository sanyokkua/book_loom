package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Map;
import lombok.Builder;

/**
 * What the sequence eval measures over one run, every figure computed by a production check or a count of the run's
 * own records.
 *
 * @param suite always {@code sequence}; the matrix table tells the report kinds apart by it
 * @param model the model measured
 * @param narrator what the brief said about the narrator, {@code set} or {@code unset}
 * @param dial the quality dial
 * @param segments the segments the run decided
 * @param flagged how many ended flagged
 * @param flaggedWithoutTarget flagged segments with no stored target, which an export writes as the source
 * @param hardGateFailuresRound0 segments whose first evaluation failed a hard gate, so they needed a repair round
 * @param hardGateKinds the findings of those segments' first repair round, counted per kind
 * @param leakedProtocol targets holding batch protocol: a terms field, a code fence or a JSON object with an id
 * @param reviewerTruncated reviewer replies cut off at the output cap ({@code finish=LENGTH})
 * @param termClaimedWrong lexicon terms whose learned rendering another term also claimed
 * @param claimedRenderings those shared renderings, {@code rendering <- term, term}
 * @param dominantShareMean the mean over the terms of the share of their occurrences carrying the commonest rendering
 * @param renderingsPerTerm the mean number of distinct renderings over the fixture's terms that were rendered at all
 * @param nameVariants the spellings beyond the first, summed over the names
 * @param genderSlips narrator-gender findings in the first-person chapters
 * @param englishLeftovers targets still in the source language: the language check's finding or a plain echo
 * @param quoteFailures targets whose quotes the source balanced and the target left blockingly unbalanced
 * @param asciiQuotes targets that still hold a straight {@code "}, which the target language never uses (an eval-side
 *     count: the quote check balances pairs and does not read the kind)
 * @param mixedScript targets holding a word of two alphabets
 * @param learned terms whose rendering the lexicon learned from the text
 * @param learnedCoverage the share of those terms' occurrences that carry the learned rendering
 * @param lexiconDistinct the lexicon's own distinct renderings per term
 * @param calls model calls that finished
 * @param callsPerSegment {@code calls / segments}
 * @param secondsPerSegment wall seconds of the run per segment
 * @param elapsedSeconds wall seconds of the run
 * @param batchedItems segments drafted inside a multi-item batch call
 * @param batchFallbacks batch items that fell back to their own draft
 * @param batchFallbackRate {@code batchFallbacks / batchedItems}
 * @param fallbackReasons the fallbacks per {@code status/problems}
 * @param editsApplied reviewer edits applied
 * @param editsRefused reviewer edits the verifier refused
 * @param callsByKind finished calls per kind
 * @param terms one entry per fixture term
 * @param names one entry per fixture name
 * @param learnedRenderings the learned renderings, {@code term -> rendering}
 */
@Builder
record SequenceMetrics(
        String suite,
        String model,
        String narrator,
        String dial,
        int segments,
        int flagged,
        int flaggedWithoutTarget,
        int hardGateFailuresRound0,
        Map<String, Integer> hardGateKinds,
        int leakedProtocol,
        int reviewerTruncated,
        int termClaimedWrong,
        List<String> claimedRenderings,
        double dominantShareMean,
        double renderingsPerTerm,
        int nameVariants,
        int genderSlips,
        int englishLeftovers,
        int quoteFailures,
        int asciiQuotes,
        int mixedScript,
        int learned,
        double learnedCoverage,
        double lexiconDistinct,
        int calls,
        double callsPerSegment,
        double secondsPerSegment,
        double elapsedSeconds,
        int batchedItems,
        int batchFallbacks,
        double batchFallbackRate,
        Map<String, Integer> fallbackReasons,
        int editsApplied,
        int editsRefused,
        Map<String, Integer> callsByKind,
        List<TermStat> terms,
        List<NameStat> names,
        List<String> learnedRenderings) {

    static final String SUITE = "sequence";

    /**
     * One recurring term.
     *
     * @param term the term
     * @param segments source segments naming it
     * @param renderings per rendering label the segments whose target carries it
     * @param distinct how many renderings were seen
     * @param dominantShare the commonest rendering's share of the renderings seen, 0 when none was seen
     */
    record TermStat(String term, int segments, Map<String, Integer> renderings, int distinct, double dominantShare) {}

    /**
     * One name.
     *
     * @param name the name
     * @param segments source segments naming it
     * @param spellings per spelling the targets that carry it
     * @param variants how many spellings were seen
     */
    record NameStat(String name, int segments, Map<String, Integer> spellings, int variants) {}

    /** The flagged share of the segments. */
    double flaggedRate() {
        return segments == 0 ? 0 : (double) flagged / segments;
    }
}
