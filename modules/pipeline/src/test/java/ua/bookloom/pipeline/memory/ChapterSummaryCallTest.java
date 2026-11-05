package ua.bookloom.pipeline.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.FILTER;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.FORMAT;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.GLAFIRA;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.SOURCE;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.call;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.chapter;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.map;
import static ua.bookloom.pipeline.memory.ChapterSummaryCalls.reply;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.pipeline.ScriptedChatModel;

/**
 * The chapter-summary call (design D10): at most three whole sentences in the target language, a sentence with a
 * source-language word asked once more and then dropped, a sentence naming an entry the chapter does not hold dropped,
 * a long chapter read in parts, and a failed call kept as no summary with one warning.
 */
class ChapterSummaryCallTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger bookloom;
    private @Nullable Level previousLevel;

    @BeforeEach
    void attachAppender() {
        bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        previousLevel = bookloom.getLevel();
        bookloom.setLevel(Level.DEBUG);
        appender.start();
        bookloom.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        bookloom.detachAppender(appender);
        bookloom.setLevel(previousLevel);
        appender.stop();
    }

    @Test
    void call_fourSentences_cutToThree() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        FORMAT, reply("Мірта йде до міста. Там вона знаходить брата. Брат мовчить. Вони вертаються."));

        final Optional<ChapterSummaryCall.Kept> kept =
                call(model, event -> {}).summarize(chapter(1, 2), SOURCE, map(List.of()), FILTER);

        assertThat(model.requests()).hasSize(1);
        assertThat(kept)
                .hasValue(new ChapterSummaryCall.Kept(
                        "Мірта йде до міста. Там вона знаходить брата. Брат мовчить.", 3, 1));
    }

    @Test
    void call_sentenceWithChto_askedAgainThenDropped() {
        final String withChto = "Мірта йде до міста. Вона знає, что брат там. Брат мовчить.";
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo(FORMAT, reply(withChto)).answerTo(FORMAT, reply(withChto));

        final Optional<ChapterSummaryCall.Kept> kept =
                call(model, event -> {}).summarize(chapter(1, 2), SOURCE, map(List.of()), FILTER);

        assertThat(model.requests()).hasSize(2);
        assertThat(userMessage(model.requests().getFirst())).doesNotContain(ChapterSummaryCall.CORRECTION_START);
        assertThat(userMessage(model.requests().get(1))).contains(ChapterSummaryCall.CORRECTION_START + "что.");
        assertThat(kept).hasValue(new ChapterSummaryCall.Kept("Мірта йде до міста. Брат мовчить.", 2, 1));
    }

    @Test
    void call_entryFromLaterChapter_dropped() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(FORMAT, reply("Мірта йде до міста. Вона зустрічає Глафіру біля воріт. Брат мовчить."));

        final Optional<ChapterSummaryCall.Kept> kept =
                call(model, event -> {}).summarize(chapter(2, 2), SOURCE, map(List.of(GLAFIRA)), FILTER);

        assertThat(model.requests()).as("no re-ask for a name").hasSize(1);
        assertThat(kept).hasValue(new ChapterSummaryCall.Kept("Мірта йде до міста. Брат мовчить.", 2, 1));
    }

    // About 6,700 source tokens against the small tier's 2,500 per part: three parts in order.
    @Test
    void call_longChapter_threeParts() {
        final List<String> lines = Collections.nCopies(
                60, "Мирта шла по дороге к мельнице. ".repeat(10).strip());
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(FORMAT, reply("Частина перша. Мірта йде."))
                .answerTo(FORMAT, reply("Частина друга. Мірта бачить млин."))
                .answerTo(FORMAT, reply("Частина третя. Мірта вертається."));

        final Optional<ChapterSummaryCall.Kept> kept =
                call(model, event -> {}).summarize(chapter(3, 60), lines, map(List.of()), FILTER);

        assertThat(model.requests()).hasSize(3);
        assertThat(userMessage(model.requests().getFirst())).doesNotContain("<Earlier part>");
        assertThat(userMessage(model.requests().get(1))).contains("<Earlier part>\nЧастина перша. Мірта йде.\n");
        assertThat(userMessage(model.requests().get(2)))
                .contains("<Earlier part>\nЧастина друга. Мірта бачить млин.\n");
        assertThat(kept).hasValue(new ChapterSummaryCall.Kept("Частина третя. Мірта вертається.", 2, 0));
    }

    @Test
    void call_failed_noSummaryWarn() {
        final ScriptedChatModel model = new ScriptedChatModel()
                .answerTo(
                        FORMAT,
                        Result.err(AppError.of(ErrorCode.contextWindow, "Too long", "The prompt does not fit.")));

        final Optional<ChapterSummaryCall.Kept> kept =
                call(model, event -> {}).summarize(chapter(4, 2), SOURCE, map(List.of()), FILTER);

        assertThat(kept).isEmpty();
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(line -> line.contains("chapter=4"))
                .singleElement()
                .asString()
                .contains("reason=contextWindow");
    }

    @Test
    void call_answered_snapshotAsksChapterThenShowsSummary() {
        final ScriptedChatModel model =
                new ScriptedChatModel().answerTo(FORMAT, reply("Мірта йде до міста. Там вона знаходить брата."));
        final List<JobEvent> events = new ArrayList<>();

        call(model, events::add).summarize(chapter(1, 2), SOURCE, map(List.of()), FILTER);

        final List<CallSnapshot> shown = events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .toList();
        final String asked =
                "Chapter 1, part 1/1: Мирта вышла из мельницы и пошла к городу. В городе она нашла брата у старого моста.";
        assertThat(shown.getFirst().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple(asked, null));
        assertThat(shown.getLast().items())
                .extracting(CallItem::id, CallItem::target)
                .containsExactly(tuple(asked, "Мірта йде до міста. Там вона знаходить брата."));
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().getLast().content();
    }
}
