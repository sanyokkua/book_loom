package ua.bookloom.pipeline.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelProfile;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.chunk.TokenEstimator;
import ua.bookloom.pipeline.context.BookMap;
import ua.bookloom.pipeline.context.ContextBuilder;
import ua.bookloom.pipeline.context.LexiconFilter;
import ua.bookloom.pipeline.context.WindowBudgets;
import ua.bookloom.pipeline.context.WindowMap;
import ua.bookloom.pipeline.project.ChapterBoundaries.Chapter;
import ua.bookloom.pipeline.prompt.CallDescriptor;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * The call that writes a finished chapter's summary (design D10): the chapter's source with the names it holds, read in
 * parts when it is longer than one part of the model's tier, each part given the summary of the part before; the last
 * part's checked sentences are the chapter's. A failed, unreadable or emptied part leaves the chapter without a
 * summary and one warning, because a missing summary only means later calls get one fewer. A part's call that a pause
 * or a stop aborted is no failure: the caller routes it like a draft's, and the chapter is asked again from part 1.
 *
 * <p>Used from the job thread only.
 */
@Slf4j
public final class ChapterSummaryCall {

    /** How the one re-ask's correction starts; the faulty words follow it. */
    static final String CORRECTION_START = "Your last answer held words that are not in the target language: ";

    private static final String ERROR = "error";
    private static final String REPLY = "reply";
    private static final int ASKED_CHARS = 160;
    private static final String CHAPTER_SOURCE = "chapterSource";
    private static final String MAP_LINES = "mapLines";
    private static final String PREVIOUS_PART = "previousPart";
    private static final String CORRECTION = "correction";
    private static final String UNREADABLE = "unreadable";
    private static final String ALL_DROPPED = "all-dropped";

    private final ContextBuilder builder;
    private final ObjectMapper mapper;
    private final CallFrame frame;
    private final ModelCalls calls;
    private final ModelProfile.Tier tier;

    /**
     * A kept summary.
     *
     * @param text the kept sentences joined by a space; never blank
     * @param sentences how many sentences it holds, 1 to 3
     * @param dropped how many sentences of the kept answer the gate cut or dropped
     */
    public record Kept(String text, int sentences, int dropped) {}

    /** One part's outcome: its kept summary, or why the chapter gets none. */
    private sealed interface Part {}

    private record Answered(Kept kept) implements Part {}

    private record Failed(String reason) implements Part {}

    private record Aborted(AppError error, int part) implements Part {}

    /** One part of a chapter as it is asked. */
    private record Asked(int number, int parts, String text, List<String> mapLines, String previous) {}

    /**
     * Binds the call to one run.
     *
     * @param builder the non-null context builder that plans the ANALYSIS frame
     * @param mapper the non-null JSON reader
     * @param frame the non-null run's call frame
     * @param calls the non-null seam every call of the run goes through
     * @param tier the non-null tier of the run's model, which sizes a part
     */
    public ChapterSummaryCall(
            final ContextBuilder builder,
            final ObjectMapper mapper,
            final CallFrame frame,
            final ModelCalls calls,
            final ModelProfile.Tier tier) {
        this.builder = Objects.requireNonNull(builder, "builder");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.frame = Objects.requireNonNull(frame, "frame");
        this.calls = Objects.requireNonNull(calls, "calls");
        this.tier = Objects.requireNonNull(tier, "tier");
    }

    /**
     * Summarises one finished chapter.
     *
     * @param chapter the non-null chapter
     * @param sourceLines the non-null source of its segments, tokens removed, one per segment in reading order
     * @param map the non-null book map, read only
     * @param filter the non-null filter of the recurring terms' renderings
     * @return the kept summary, or empty when a part's call failed or its reply kept nothing; an error only when a
     *     pause or a stop aborted a part's call ({@code cancelled}), so the caller makes the whole call again or ends
     */
    public Result<Optional<Kept>> summarize(
            final Chapter chapter, final List<String> sourceLines, final BookMap map, final LexiconFilter filter) {
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(sourceLines, "sourceLines");
        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(filter, "filter");
        final List<List<String>> parts = parts(sourceLines);
        final SummaryGate gate = SummaryGate.of(
                mapper, readingLanguage(), frame.targetLanguage(), map, String.join("\n\n", sourceLines));
        log.debug(
                "Chapter summary call chapter={} key={} segments={} tokens={} parts={} mapLines={}",
                chapter.number(),
                chapter.key(),
                sourceLines.size(),
                chapter.tokens(),
                parts.size(),
                WindowMap.chapterCounted(sourceLines, map, filter).size());
        return outcome(chapter, askParts(chapter, parts, map, filter, gate));
    }

