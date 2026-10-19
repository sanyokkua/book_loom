package ua.bookloom.pipeline.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.glossary.LexiconReplies.reading;
import static ua.bookloom.pipeline.glossary.LexiconReplies.termOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ForeignPassagePolicy;
import ua.bookloom.pipeline.glossary.LexiconReading.Decision;
import ua.bookloom.pipeline.glossary.LexiconReading.Verdict;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.prompt.PromptTemplates;
import ua.bookloom.pipeline.prompt.StyleSheet;

/**
 * A model scan's candidates are many frequent words, mostly ordinary (15i.N gate): at Balanced the most frequent are read
 * in their windows, and of the rest only the words the batch choice keeps, so a wide pool costs a call per ten words.
 * The book is invented.
 */
class LexiconScanReadingTest {

    private static final CallFrame FRAME =
            new CallFrame("en", "uk", StyleSheet.from(BookBrief.defaults("en")), ForeignPassagePolicy.KEEP);
    private static final String CHOICE =
            "{\"terms\":[{\"term\":\"beacon\",\"keep\":true},{\"term\":\"ferry\",\"keep\":false}]}";

    private final List<CallKind> kinds = new ArrayList<>();
    private final List<String> readTerms = new ArrayList<>();

    /** Nine days: the beacon, the ferry and the anchor each on some of them, and a lantern on the last. */
    private static BookSlice book() {
        return GlossaryTestSegments.of(IntStream.rangeClosed(1, 9)
                .mapToObj(day -> "Day %02d. ".formatted(day)
                        + switch (day % 3) {
                            case 0 -> "The beacon was lit before the wind rose.";
                            case 1 -> "The ferry crossed the bay slowly.";
                            default -> "The anchor dragged in the mud.";
                        }
                        + (day == 9 ? " A lantern hung there." : ""))
                .toList());
    }

    /** A batch choice that keeps the beacon and drops the ferry, and a reading of each read term as a term. */
    private ModelCalls model() {
        return (kind, segmentId, request) -> {
            kinds.add(kind);
            if (kind == CallKind.TERM_CHOICE) {
                return Result.ok(new ChatResponse(CHOICE, FinishReason.STOP));
            }
            final String term = termOf(request.messages().getLast().content());
            readTerms.add(term);
            return Result.ok(new ChatResponse(reading(term, "term", 1, "маяк"), FinishReason.STOP));
        };
    }

    @Test
    void scan_balanced_readsInWindowsOnlyWhatTheBatchChoiceKeeps() {
        final LexiconReading reading = new LexiconReading(
                new PromptTemplates(), new ObjectMapper(), new TermChoice(new PromptTemplates(), new ObjectMapper()));

        final List<String> pool =
                new ArrayList<>(Collections.nCopies(LexiconReading.readFirst(QualityDial.BALANCED), "lantern"));
        pool.addAll(List.of("beacon", "ferry", "anchor"));

        final Result<Map<String, Decision>> result = reading.scan(pool, book(), QualityDial.BALANCED, FRAME, model());

        assertThat(result.isOk()).as("%s", result.error()).isTrue();
        assertThat(kinds).containsExactly(CallKind.TERM_CHOICE, CallKind.TERM_READING, CallKind.TERM_READING);
        assertThat(readTerms).containsExactly("lantern", "beacon");
        assertThat(Objects.requireNonNull(result.data()))
                .containsEntry("beacon", new Decision(Verdict.KEEP, "маяк"))
                .containsEntry("ferry", new Decision(Verdict.DROP, null))
                .containsEntry("anchor", new Decision(Verdict.UNDECIDED, null));
    }
}
