package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.BookFixtures;

/**
 * Where the check that a destination file exists runs and how its answer is published: off the FX thread, on the
 * background executor, and only if nobody has asked something newer in the meantime. A queued executor lets each test
 * decide when an answer arrives.
 */
class BookBriefViewModelExistsTest extends BookBriefViewModelTestBase {

    @TempDir
    private Path dir;

    private final QueuedExecutor queue = new QueuedExecutor();

    @BeforeEach
    void useTheQueuedExecutor() {
        brief = onFx(() -> new BookBriefViewModel(imports, queue));
        openBook(dir.resolve("Frankenstein.epub"), BookFixtures.frankenstein());
        runQueue();
    }

    private void runQueue() {
        queue.runAll();
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the file were looked for on the FX thread itself, THEN the answer would be there before any executor ran.
    @Test
    void destinationExists_editedToAnExistingFile_isKnownOnlyOnceTheExecutorHasRun() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");

        editDestination(occupied.toString());

        assertThat(queue.pending()).isEqualTo(1);
        assertThat(destinationExists()).isFalse();
        runQueue();
        assertThat(destinationExists()).isTrue();
    }

    // IF the report were cleared while an answer is in flight, THEN the warning would flicker on every keystroke.
    @Test
    void destinationExists_answerInFlight_keepsThePreviousValue() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        editDestination(occupied.toString());
        runQueue();
        assertThat(destinationExists()).isTrue();

        editDestination(dir.resolve("free.epub").toString());

        assertThat(destinationExists()).isTrue();
        runQueue();
        assertThat(destinationExists()).isFalse();
    }

    // IF an older answer were published after a newer one, THEN the report would describe the path typed before.
    @Test
    void destinationExists_olderAnswerArrivesLast_isDiscarded() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        editDestination(occupied.toString());
        editDestination(dir.resolve("free.epub").toString());

        queue.runNewestFirst();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(destinationExists()).isFalse();
    }

    // IF an unparsable path waited for an executor, THEN the report would describe the path typed before it.
    @Test
    void destinationExists_pathThatCannotBeParsed_isFalseAtOnce() throws IOException {
        final Path occupied = Files.writeString(dir.resolve("taken.epub"), "already here");
        editDestination(occupied.toString());
        runQueue();
        assertThat(destinationExists()).isTrue();

        editDestination("/books/bad\0name.epub");

        assertThat(destinationExists()).isFalse();
        assertThat(queue.pending()).isZero();
    }

    // IF a blank path were checked, THEN the current directory would be reported as an occupied destination.
    @Test
    void destinationExists_blankPath_isFalseAtOnceAndNothingIsSubmitted() {
        editDestination("   ");

        assertThat(destinationExists()).isFalse();
        assertThat(queue.pending()).isZero();
    }

    // IF a stale answer for the previous book landed after the next was opened, THEN the warning would be for the
    // wrong file.
    @Test
    void destinationExists_bookChangedWhileAnAnswerIsInFlight_answerOfTheOldBookIsDiscarded() throws IOException {
        Files.writeString(dir.resolve("Frankenstein.uk.epub"), "already here");
        refresh();
        openBook(dir.resolve("Dracula.epub"), BookFixtures.frankenstein());

        queue.runNewestFirst();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(destinationExists()).isFalse();
    }
}