    private Result<Optional<Kept>> outcome(final Chapter chapter, final Part part) {
        return switch (part) {
            case Failed(final String reason) -> {
                log.warn(
                        "Chapter summary not kept chapter={} key={} reason={}",
                        chapter.number(),
                        chapter.key(),
                        reason);
                yield Result.ok(Optional.empty());
            }
            case Aborted(final AppError error, final int number) -> {
                log.debug(
                        "Chapter summary call aborted chapter={} key={} part={}; made again on resume",
                        chapter.number(),
                        chapter.key(),
                        number);
                yield Result.err(error);
            }
            case Answered(final Kept kept) -> Result.ok(Optional.of(logKept(chapter, kept)));
        };
    }

    // Each part after the first carries the kept summary of the one before; the first failed or aborted part ends the
    // chapter.
    private Part askParts(
            final Chapter chapter,
            final List<List<String>> parts,
            final BookMap map,
            final LexiconFilter filter,
            final SummaryGate gate) {
        String previous = "";
        Part outcome = new Failed(UNREADABLE);
        for (int index = 0; index < parts.size(); index++) {
            final List<String> lines = parts.get(index);
            final Asked asked = new Asked(
                    index + 1, parts.size(), text(lines), WindowMap.chapterCounted(lines, map, filter), previous);
            outcome = askPart(chapter, asked, gate);
            switch (outcome) {
                case Failed failed -> {
                    return failed;
                }
                case Aborted aborted -> {
                    return aborted;
                }
                case Answered(final Kept kept) -> previous = kept.text();
            }
        }
        return outcome;
    }

    private Kept logKept(final Chapter chapter, final Kept kept) {
        log.info(
                "chapter summary chapter={} sentences={} dropped={}",
                chapter.number(),
                kept.sentences(),
                kept.dropped());
        log.trace("Chapter summary kept chapter={} text={}", chapter.number(), kept.text());
        return kept;
    }

    // One part: its answer through the gate, and one re-ask when a sentence failed the language check.
    private Part askPart(final Chapter chapter, final Asked asked, final SummaryGate gate) {
        final SummaryGate.Place place = new SummaryGate.Place(chapter.number(), asked.number());
        final String line = "Chapter " + chapter.number() + ", part " + asked.number() + "/" + asked.parts() + ": "
                + excerpt(asked.text());
        final Result<ChatResponse> first = send(values(asked, ""), line);
        if (first.isErr()) {
            final AppError error = Objects.requireNonNull(first.error(), ERROR);
            return isAbort(error)
                    ? new Aborted(error, asked.number())
                    : new Failed(error.code().name());
        }
        final Optional<SummaryGate.Reading> read =
                gate.read(Objects.requireNonNull(first.data(), REPLY).content(), place, false);
        if (read.isEmpty()) {
            return new Failed(UNREADABLE);
        }
        return read.get().faultyWords().isEmpty()
                ? kept(line, read.get())
                : reasked(asked, line, place, gate, read.get());
    }

    private Part kept(final String line, final SummaryGate.Reading reading) {
        if (reading.clean().isEmpty()) {
            return new Failed(ALL_DROPPED);
        }
        final Kept kept = new Kept(
                String.join(" ", reading.clean()), reading.clean().size(), reading.dropped() + reading.faulty());
        calls.answered(Map.of(line, kept.text()));
        return new Answered(kept);
    }

