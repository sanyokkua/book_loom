package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.prompt.ReasoningPlan;
import ua.bookloom.pipeline.prompt.RequestDefaults;
import ua.bookloom.pipeline.prompt.SamplingMode;

/** A retry replays a draft in the request defaults the run was resolved with, not in whatever the environment says. */
class RetryFramesTest {

    @Test
    void of_requestDefaults_areCarriedByTheFrame() {
        final Document book = new Document("d1", BookFormat.TXT, "en", null, null, null, "hash", Map.of(), List.of());
        final BookBrief brief = BookBrief.defaults("en").withLanguages("en", "uk");
        final ContextSnapshot snapshot =
                new ContextSnapshot(List.of(), List.of(), List.of(), null, "Style.", List.of(), List.of());
        final RequestDefaults defaults =
                new RequestDefaults(ReasoningPlan.stored(ReasoningLevel.LOW), SamplingMode.TUNED);

        assertThat(RetryFrames.of(brief, snapshot, book, null, defaults).requestDefaults())
                .isEqualTo(defaults);
    }
}
