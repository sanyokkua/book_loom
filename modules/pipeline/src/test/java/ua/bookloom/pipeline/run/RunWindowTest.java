package ua.bookloom.pipeline.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** The window a run sizes for is latched when a chunk starts, so a save mid-chunk changes the next chunk only. */
class RunWindowTest {

    // IF the override is saved after the chunk was entered, THEN the chunk keeps its window until the next entry.
    @Test
    void enterChunk_overrideSavedAfterEntry_currentKeepsOldUntilNextEntry() {
        final AtomicReference<Integer> stored = new AtomicReference<>();
        final RunWindow window = new RunWindow(null, stored::get);

        window.enterChunk();
        stored.set(8192);

        assertThat(window.current()).isEqualTo(16384);
        window.enterChunk();
        assertThat(window.current()).isEqualTo(8192);
    }

    @Test
    void enterChunk_noOverride_usesCappedDetected() {
        final RunWindow window = new RunWindow(131072, () -> null);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(16384);
    }

    @Test
    void enterChunk_overrideAboveTheCap_overrideWins() {
        final RunWindow window = new RunWindow(4096, () -> 32768);

        window.enterChunk();

        assertThat(window.current()).isEqualTo(32768);
    }
}
