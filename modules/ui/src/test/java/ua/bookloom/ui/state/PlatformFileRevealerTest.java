package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** The production revealer over a recording launcher, so no test ever opens a real file manager. */
class PlatformFileRevealerTest {

    private static final Path BOOK = Path.of("books", "Frankenstein.uk.epub");

    private static CommandLauncher launch(final List<List<String>> launched) {
        return command -> {
            launched.add(command);
            return CompletableFuture.completedFuture(0);
        };
    }

    private static CommandLauncher exiting(final int code) {
        return command -> CompletableFuture.completedFuture(code);
    }

    // IF the revealer did not hand the system's command to the launcher, THEN the button would do nothing.
    @Test
    void reveal_realFile_passesTheOsCommandToTheLauncher() {
        final List<List<String>> launched = new ArrayList<>();
        final PlatformFileRevealer revealer =
                new PlatformFileRevealer(new DirectExecutor(), launch(launched), () -> "Mac OS X");

        revealer.reveal(BOOK);

        assertThat(launched).containsExactly(List.of("open", "-R", BOOK.toString()));
    }

    // IF a failing launch escaped, THEN a missing file manager would turn a finished run into an error on the click.
    @Test
    void reveal_launcherThrows_doesNotThrow() {
        final PlatformFileRevealer revealer = new PlatformFileRevealer(
                new DirectExecutor(),
                command -> {
                    throw new IllegalStateException("no file manager");
                },
                () -> "Linux");

        assertThatCode(() -> revealer.reveal(BOOK)).doesNotThrowAnyException();
    }

    // IF a path with no parent were refused, THEN a book written to the filesystem root could not be shown.
    @Test
    void reveal_fileWithNoParent_stillLaunchesTheCommand() {
        final Path root = Path.of("").toAbsolutePath().getRoot();
        final List<List<String>> launched = new ArrayList<>();
        final PlatformFileRevealer revealer =
                new PlatformFileRevealer(new DirectExecutor(), launch(launched), () -> "Linux");

        revealer.reveal(root);

        assertThat(launched).containsExactly(List.of("xdg-open", root.toString()));
    }

    // IF a stopped pool's rejection escaped, THEN pressing the button while the app shuts down would throw.
    @Test
    void reveal_executorRejects_doesNotThrow() {
        final List<List<String>> launched = new ArrayList<>();
        final ExecutorService stopped = Executors.newSingleThreadExecutor();
        stopped.shutdown();
        final PlatformFileRevealer revealer = new PlatformFileRevealer(stopped, launch(launched), () -> "Linux");

        assertThatCode(() -> revealer.reveal(BOOK)).doesNotThrowAnyException();
        assertThat(launched).isEmpty();
    }

    // IF Explorer's habitual exit code 1 escaped as a failure, THEN Show would raise an error for a shown folder.
    @Test
    void reveal_explorerExitsOne_doesNotThrow() {
        final PlatformFileRevealer revealer =
                new PlatformFileRevealer(new DirectExecutor(), exiting(1), () -> "Windows 11");

        assertThatCode(() -> revealer.reveal(BOOK)).doesNotThrowAnyException();
    }

    // IF a failing exit code escaped, THEN a finished export would turn into an error on the click.
    @Test
    void reveal_commandExitsWithFailure_doesNotThrow() {
        final PlatformFileRevealer revealer =
                new PlatformFileRevealer(new DirectExecutor(), exiting(2), () -> "Mac OS X");

        assertThatCode(() -> revealer.reveal(BOOK)).doesNotThrowAnyException();
    }

    // IF Open book launched the reveal command, THEN it would select the file in Finder instead of opening it.
    @Test
    void open_realFile_passesTheOpenCommandToTheLauncher() {
        final List<List<String>> launched = new ArrayList<>();
        final PlatformFileRevealer revealer =
                new PlatformFileRevealer(new DirectExecutor(), launch(launched), () -> "Mac OS X");

        revealer.open(BOOK);

        assertThat(launched).containsExactly(List.of("open", BOOK.toString()));
    }

    // IF a failing launch escaped, THEN a missing default program would turn a finished export into an error.
    @Test
    void open_launcherThrows_doesNotThrow() {
        final PlatformFileRevealer revealer = new PlatformFileRevealer(
                new DirectExecutor(),
                command -> {
                    throw new IllegalStateException("no default program");
                },
                () -> "Linux");

        assertThatCode(() -> revealer.open(BOOK)).doesNotThrowAnyException();
    }
}