    // A pause or a stop answers the call cancelled; a timeout or any other error is the part's failure.
    private static boolean isAbort(final AppError error) {
        return error.code() == ErrorCode.cancelled;
    }

    // The second answer goes through the same checks with its faulty sentences dropped; when it cannot be had, the
    // first answer's clean sentences stand. An aborted re-ask is an abort: the whole call is made again, never the
    // first answer kept in its place.
    private Part reasked(
            final Asked asked,
            final String line,
            final SummaryGate.Place place,
            final SummaryGate gate,
            final SummaryGate.Reading first) {
        log.debug(
                "Chapter summary re-ask chapter={} part={} faultySentences={}",
                place.chapter(),
                place.part(),
                first.faulty());
        log.trace("Chapter summary re-ask words {}", first.faultyWords());
        final String correction = CORRECTION_START + String.join(", ", first.faultyWords())
                + ". Write the summary again with every word in the target language.";
        final Result<ChatResponse> second = send(values(asked, correction), line);
        if (second.isErr() && isAbort(Objects.requireNonNull(second.error(), ERROR))) {
            return new Aborted(Objects.requireNonNull(second.error(), ERROR), asked.number());
        }
        final Optional<SummaryGate.Reading> read = second.isErr()
                ? Optional.empty()
                : gate.read(Objects.requireNonNull(second.data(), REPLY).content(), place, true);
        if (read.isEmpty()) {
            log.debug(
                    "Chapter summary re-ask chapter={} part={} gave nothing; the first answer's clean sentences stand",
                    place.chapter(),
                    place.part());
            return kept(line, first);
        }
        return kept(line, read.get());
    }

    private Result<ChatResponse> send(final Map<String, String> values, final String line) {
        final ChatRequest request =
                builder.analysisRequest(PromptName.CHAPTER_SUMMARY, frame, values, OutputLimit.forChapterSummary());
        if (log.isTraceEnabled()) {
            log.trace("Chapter summary call messages {}", request.messages());
        }
        final Result<ChatResponse> reply = calls.callAbout(
                CallKind.CHAPTER_SUMMARY,
                List.of(),
                request,
                CallDescriptor.asking(PromptName.CHAPTER_SUMMARY, List.of(line)));
        if (reply.isOk()) {
            log.trace(
                    "Chapter summary call reply {}",
                    Objects.requireNonNull(reply.data(), REPLY).content());
        }
        return reply;
    }

    private static Map<String, String> values(final Asked asked, final String correction) {
        final Map<String, String> values = new HashMap<>();
        values.put(CHAPTER_SOURCE, asked.text());
        values.put(MAP_LINES, String.join("\n", asked.mapLines()));
        values.put(PREVIOUS_PART, asked.previous());
        values.put(CORRECTION, correction);
        return values;
    }

    // Parts end at segment ends; a segment longer than a whole part is a part alone, its middle dropped.
    private List<List<String>> parts(final List<String> lines) {
        final int budget = WindowBudgets.chapterPart(tier);
        final List<List<String>> parts = new ArrayList<>();
        List<String> part = new ArrayList<>();
        int tokens = 0;
        for (final String line : lines) {
            final int size = TokenEstimator.estimate(line, readingLanguage());
            if (!part.isEmpty() && tokens + size > budget) {
                parts.add(part);
                part = new ArrayList<>();
                tokens = 0;
            }
            part.add(size > budget ? ChapterText.capped(List.of(line), readingLanguage(), budget) : line);
            tokens += Math.min(size, budget);
        }
        if (!part.isEmpty()) {
            parts.add(part);
        }
        return parts;
    }

    private static String text(final List<String> lines) {
        return String.join("\n", lines);
    }

    private @Nullable String readingLanguage() {
        return frame.analysisLanguage() != null ? frame.analysisLanguage() : frame.sourceLanguage();
    }

    // The part as one short line: the call's panel names what was summarised, not the whole part.
    private static String excerpt(final String text) {
        final String line = text.replaceAll("\\s+", " ").strip();
        return line.length() <= ASKED_CHARS ? line : line.substring(0, ASKED_CHARS) + "…";
    }
}
