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
import ua.bookloom.api.document.Document;
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
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);
        awaitAnswered(viewModel);

        assertThat(port.openCallsOnFxThread()).containsExactly(false);
        assertThat(stateOf(viewModel)).isInstanceOf(ImportState.Detected.class);
    }

    // IF a held open did not show as in progress with the file's name, THEN the person could not tell the drop had
    // been accepted; and IF the answer were not published after release, THEN the screen would stay locked.
    @Test
    void open_portNotYetAnswered_isOpeningWithTheFileNameThenAnswers() throws InterruptedException, TimeoutException {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        port.hold();
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);
        port.awaitEntered();
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isOpening(viewModel)).isTrue();
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Opening("Frankenstein.epub"));
        assertThat(openedBookOf(viewModel)).isNull();

        port.release();
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
        port.on(first, Result.ok(BookFixtures.frankenstein()));
        port.on(second, Result.ok(BookFixtures.book("second", BookFormat.EPUB, "en", "Other", "Someone", 1)));
        port.hold();
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, first);
        port.awaitEntered();

        onFx(() -> {
            viewModel.open(second);
            return null;
        });

        assertThat(port.openCallCount()).isEqualTo(1);
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Opening("first.epub"));
        port.release();
        awaitAnswered(viewModel);

        assertThat(port.openedPaths()).containsExactly(first);
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
        port.throwing(new IllegalStateException("boom-secret-detail"));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(errors.presented()).hasSize(1);
        final AppError presented = errors.presented().get(0);
        assertThat(presented.code()).isEqualTo(ErrorCode.internal);
        assertThat(String.valueOf(presented.details())).doesNotContain("boom-secret-detail");
        assertThat(stateOf(viewModel)).isEqualTo(new ImportState.Idle());
        assertThat(openedBookOf(viewModel)).isNull();
        assertThat(isOpening(viewModel)).isFalse();
        assertThat(toasts.raised()).isEmpty();
    }

    // IF the first ever open tried to release a book, THEN the port would be asked to close something it never opened.
    @Test
    void open_firstBook_releasesNothing() {
        final Path source = dir.resolve("Frankenstein.epub");
        port.on(source, Result.ok(BookFixtures.frankenstein()));
        final ImportViewModel viewModel = viewModel();

        open(viewModel, source);

        assertThat(port.closedDocuments()).isEmpty();
    }

    // IF opening a second book left the first retained, THEN every book opened in a session would stay in memory; the
    // screen must report the second and the port must be asked to close exactly the first.
    @Test
    void open_secondBook_replacesTheFirstAndReleasesIt() {
        final Path firstPath = dir.resolve("first.epub");
        final Path secondPath = dir.resolve("second.epub");
        final Document first = BookFixtures.frankenstein();
        final Document second = BookFixtures.book("second", BookFormat.EPUB, "fr", "Candide", "Voltaire", 1, 1);
        port.on(firstPath, Result.ok(first));
        port.on(secondPath, Result.ok(second));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, firstPath);

        open(viewModel, secondPath);

        assertThat(stateOf(viewModel))
                .isEqualTo(new ImportState.Detected(
                        new BookCard("second.epub", BookFormat.EPUB, "Candide", "Voltaire", "fr", 2, 2)));
        assertThat(openedBookOf(viewModel)).isEqualTo(new OpenedBook(secondPath, second));
        assertThat(port.closedDocuments()).containsExactly(first);
    }

    // IF a refusal left the previous book open, THEN the screen would refuse a file while the brief screen still read
    // the old book; the previous book is released and no book is open after a refusal.
    @Test
    void open_refusalAfterABook_releasesThePreviousBookAndClearsTheOpenBook() {
        final Path goodPath = dir.resolve("Frankenstein.epub");
        final Path badPath = dir.resolve("secret.epub");
        final Document good = BookFixtures.frankenstein();
        port.on(goodPath, Result.ok(good));
        port.on(
                badPath,
                Result.err(AppError.of(ErrorCode.validation, "This book is protected", "The book is encrypted.")));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);

        open(viewModel, badPath);

        assertThat(port.closedDocuments()).containsExactly(good);
        assertThat(openedBookOf(viewModel)).isNull();
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
        port.on(goodPath, Result.ok(BookFixtures.frankenstein()));
        port.on(badPath, Result.err(fault));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);
        final ImportState detected = stateOf(viewModel);
        final OpenedBook openBook = openedBookOf(viewModel);

        open(viewModel, badPath);

        assertThat(stateOf(viewModel)).isEqualTo(detected).isInstanceOf(ImportState.Detected.class);
        assertThat(openedBookOf(viewModel)).isSameAs(openBook).isNotNull();
        assertThat(port.closedDocuments()).isEmpty();
        assertThat(errors.presented()).containsExactly(fault);
        assertThat(isOpening(viewModel)).isFalse();
    }

    // IF a port that threw discarded the open book, THEN a defective adapter would wipe the person's work; it is
    // answered as an internal error like any other non-refusal.
    @Test
    void open_portThrowsAfterABook_keepsThePreviousBookAndRestoresItsState() {
        final Path goodPath = dir.resolve("Frankenstein.epub");
        port.on(goodPath, Result.ok(BookFixtures.frankenstein()));
        final ImportViewModel viewModel = viewModel();
        open(viewModel, goodPath);
        final ImportState detected = stateOf(viewModel);
        final OpenedBook openBook = openedBookOf(viewModel);
        port.throwing(new IllegalStateException("boom"));

        open(viewModel, dir.resolve("other.epub"));

        assertThat(stateOf(viewModel)).isEqualTo(detected);
        assertThat(openedBookOf(viewModel)).isSameAs(openBook);
        assertThat(port.closedDocuments()).isEmpty();
        assertThat(errors.presented()).hasSize(1);
    }

    // IF the release ran on the FX thread, THEN closing a large parsed book could stall the window.
    @Test
    void open_secondBookOnRealExecutor_releasesTheFirstOffTheFxThread() throws TimeoutException {
        final Path firstPath = dir.resolve("first.epub");
        final Path secondPath = dir.resolve("second.epub");
        port.on(firstPath, Result.ok(BookFixtures.frankenstein()));
        port.on(secondPath, Result.ok(BookFixtures.book("second", BookFormat.EPUB, null, null, null, 1)));
        useBackgroundThread();
        final ImportViewModel viewModel = viewModel();
        open(viewModel, firstPath);
        awaitAnswered(viewModel);

        open(viewModel, secondPath);
        awaitAnswered(viewModel);
        WaitForAsyncUtils.waitFor(
                WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> !port.closeCallsOnFxThread().isEmpty());

        assertThat(port.closeCallsOnFxThread()).containsExactly(false);
    }
}
