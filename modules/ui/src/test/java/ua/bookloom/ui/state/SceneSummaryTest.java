package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.FxTestBase;
import ua.bookloom.ui.MemorySummaries;

/** The scene summary line reads the project's newest rolling summary off the FX thread, and again when it changes. */
class SceneSummaryTest extends FxTestBase {

    private static final String TARGET = "Mira Holt returns to Velk.";

    private final MemorySummaries summaries = new MemorySummaries();
    private final CurrentProject project = new CurrentProject();
    private StateMirror mirror;
    private SceneSummary scene;

    @BeforeEach
    void build() {
        mirror = new StateMirror();
        scene = new SceneSummary(summaries, Runnable::run, Runnable::run, project, mirror);
    }

    private void openBook() {
        onFx(() -> {
            OpenBookForTest.open(project, "en", "uk");
            return null;
        });
    }

    private String text() {
        return onFx(() -> scene.text().get());
    }

    // IF the line showed the source text when a target text exists, THEN the person would read the wrong language.
    @Test
    void text_summaryWithATargetText_isTheTargetText() {
        summaries.put("p1", "Mira Holt goes back to Velk.", TARGET, 2);

        openBook();

        assertThat(text()).isEqualTo(TARGET);
    }

    // IF a summary with no target text showed nothing, THEN the line would claim there is no summary.
    @Test
    void text_summaryWithNoTargetText_fallsBackToTheSourceText() {
        summaries.put("p1", "Mira Holt goes back to Velk.", "", 2);

        openBook();

        assertThat(text()).isEqualTo("Mira Holt goes back to Velk.");
    }

    // IF a book with no summary showed old text, THEN the card would show another book's story.
    @Test
    void text_noSummaryYet_isNull() {
        openBook();

        assertThat(text()).isNull();
    }

    // IF the line read the summary once, THEN it would stay on the first one while the run keeps refreshing it.
    @Test
    void text_summaryVersionRises_readsTheNewSummary() {
        summaries.put("p1", "First.", "Перше.", 1);
        openBook();
        summaries.put("p1", "Second.", "Друге.", 2);

        onFx(() -> {
            mirror.live().publishCalls(new LiveCalls(null, null, Map.of(), null, List.of(), List.of(), 2));
            return null;
        });

        assertThat(text()).isEqualTo("Друге.");
    }
}
