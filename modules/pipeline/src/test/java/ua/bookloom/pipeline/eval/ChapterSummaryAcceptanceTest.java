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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.SequenceJobs.Prepared;
import ua.bookloom.pipeline.WireMockProvider;

/**
 * Group 12's acceptance (design D8): a chapter's draft carries the earlier chapters' summaries, and only the part
 * of a summary that names what the earlier text holds. Written red in task 12.1; the chapter-summary call's schema
 * must carry {@code "title":"chapter_summary"} so the Ollama body, which names no schema, shows it too (task 12.3).
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
    private static final String ABSENT_NAME = "Tobin Reed";
    private static final Pattern ITEM = Pattern.compile("<s id=\"([^\"]+)\">(.*?)</s>", Pattern.DOTALL);

    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_switchOn_chapter2FirstDraftCarriesChapter1Summary(final ProviderKind kind) {
        final List<String> bodies = bodiesOfRun(kind, false);

        assertThat(summaryRequests(bodies))
                .as("one chapter-summary call, for chapter 1")
                .hasSize(1);
        final String message = userMessage(firstChapter2Draft(bodies));
        assertThat(message).contains(EARLIER, "S1.");
        assertThat(message.indexOf("S1."))
                .as("the summary stands before the recent-names card")
                .isLessThan(message.indexOf(CARD))
                .isGreaterThan(message.indexOf(EARLIER));
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_switchOn_noChapter1CallCarriesSummary(final ProviderKind kind) {
        final List<String> bodies = bodiesOfRun(kind, false);

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
        final List<String> bodies = bodiesOfRun(kind, true);

        assertThat(firstChapter2Draft(bodies)).as("chapter 2 was drafted").isNotEmpty();
        final String block = earlierBlock(userMessage(firstChapter2Draft(bodies)));
        assertThat(block).contains("S1.", "S3.").doesNotContain(ABSENT_NAME);
    }

    private List<String> bodiesOfRun(final ProviderKind kind, final boolean seedAbsentName) {
        final MiniBook book = MiniBook.load(MiniBook.MINI_EN);
        final MiniRunner runner = new MiniRunner(book, QualityDial.BALANCED, NamePolicy.TRANSLITERATE, null);
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubResponder(ChapterSummaryAcceptanceTest::replyTo);
            final Prepared prepared = SequenceJobs.importBook(book.writeEpub(directory), runner.brief());
            if (seedAbsentName) {
                seed(prepared, ABSENT_NAME);
            }
            SequenceJobs.run(
                    prepared, provider.model(REQUEST_TIMEOUT, ignored -> {}), ReviewMode.UNATTENDED, null, e -> {});
            return provider.chatBodies();
        }
    }

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

    private static String firstChapter2Draft(final List<String> bodies) {
        return bodies.stream()
                .filter(ChapterSummaryAcceptanceTest::isDraft)
                .filter(body -> userMessage(body).contains(CHAPTER_2))
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
