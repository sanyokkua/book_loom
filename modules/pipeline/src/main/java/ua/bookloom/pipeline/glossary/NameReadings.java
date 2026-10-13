package ua.bookloom.pipeline.glossary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.GenderOrigin;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.pipeline.glossary.NameWindows.Evidence;
import ua.bookloom.pipeline.glossary.NamesPlan.Candidate;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Decision;
import ua.bookloom.pipeline.glossary.ReadingVerdict.Facts;
import ua.bookloom.pipeline.glossary.ReadingVerdict.ReadCall;
import ua.bookloom.pipeline.glossary.TermReading.Reading;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Kind;
import ua.bookloom.pipeline.glossary.TermReviewReplies.Verdict;
import ua.bookloom.pipeline.index.Occurrences;
import ua.bookloom.pipeline.index.Window;
import ua.bookloom.pipeline.index.WordCounts;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * The review's reading path (15i.N5): the entries the dial's {@link NamesPlan} reads are read in context, one term per
 * call ({@link TermReading}), and decided by the code from the readings ({@link ReadingVerdict}); the verdicts take the
 * shape the batched review gives, so both paths commit through {@link ReviewCommit}. Every reading is asked before any
 * decision, so a cluster's members pool their votes, and nothing is written here.
 */
@Slf4j
final class NameReadings {

    private final TermReading reading;
    private final ReadingVerdict verdicts;

    NameReadings(final PromptTemplates templates, final ObjectMapper mapper) {
        this.reading = new TermReading(templates, mapper);
        // The review does not know which model answers, and no model has passed the coreference gate yet.
        this.verdicts = new ReadingVerdict(templates, mapper, ReadingVotes.modeFor(null));
    }

    /**
     * What a review reads with.
     *
     * @param open the entries the model may still change
     * @param held the whole glossary, whose clusters pool a person's spellings
     * @param evidence the review's windows and the code's pronoun tally of each open entry, by term
     * @param story the book's body segments
     * @param frame the run's language pair and analysis language
     * @param dial the project's quality dial, which picks the entries read
     * @param calls the seam every model call goes through
     */
    record Inputs(
            List<GlossaryEntry> open,
            List<GlossaryEntry> held,
            Map<String, Evidence> evidence,
            BookSlice story,
            CallFrame frame,
            QualityDial dial,
            ModelCalls calls) {}

    /**
     * What the reading path leaves.
     *
     * @param verdicts the code's verdicts on the entries read
     * @param unread the open entries the plan does not read, for the batched review, in glossary order
     * @param readings what the calls read of each entry read, by entry id: a speaking name or a brand in any of its
     *     calls is one
     */
    record Outcome(List<Verdict> verdicts, List<GlossaryEntry> unread, Map<String, Reading> readings) {

