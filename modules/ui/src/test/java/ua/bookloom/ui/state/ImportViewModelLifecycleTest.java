package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.ImportedBook;
import ua.bookloom.ui.BookFixtures;

/**
 * The life of one open on the import view model: which thread the port runs on, what a held open looks like from the
 * outside, what a second open does meanwhile, how a thrown port is answered, and when a replaced book is released.
 */
class ImportViewModelLifecycleTest extends ImportViewModelTestBase {

    @TempDir
    private Path dir;

    // IF the parse ran on the FX thread, THEN a large book would freeze the window while it opens.
    @Test
    void open_realExecutor_callsThePortOffTheFxThread() throws TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);
        awaitAnswered(viewModel);

        assertThat(projects.importCallsOnFxThread()).containsExactly(false);
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    // IF a held open did not show as in progress with the file's name, THEN the person could not tell the drop had
    // been accepted; and IF the answer were not published after release, THEN the screen would stay locked.
    @Test
    void open_portNotYetAnswered_isOpeningWithTheFileNameThenAnswers() throws InterruptedException, TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        projects.hold();
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);
        projects.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isOpening(viewModel)).isTrue();
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Opening("Frankenstein.epub"));
        assertThat(openedBook()).isNull();

        projects.release();
        awaitAnswered(viewModel);

        assertThat(isOpening(viewModel)).isFalse();
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    // IF a second drop while the first was still parsing started a second parse, THEN two books would race to be the
    // open one; the second request must be ignored, and the first must still complete.
    @Test
    void open_whileOpening_secondRequestIsIgnored() throws InterruptedException, TimeoutException {
        final Path first = dir.resolve("first.epub");
        final Path second = dir.resolve("second.epub");
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(second, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Other", "Someone", 1)));
        projects.hold();
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, first);
        projects.awaitEntered();

        onFx(() -> {
            viewModel.open(second);
            return null;
        });

        assertThat(projects.imports().size()).isEqualTo(1);
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Opening("first.epub"));
        projects.release();
        awaitAnswered(viewModel);

        assertThat(projects.imports()).containsExactly(first);
        assertThat(stateOf(viewModel))
                .isInstanceOfSatisfying(
                        ImportState.Detected.class,
                        detected -> assertThat(detected.card().fileName()).isEqualTo("first.epub"));
    }

    // IF a port that threw were let through, THEN the exception would cross the module edge; it must come back as an
    // internal error, go to the presenter, and leave the screen usable and not showing a stale book.
    @Test
    void open_portThrows_answeredAsInternalErrorAndScreenStaysUsable() {
        final Path source = dir.resolve("any.epub");
        projects.throwing(new IllegalStateException("boom-secret-detail"));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(errors.presented()).hasSize(1);
        final AppError presented = errors.presented().get(0);
        assertThat(presented.code()).isEqualTo(ErrorCode.internal);
        assertThat(String.valueOf(presented.details())).doesNotContain("boom-secret-detail");
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(openedBook()).isNull();
        assertThat(isOpening(viewModel)).isFalse();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF the first ever open tried to release a book, THEN the port would be asked to close something it never opened.
    @Test
    void open_firstBook_releasesNothing() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(projects.closedProjects()).isEmpty();
    }

    // IF opening a second book left the first retained, THEN every book opened in a session would stay in memory; the
    // screen must report the second and the port must be asked to close exactly the first.
    @Test
    void open_secondBook_replacesTheFirstAndReleasesIt() {
        final Path firstPath = dir.resolve("first.epub");
        final Path secondPath = dir.resolve("second.epub");
        final ImportedBook first = BookFixtures.frankensteinImport();
        final ImportedBook second = BookFixtures.imported("p2", BookFormat.EPUB, "fr", "Candide", "Voltaire", 1, 1);
        projects.on(firstPath, Result.ok(first));
        projects.on(secondPath, Result.ok(second));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, firstPath);

        open(viewModel, secondPath);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(
                        new BookCard("second.epub", BookFormat.EPUB, "Candide", "Voltaire", "fr", 2, 2)));
        assertThat(openedBook()).isNotNull().satisfies(book -> {
            assertThat(book.projectId()).isEqualTo("p2");
            assertThat(book.source()).isEqualTo(secondPath);
        });
        assertThat(projects.closedProjects()).containsExactly("p1");
    }

    // IF a refusal left the previous book open, THEN the screen would refuse a file while the brief screen still read
    // the old book; the previous book is released and no book is open after a refusal.
    @Test
    void open_refusalAfterABook_releasesThePreviousBookAndClearsTheOpenBook() {
        final Path goodPath = dir.resolve("Frankenstein.epub");
        final Path badPath = dir.resolve("secret.epub");
        final ImportedBook good = BookFixtures.frankensteinImport();
        projects.on(goodPath, Result.ok(good));
        projects.on(
                badPath,
                Result.err(AppError.of(ErrorCode.validation, "This book is protected", "The book is encrypted.")));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);

        open(viewModel, badPath);

        assertThat(projects.closedProjects()).containsExactly("p1");
        assertThat(openedBook()).isNull();
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Refused.class);
    }

    // IF an unexpected or busy failure discarded the book already open, THEN a transient fault would cost the person
    // their work; the book stays open, its state comes back, and nothing is released.
    @ParameterizedTest
    @EnumSource(
            value = ErrorCode.class,
            names = {"internal", "busy"})
    void open_internalOrBusyAfterABook_keepsThePreviousBookAndRestoresItsState(final ErrorCode code) {
        final Path goodPath = dir.resolve("Frankenstein.epub");
        final Path badPath = dir.resolve("flaky.epub");
        final AppError fault = AppError.of(code, "Could not open", "The book could not be opened.");
        projects.on(goodPath, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(badPath, Result.err(fault));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);
        final ImportState detected = stateOf(viewModel);
        final OpenedBook openBook = openedBook();

        open(viewModel, badPath);

        assertThat(stateOf(viewModel)).isEqualTo(detected).isInstanceOf(ImportState.Detected.class);
        assertThat(openedBook()).isSameAs(openBook).isNotNull();
        assertThat(projects.closedProjects()).isEmpty();
        assertThat(errors.presented()).containsExactly(fault);
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF a port that threw discarded the open book, THEN a defective adapter would wipe the person's work; it is
    // answered as an internal error like any other non-refusal.
    @Test
    void open_portThrowsAfterABook_keepsThePreviousBookAndRestoresItsState() {
        final Path goodPath = dir.resolve("Frankenstein.epub");
        projects.on(goodPath, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);
        final ImportState detected = stateOf(viewModel);
        final OpenedBook openBook = openedBook();
        projects.throwing(new IllegalStateException("boom"));

        open(viewModel, dir.resolve("other.epub"));

        assertThat(stateOf(viewModel)).isEqualTo(detected);
        assertThat(openedBook()).isSameAs(openBook);
        assertThat(projects.closedProjects()).isEmpty();
        assertThat(errors.presented()).hasSize(1);
    }

    // IF the release ran on the FX thread, THEN closing a large parsed book could stall the window.
    @Test
    void open_secondBookOnRealExecutor_releasesTheFirstOffTheFxThread() throws TimeoutException {
        final Path firstPath = dir.resolve("first.epub");
        final Path secondPath = dir.resolve("second.epub");
        projects.on(firstPath, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(secondPath, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, null, null, null, 1)));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, firstPath);
        awaitAnswered(viewModel);

        open(viewModel, secondPath);
        awaitAnswered(viewModel);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !projects.closeCallsOnFxThread().isEmpty());

        assertThat(projects.closeCallsOnFxThread()).containsExactly(false);
    }

    // IF Cancel left the project open, THEN every abandoned import would stay stored and the next screens would still
    // read it; Cancel releases exactly the open project, empties the holder and returns the screen to the drop zone.
    @Test
    void cancel_bookOpen_closesItsProjectEmptiesTheHolderAndReturnsToIdle() {
        final Path source = dir.resolve("Frankenstein.epub");
        projects.on(source, Result.ok(BookFixtures.frankensteinImport()));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, source);

        onFx(() -> {
            viewModel.cancel();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.closedProjects()).containsExactly("p1");
        assertThat(openedBook()).isNull();
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
    }

    // IF Cancel with nothing open asked the service to close something, THEN it would release a project that is not
    // there.
    @Test
    void cancel_nothingOpen_closesNothing() {
        final ImportViewModel viewModel = viewModel();

        onFx(() -> {
            viewModel.cancel();
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(projects.closedProjects()).isEmpty();
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
    }

    // IF Cancel during an unanswered import discarded the current book, THEN the answer would then open a second book
    // beside a project that was already released; it is ignored until the answer decides what is open.
    @Test
    void cancel_whileOpening_isIgnored() throws InterruptedException, TimeoutException {
        final Path first = dir.resolve("first.epub");
        final Path second = dir.resolve("second.epub");
        projects.on(first, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(second, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Other", "Someone", 1)));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, first);
        awaitAnswered(viewModel);
        projects.hold();
        open(viewModel, second);
        projects.awaitEntered();

        onFx(() -> {
            viewModel.cancel();
            return null;
        });

        assertThat(projects.closedProjects()).isEmpty();
        assertThat(openedBook()).isNotNull();
        projects.release();
        awaitAnswered(viewModel);
    }

    // IF the replaced project were closed before the new one was current, THEN a screen reading the holder in between
    // would find nothing open; the new project is current once the old one is released.
    @Test
    void open_secondBook_closesTheFirstProjectAndTheHolderReadsTheSecond() {
        final Path firstPath = dir.resolve("Frankenstein.epub");
        final Path secondPath = dir.resolve("Dracula.epub");
        projects.on(firstPath, Result.ok(BookFixtures.frankensteinImport()));
        projects.on(
                secondPath, Result.ok(BookFixtures.imported("p2", BookFormat.EPUB, "en", "Dracula", "Bram Stoker", 1)));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, firstPath);

        open(viewModel, secondPath);

        assertThat(projects.closedProjects()).containsExactly("p1");
        assertThat(openedBook()).extracting(OpenedBook::projectId).isEqualTo("p2");
    }
}
