package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ChapterSummary;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Group 12's acceptance (design D10): a chapter's draft carries the earlier chapters' summaries, and only the part
 * of a summary that names what the earlier text holds; with the brief's switch off no summary is asked or sent. Written
 * red in task 12.1; the chapter-summary call's schema must carry {@code "title":"chapter_summary"} so the Ollama body,
 * which names no schema, shows it too (task 12.4).
 */
@Tag("slow")
class ChapterSummaryAcceptanceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final String SUMMARY_MARK = "\"chapter_summary\"";
    private static final String SUMMARY_REPLY = "{\"summary\":\"S1. Tobin Reed arrived. S3.\"}";
    private static final String EARLIER = "[Earlier chapters]";
    private static final String CARD = "[Recently present";
    private static final String CHAPTER_2 = "Lamp Room";
    private static final String ITEM_MARK = "<s id=";
    private static final String ITEMS_OPEN = "<Items>";
    private static final String ABSENT_NAME = "Tobin Reed";
    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]+)\">(.*?)</s>", Pattern.DOTALL);

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_switchOn_chapter2FirstDraftCarriesChapter1Summary(final ProviderKind kind) {
        final List<String> bodies = bodiesOfRun(kind, false, null).bodies();

        assertThat(summaryRequests(bodies))
                .as("one chapter-summary call, for chapter 1")
                .hasSize(1);
        final String message = userMessage(firstChapter2Draft(bodies));
        assertThat(message).contains(EARLIER, "S1.");
        final int summary = message.indexOf("S1.");
        assertThat(summary)
                .as("the summary stands under its heading, before the items")
                .isGreaterThan(message.indexOf(EARLIER))
                .isLessThan(message.indexOf(ITEMS_OPEN));
        // Block 8 holds the summaries first, then the card; the card is left out at a chapter start, as here.
        assertThat(message.indexOf(CARD))
                .as("the recent-names card, when present, follows the summary")
                .satisfiesAnyOf(
                        card -> assertThat(card).isNegative(),
                        card -> assertThat(card).isGreaterThan(summary));
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_switchOn_noChapter1CallCarriesSummary(final ProviderKind kind) {
        final List<String> bodies = bodiesOfRun(kind, false, null).bodies();

        assertThat(firstChapter2Draft(bodies)).as("chapter 2 was drafted").isNotEmpty();
        final int firstChapter2 = bodies.indexOf(firstChapter2Draft(bodies));
        final List<String> before = bodies.subList(0, firstChapter2);
        assertThat(summaryRequests(before))
                .as("the summary is asked before chapter 2 starts")
                .hasSize(1);
        assertThat(before.stream().filter(ChapterSummaryAcceptanceTest::isDraft))
                .as("chapter 1 was drafted")
                .isNotEmpty()
                .noneMatch(body -> userMessage(body).contains(EARLIER));
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_summaryNamesEntryAbsentFromChapter1_sentenceDropped(final ProviderKind kind) {
        final List<String> bodies = bodiesOfRun(kind, true, null).bodies();

        assertThat(firstChapter2Draft(bodies)).as("chapter 2 was drafted").isNotEmpty();
        final String block = earlierBlock(userMessage(firstChapter2Draft(bodies)));
        assertThat(block).contains("S1.", "S3.").doesNotContain(ABSENT_NAME);
    }

    // The switch-off case. Until the chapter-summary call (12.4) and block 8 (12.5) exist, no run makes such a call or
    // sends such a block, so the two "none" assertions below hold whatever the switch says; they become discriminating
    // once those tasks land. What proves the switch today is the stored brief reading it back as off.
    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_switchOff_noChapterSummaryRequest(final ProviderKind kind) {
        final RunSeen run = bodiesOfRun(kind, false, false);

        assertThat(run.storedBrief().summariesOn())
                .as("the stored brief keeps the switch off")
                .isFalse();
        assertThat(firstChapter2Draft(run.bodies())).as("chapter 2 was drafted").isNotEmpty();
        assertThat(summaryRequests(run.bodies())).as("no chapter-summary call").isEmpty();
        assertThat(run.bodies().stream().filter(ChapterSummaryAcceptanceTest::isDraft))
                .as("no draft carries an earlier-chapters block")
                .noneMatch(body -> userMessage(body).contains(EARLIER));
    }

    // The keys are what the snapshot keeps of block 8, for a run's draft and for a desk retry that rebuilt it.
    @Test
    void snapshot_storesSummaryKeys() {
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final MiniRunner runner = new MiniRunner(book, QualityDial.BALANCED, NamePolicy.TRANSLITERATE, null);
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubResponder(ChapterSummaryAcceptanceTest::replyTo);
            final Prepared prepared = SequenceJobs.importBook(book.writeEpub(directory), runner.brief());
            final ChatModel model = provider.model(REQUEST_TIMEOUT, ignored -> {});
            SequenceJobs.run(prepared, model, ReviewMode.UNATTENDED, null, e -> {});
            final List<String> chapter1 = keptKeys(prepared);
            final String heading = chapter2Heading(prepared);
            final List<SegmentRecord> records = records(prepared);

            assertThat(chapter1).as("chapter 1's summary was kept").hasSize(1);
            assertThat(records.stream().map(SegmentRecord::context).filter(Objects::nonNull))
                    .extracting(ContextSnapshot::chapterSummaryKeys)
                    .containsOnly(List.of(), chapter1)
                    .contains(List.of(), chapter1);
            assertThat(contextOf(records, heading).chapterSummaryKeys()).isEqualTo(chapter1);
            final SegmentRecord retried = SequenceJobs.retryDraft(prepared)
                    .retry(prepared.projectId(), heading, null, false, model)
                    .data();
            assertThat(Objects.requireNonNull(retried).context())
                    .extracting(ContextSnapshot::chapterSummaryKeys, ContextSnapshot::retry)
                    .containsExactly(chapter1, true);
        }
    }

    private static List<String> keptKeys(final Prepared prepared) {
        return Objects.requireNonNull(
                        prepared.stores().summaries().list(prepared.projectId()).data(), "summaries")
                .stream()
                .map(ChapterSummary::chapterKey)
                .toList();
    }

    private static String chapter2Heading(final Prepared prepared) {
        return prepared.story().stream()
                .filter(segment -> segment.masked().contains(CHAPTER_2))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static List<SegmentRecord> records(final Prepared prepared) {
        return Objects.requireNonNull(
                prepared.stores().segments().all(prepared.projectId()).data(), "records");
    }

    private static ContextSnapshot contextOf(final List<SegmentRecord> records, final String segmentId) {
        return records.stream()
                .filter(record -> record.segmentId().equals(segmentId))
                .map(SegmentRecord::context)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    private RunSeen bodiesOfRun(
            final ProviderKind kind, final boolean seedAbsentName, @Nullable final Boolean chapterSummaries) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final MiniRunner runner = new MiniRunner(book, QualityDial.BALANCED, NamePolicy.TRANSLITERATE, null);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(ChapterSummaryAcceptanceTest::replyTo);
            final Prepared prepared = SequenceJobs.importBook(
                    book.writeEpub(directory), runner.brief().withChapterSummaries(chapterSummaries));
            if (seedAbsentName) {
                seed(prepared, ABSENT_NAME);
            }
            SequenceJobs.run(
                    prepared, provider.model(REQUEST_TIMEOUT, ignored -> {}), ReviewMode.UNATTENDED, null, e -> {});
            return new RunSeen(provider.chatBodies(), SequenceJobs.storedBrief(prepared));
        }
    }

    /** The request bodies a run sent and the brief its project holds afterwards. */
    private record RunSeen(List<String> bodies, BookBrief storedBrief) {}

    private static void seed(final Prepared prepared, final String term) {
        Objects.requireNonNull(
                prepared.stores()
                        .glossary()
                        .add(GlossaryEntry.of(
                                prepared.projectId() + ":" + term,
                                prepared.projectId(),
                                term,
                                null,
                                TermType.CHARACTER,
                                Gender.UNKNOWN,
                                false))
                        .data(),
                "seeded");
    }

    // The summary call gets its canned summary, a batch draft its items upper-cased, anything else the empty reviewer.
    private static String replyTo(final String body) {
        if (isChapterSummary(body)) {
            return SUMMARY_REPLY;
        }
        final Matcher matcher = ITEM.matcher(userMessage(body));
        final List<Map<String, String>> items = new ArrayList<>();
        while (matcher.find()) {
            final Map<String, String> item = new LinkedHashMap<>();
            item.put("id", matcher.group(1));
            item.put("target", matcher.group(2).toUpperCase(Locale.ROOT));
            items.add(item);
        }
        return items.isEmpty() ? "{\"results\":[]}" : write(Map.of("items", items));
    }

    private static boolean isChapterSummary(final String body) {
        return body.contains(SUMMARY_MARK);
    }

    private static boolean isDraft(final String body) {
        return !isChapterSummary(body) && userMessage(body).contains(ITEM_MARK);
    }

    private static List<String> summaryRequests(final List<String> bodies) {
        return bodies.stream()
                .filter(ChapterSummaryAcceptanceTest::isChapterSummary)
                .toList();
    }

    // A draft of chapter 2 holds its heading among its items; chapter 1's last window only shows it as the next source.
    private static String firstChapter2Draft(final List<String> bodies) {
        return bodies.stream()
                .filter(ChapterSummaryAcceptanceTest::isDraft)
                .filter(body -> ITEM.matcher(userMessage(body))
                        .results()
                        .anyMatch(item -> item.group(2).contains(CHAPTER_2)))
                .findFirst()
                .orElse("");
    }

    private static String earlierBlock(final String message) {
        final int start = message.indexOf(EARLIER);
        final int end = start < 0 ? -1 : message.indexOf("\n\n", start);
        return start < 0 ? "" : message.substring(start, end < 0 ? message.length() : end);
    }

    /** The user message of a chat request body, the same field in both dialects. */
    private static String userMessage(final String body) {
        try {
            return MAPPER.readTree(body)
                    .path("messages")
                    .path(1)
                    .path("content")
                    .asText();
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not read request body", cause);
        }
    }

    private static String write(final Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode reply", cause);
        }
    }
}
