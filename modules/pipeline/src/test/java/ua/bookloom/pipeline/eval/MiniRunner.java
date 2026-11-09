package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.persistence.GlossaryRepository;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Deferral;
import ua.bookloom.api.project.DeferralReason;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.eval.MiniBook.Block;
import ua.bookloom.pipeline.eval.MiniRun.Calls;
import ua.bookloom.pipeline.eval.MiniRun.Outcome;
import ua.bookloom.pipeline.glossary.NameKnowledgeCalls;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.StyleSheet;
import ua.bookloom.pipeline.revision.DeferralRegister;
import ua.bookloom.pipeline.revision.PassOptions;

/**
 * Runs the mini book the way the app does a whole book: the autonomous names pass, the translation job, then the
 * export's consistency pass, all through the production classes over one imported project and one model. The brief
 * takes the pair and the narrator from the dataset's book.
 */
@Slf4j
final class MiniRunner {

    private static final Pattern TOKEN = Pattern.compile("⟦[^⟧]*⟧");

    private final MiniBook book;
    private final QualityDial dial;
    private final NamePolicy policy;
    private final @Nullable Integer window;
    private final List<String> added;

    /**
     * Creates the runner.
     *
     * @param book the book to translate
     * @param dial the quality dial of the brief
     * @param policy the brief's name policy
     * @param window the context window the provider is taken to report, or null for the run's default
     */
    MiniRunner(final MiniBook book, final QualityDial dial, final NamePolicy policy, @Nullable final Integer window) {
        this(book, dial, policy, window, List.of());
    }

    /**
     * Creates a runner that adds some characters after the names pass, as the person adds them with "+ Add" (no target,
     * no gender), and asks the name-knowledge stage about them, as "Review with model" does (arm 4b, task 7.8, adds the
     * characters the scan leaves out).
     *
     * @param added the terms added as characters; one already in the glossary is left as it is
     */
    MiniRunner(
            final MiniBook book,
            final QualityDial dial,
            final NamePolicy policy,
            @Nullable final Integer window,
            final List<String> added) {
        this.book = Objects.requireNonNull(book, "book");
        this.dial = Objects.requireNonNull(dial, "dial");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.window = window;
        this.added = List.copyOf(added);
    }

    /**
     * Runs the book.
     *
     * @param model the model every phase calls
     * @param workDir an existing directory the EPUB is written into
     * @return what the run decided
     */
    MiniRun run(final ChatModel model, final Path workDir) {
        log.info("Mini eval start dial={} names={} window={}", dial, policy, window);
        final Path file = book.writeEpub(workDir);
        final Prepared prepared = SequenceJobs.importBook(file, brief());
        try (SequenceLogCapture capture = new SequenceLogCapture()) {
            return run(prepared, model, capture);
        }
    }

    // The three phases, with the log read for what the chapter-summary gate decided.
    private MiniRun run(final Prepared prepared, final ChatModel model, final SequenceLogCapture capture) {
        final StageProbe probe = new StageProbe(model);
        final List<String> errors = new ArrayList<>();
        final Instant started = Instant.now();
        final int names = WholeBookStages.namesPass(prepared, probe, window, errors) + addCharacters(prepared, probe);
        final int afterNames = probe.mark();
        final JobState end = WholeBookStages.translate(prepared, probe, window, errors);
        final int afterJob = probe.mark();
        final List<MiniRun.Proofread> proofread = proofread(prepared);
        logOpenDeferrals(prepared);
        consistency(prepared, probe, errors);
        final Calls calls = new Calls(names, afterJob - afterNames, probe.mark() - afterJob);
        final Duration elapsed = Duration.between(started, Instant.now());
        log.info("Mini eval end state={} calls={} errors={} elapsed={}", end, calls, errors, elapsed);
        return new MiniRun(
                policy,
                end,
                outcomes(prepared),
                glossary(prepared),
                errors,
                calls,
                elapsed,
                probe.callsSince(0),
                capture.summaryGate(),
                proofread);
    }

    /**
     * The brief the run imports the book with: the dataset's pair and narrator, this runner's dial and policy, and the
     * chapter-summaries switch the environment forces ({@link EvalChapterSummaries}).
     */
    BookBrief brief() {
        final Narrator narrator =
                new Narrator(book.narrator().person(), book.narrator().gender());
        return EvalChapterSummaries.apply(
                SequenceJobs.brief(book.sourceLanguage(), book.targetLanguage(), dial, narrator, policy),
                System.getenv());
    }

    // After the names pass, as the person adds a character the scan left out and then asks "Review with model": the
    // name-knowledge stage gives each added one its likely gender. Returns the calls it made.
    private int addCharacters(final Prepared prepared, final StageProbe probe) {
        if (added.isEmpty()) {
            return 0;
        }
        final int mark = probe.mark();
        final GlossaryRepository glossary = prepared.stores().glossary();
        added.forEach(term -> addCharacter(glossary, prepared.projectId(), term));
        final List<GlossaryEntry> held =
                Objects.requireNonNull(glossary.all(prepared.projectId()).data(), "glossary");
        final CallFrame frame = new CallFrame(
                book.sourceLanguage(),
                book.targetLanguage(),
                StyleSheet.from(BookBrief.defaults(book.sourceLanguage())),
                ForeignPassagePolicy.KEEP);
        final Result<List<GlossaryEntry>> answered = new NameKnowledgeCalls(
                        EvalVariants.templates(), new ObjectMapper())
                .applied(held, frame, probe.calls(book.targetLanguage()));
        Objects.requireNonNull(answered.data(), () -> "name knowledge: " + answered.error())
                .forEach(entry -> Objects.requireNonNull(glossary.update(entry).data(), "update"));
        return probe.mark() - mark;
    }

