package ua.bookloom.pipeline.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.pipeline.checks.WordValidator;

/** A frame carries the request defaults its run resolved, so every call of the run is built with the same ones. */
class CallFrameRequestDefaultsTest {

    private static final RequestDefaults LOW =
            new RequestDefaults(ReasoningPlan.stored(ReasoningLevel.LOW), SamplingMode.TUNED);

    private static final Document BOOK =
            new Document("d1", BookFormat.TXT, "en", null, null, null, "hash", Map.of(), List.of());
    private static final BookBrief BRIEF = BookBrief.defaults("en").withLanguages("en", "uk");

    @Test
    void forRun_withDefaults_carriesThem() {
        final CallFrame frame = CallFrame.forRun(BRIEF, StyleSheet.from(BRIEF), BOOK, null, WordValidator.none(), LOW);

        assertThat(frame.requestDefaults()).isEqualTo(LOW);
    }

    @Test
    void forBook_withDefaults_carriesThem() {
        final CallFrame frame = CallFrame.forBook(BRIEF, BOOK, null, LOW);

        assertThat(frame.requestDefaults()).isEqualTo(LOW);
    }
}
