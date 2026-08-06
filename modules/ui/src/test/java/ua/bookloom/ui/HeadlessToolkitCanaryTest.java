package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * The headless-rendering canary: proof that a TestFX suite boots the JavaFX toolkit and renders a real scene graph
 * with no display server attached.
 *
 * <p>This is the load-bearing test of ADR-0019. The frozen specification names "TestFX + Monocle (headless)" for
 * every UI tier, but {@code org.testfx:openjfx-monocle} has no release past 21.0.2 and works by patching
 * {@code javafx.graphics} internals, so it cannot be run against this project's JavaFX 26 runtime at all. JavaFX 26
 * ships its own headless glass platform instead, selected by {@code -Dglass.platform=Headless} in
 * {@code bookloom.test-conventions}. That substitution is only credible if something actually renders under it —
 * otherwise the whole UI test tier would be discovered to be unrunnable at the first real screen, several changes
 * from here.
 *
 * <p>Deliberately no robot interaction: a click or a keystroke needs a Glass robot, which is a separate capability
 * from rendering. What must be true today is that the toolkit starts, a stage shows, and layout runs.
 */
class HeadlessToolkitCanaryTest extends ApplicationTest {

    private static final String LABEL_ID = "#canary-label";

    @Override
    public void start(final Stage stage) {
        final Label label = new Label("BookLoom");
        label.setId(LABEL_ID.substring(1));
        stage.setScene(new Scene(new StackPane(label), 320, 200));
        stage.show();
    }

    // Covers task 5.5: a TestFX test renders headlessly under the JavaFX 26 built-in headless platform, so
    // `./gradlew check` needs no display server and no Monocle artifact (ADR-0019).
    @Test
    void start_headlessGlassPlatform_showsTheStageAndLaysOutTheScene() {
        assertThat(System.getProperty("glass.platform"))
                .as("the headless platform must be selected by the test conventions, not by the local environment")
                .isEqualTo("Headless");

        // Looked up through the live scene graph rather than held in a field: the lookup itself proves the stage
        // is registered with the toolkit's window list, which a field reference would not.
        final Label label = lookup(LABEL_ID).queryAs(Label.class);

        assertThat(label.getScene().getWindow().isShowing()).isTrue();
        assertThat(label.getText()).isEqualTo("BookLoom");

        // A width only exists once a layout pass has actually run, which is what distinguishes "the toolkit
        // started" from "the toolkit rendered".
        assertThat(label.getWidth()).isGreaterThan(0.0);
    }
}