    // A term already in the glossary is left as it is.
    private static void addCharacter(final GlossaryRepository glossary, final String projectId, final String term) {
        final boolean held = Objects.requireNonNull(
                        glossary.findByTerm(projectId, term).data(), "find")
                .isPresent();
        log.info("Mini eval adds character term={} alreadyHeld={}", term, held);
        if (!held) {
            Objects.requireNonNull(
                    glossary.add(GlossaryEntry.of(
                                    projectId + ":" + term,
                                    projectId,
                                    term,
                                    null,
                                    TermType.CHARACTER,
                                    Gender.UNKNOWN,
                                    false))
                            .data(),
                    "added");
        }
    }

    private void consistency(final Prepared prepared, final StageProbe probe, final List<String> errors) {
        final Result<?> result = SequenceJobs.consistencyPass(prepared)
                .run(
                        prepared.projectId(),
                        probe.calls(book.targetLanguage()),
                        new PassOptions(true, dial == QualityDial.MAX));
        WholeBookStages.note("consistency pass", result, errors);
    }

    private Map<String, Outcome> outcomes(final Prepared prepared) {
        final Map<String, Outcome> outcomes = new LinkedHashMap<>();
        stored(prepared)
                .forEach((block, record) -> outcomes.put(block.id(), Outcome.of(block.id(), block.text(), record)));
        return outcomes;
    }

    // Each segment the job's Proofread stage read, as the job left it: the consistency pass that follows may rewrite
    // a text, so the verdicts are read before it runs.
    private List<MiniRun.Proofread> proofread(final Prepared prepared) {
        final List<MiniRun.Proofread> read = new ArrayList<>();
        stored(prepared).forEach((block, record) -> {
            if (record.proofread() != null) {
                read.add(MiniRun.Proofread.of(block.id(), block.text(), record));
            }
        });
        log.info(
                "Mini eval proofread verdicts read={} kept={}",
                read.size(),
                read.stream().filter(MiniRun.Proofread::isKept).count());
        return read;
    }

    // The deferrals the job left open, and how many of them wait on a gender that has arrived (a confirmed glossary
    // gender, or the narrator's checkable one, as the Proofread stage reads them): the gate of design D25 row 14 wants
    // none of those on mini-ru (arm 7b).
    private static void logOpenDeferrals(final Prepared prepared) {
        final List<Deferral> open = Objects.requireNonNull(
                prepared.deferrals().open(prepared.projectId()).data(), "open deferrals");
        final Narrator narrator = Objects.requireNonNull(
                        prepared.stores().projects().find(prepared.projectId()).data(), "project")
                .orElseThrow()
                .brief()
                .narrator();
        final Map<String, GlossaryEntry> byTerm = new LinkedHashMap<>();
        Objects.requireNonNull(
                        prepared.stores().glossary().all(prepared.projectId()).data(), "glossary")
                .forEach(entry -> byTerm.putIfAbsent(entry.term(), entry));
        final List<Deferral> arrived = open.stream()
                .filter(deferral -> hasArrived(deferral, byTerm, narrator))
                .toList();
        log.info("Mini eval open deferrals total={} arrivedFact={}", open.size(), arrived.size());
        open.forEach(deferral -> log.debug(
                "Mini eval open deferral segmentId={} reason={} arrived={}",
                deferral.segmentId(),
                deferral.reason(),
                arrived.contains(deferral)));
    }

    private static boolean hasArrived(
            final Deferral deferral, final Map<String, GlossaryEntry> byTerm, final Narrator narrator) {
        final String waitingOn = deferral.waitingOn();
        final boolean gender = deferral.reason() == DeferralReason.GENDER_UNKNOWN
                || deferral.reason() == DeferralReason.GENDER_CHANGED;
        if (!gender || waitingOn == null) {
            return false;
        }
        if (DeferralRegister.NARRATOR.equals(waitingOn)) {
            return narrator.hasCheckableGender();
        }
        final GlossaryEntry entry = byTerm.get(waitingOn);
        return entry != null && entry.hasConfirmedGender();
    }

    // Each block of the book with the stored record of its segment, in reading order; a block with no segment or no
    // record is left out.
    private Map<Block, SegmentRecord> stored(final Prepared prepared) {
        final Map<String, SegmentRecord> stored = new LinkedHashMap<>();
        Objects.requireNonNull(
                        prepared.stores().segments().all(prepared.projectId()).data())
                .forEach(record -> stored.put(record.segmentId(), record));
        final Map<String, Block> byText = new LinkedHashMap<>();
        book.blocks().forEach(block -> byText.put(block.text(), block));
        final Map<Block, SegmentRecord> pairs = new LinkedHashMap<>();
        for (final Unit unit : prepared.document().units()) {
            for (final Segment segment : unit.segments()) {
                final Block block = byText.get(
                        TOKEN.matcher(segment.masked()).replaceAll("").strip());
                final SegmentRecord record = stored.get(segment.id());
                if (block != null && record != null) {
                    pairs.put(block, record);
                }
            }
        }
        return pairs;
    }

    private static List<GlossaryEntry> glossary(final Prepared prepared) {
        return Objects.requireNonNull(
                prepared.stores().glossary().all(prepared.projectId()).data());
    }
}
