package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.util.Locale;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import ua.bookloom.ui.UiTestInjector;
import ua.bookloom.ui.state.FixedMemory;

/** The tagline and the memory-based model line of the first-launch Projects card. */
class ProjectsEmptyTest extends ProjectsScreenTestBase {

    private static final String LINE = "projects-empty-recommend";
    private final FixedMemory memory = new FixedMemory(16);

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.builder(locale)
                .projects(projects)
                .projectActions(actions)
                .revealer(revealer)
                .engine(engine)
                .memory(memory)
                .gated()
                .build();
    }

    private String line() {
        return ((Label) node(LINE)).getText();
    }

    @Test
    void tagline_saysPrivateWithLocalModel() throws Exception {
        showEmptyListing();

        assertThat(textOf("projects-empty-card"))
                .contains("Translate a book. Private when you use a local AI model.")
                .doesNotContain("own computer")
                .doesNotContain("always");
    }

    @Test
    void recommend_16gb_namesE4b() throws Exception {
        memory.gigabytes(16);
        showEmptyListing();

        assertThat(line())
                .isEqualTo(
                        "This computer has 16 GB: gemma4:e4b is recommended. Pull it with `ollama pull gemma4:e4b`.");
    }

    @Test
    void recommend_32gb_names26bA4b() throws Exception {
        memory.gigabytes(32);
        showEmptyListing();

        assertThat(line()).contains("gemma4:26b-a4b is recommended").doesNotContain("31b");
        assertThat(isPresent("projects-empty-optin")).isFalse();
    }

    @Test
    void recommend_48gb_offers31b() throws Exception {
        memory.gigabytes(48);
        showEmptyListing();

        assertThat(line()).contains("gemma4:26b-a4b is recommended");
        assertThat(((Label) node("projects-empty-optin")).getText())
                .isEqualTo("Optional, best quality, slower, not measured here: gemma4:31b (`ollama pull gemma4:31b`).");
    }

    @Test
    void projectList_hidesLine() throws Exception {
        memory.gigabytes(16);
        showListing(ukrainianRunning());

        assertThat(isPresent(LINE)).isFalse();
    }

    @Test
    void recommend_startsNoProcess() throws Exception {
        showEmptyListing();

        assertThat(node(LINE)).isInstanceOf(Label.class);
        assertThat(textOf(LINE)).contains("`ollama pull gemma4:e4b`");
        assertThat(engine.requests()).isEmpty();
    }
}