        /** The ids of the entries the readings decided are proper names: the only verified names (15i.T4). */
        Set<String> names() {
            return verdicts.stream()
                    .filter(verdict -> verdict.kind() == Kind.NAME)
                    .map(verdict -> verdict.entry().id())
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Reads and decides the entries the plan reads.
     *
     * @param in what the review reads with; never null
     * @return the verdicts and the entries left for the batch; or the first failed call's error
     */
    Result<Outcome> read(final Inputs in) {
        Objects.requireNonNull(in, "in");
        final NamesPlan plan = NamesPlan.of(in.dial(), NamesPlan.candidates(in.open(), in.story()));
        log.info(
                "Names reading started dial={} read={} of {} open entries, about {} calls",
                in.dial(),
                plan.read().size(),
                in.open().size(),
                plan.estimatedCalls());
        final NameClusters clusters =
                NameClusters.of(in.held(), in.story().index(), in.frame().analysisLanguage());
        final Map<String, List<ReadCall>> byId = new LinkedHashMap<>();
        for (final Candidate candidate : plan.read()) {
            final Result<List<ReadCall>> read = readOne(candidate, plan, clusters, in);
            if (read.isErr()) {
                log.warn(
                        "Names reading stopped at entry {}: a call failed",
                        candidate.entry().id());
                return Result.err(Objects.requireNonNull(read.error(), "error"));
            }
            byId.put(candidate.entry().id(), Objects.requireNonNull(read.data(), "read"));
        }
        return decideAll(plan, byId, clusters, in);
    }

    private Result<List<ReadCall>> readOne(
            final Candidate candidate, final NamesPlan plan, final NameClusters clusters, final Inputs in) {
        final GlossaryEntry entry = candidate.entry();
        final List<String> others = clusters.namesOf(entry).stream()
                .filter(name -> !entry.names().contains(name))
                .toList();
        final TermReading.Subject subject = new TermReading.Subject(
                entry.term(), candidate.mentions(), evidenceOf(entry, in).pronouns(), others);
        final List<ReadCall> read = new ArrayList<>();
        for (final List<Window> group : plan.calls(plan.windowsOf(entry, in.story()))) {
            final Result<Reading> answered = reading.read(subject, group, in.frame(), in.calls());
            if (answered.isErr()) {
                return Result.err(Objects.requireNonNull(answered.error(), "error"));
            }
            read.add(new ReadCall(group, Objects.requireNonNull(answered.data(), "reading")));
        }
        log.debug("Names reading of entry {}: {} calls", entry.id(), read.size());
        return Result.ok(read);
    }

    private Result<Outcome> decideAll(
            final NamesPlan plan,
            final Map<String, List<ReadCall>> byId,
            final NameClusters clusters,
            final Inputs in) {
        final String language = in.frame().analysisLanguage();
        final WordCounts counts =
                WordCounts.of(in.story().index().scanReads(in.story().ordinals(), language));
        final List<Verdict> decided = new ArrayList<>();
        for (final Candidate candidate : plan.read()) {
            final GlossaryEntry entry = candidate.entry();
            final Facts facts = new Facts(
                    entry,
                    byId.getOrDefault(entry.id(), List.of()),
                    memberCalls(entry, clusters, byId),
                    evidenceOf(entry, in),
                    lowerShare(entry, counts),
                    in.dial(),
                    language);
            final Result<Decision> verdict = verdicts.verdict(facts, in.frame(), in.calls());
            if (verdict.isErr()) {
                return Result.err(Objects.requireNonNull(verdict.error(), "error"));
            }
            decided.add(verdictOf(entry, Objects.requireNonNull(verdict.data(), "decision")));
        }
        final Set<String> read = new HashSet<>(byId.keySet());
        final List<GlossaryEntry> unread =
                in.open().stream().filter(entry -> !read.contains(entry.id())).toList();
        log.info("Names reading finished decided={} left for the batch={}", decided.size(), unread.size());
        return Result.ok(new Outcome(decided, unread, readingsOf(plan, byId)));
    }

    private static Map<String, Reading> readingsOf(final NamesPlan plan, final Map<String, List<ReadCall>> byId) {
        final Map<String, Reading> readings = new LinkedHashMap<>();
        for (final Candidate candidate : plan.read()) {
            final GlossaryEntry entry = candidate.entry();
            final List<Reading> calls = byId.getOrDefault(entry.id(), List.of()).stream()
                    .map(ReadCall::reading)
                    .toList();
            readings.put(
                    entry.id(),
                    new Reading(
                            entry.term(),
                            List.of(),
                            calls.stream().anyMatch(Reading::speaking),
                            calls.stream().anyMatch(Reading::brand)));
        }
        return readings;
    }

    private static List<ReadCall> memberCalls(
            final GlossaryEntry entry, final NameClusters clusters, final Map<String, List<ReadCall>> byId) {
        return clusters.membersOf(entry).stream()
                .filter(member -> !member.id().equals(entry.id()))
                .flatMap(member -> byId.getOrDefault(member.id(), List.of()).stream())
                .toList();
    }

    private static Evidence evidenceOf(final GlossaryEntry entry, final Inputs in) {
        return in.evidence().getOrDefault(entry.term(), Evidence.NONE);
    }

    // A run of words is never written in lower case as one word, so only a single word has a share.
    private static double lowerShare(final GlossaryEntry entry, final WordCounts counts) {
        return entry.term().indexOf(' ') >= 0 ? 0.0 : counts.lowerShare(Occurrences.keyOf(entry.term()));
    }

    private static Verdict verdictOf(final GlossaryEntry entry, final Decision decision) {
        return new Verdict(
                entry,
                decision.kind(),
                decision.type(),
                decision.gender(),
                decision.quote(),
                decision.origin() == GenderOrigin.VERIFIED);
    }
}
