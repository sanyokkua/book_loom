package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Injector;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.pipeline.JobReport;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.state.BookCard;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.ImportViewModel;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.theme.ThemeMode;

/**
 * UI-matches-mockup conformance: for each screen, under each value block, the parts named below paint with the role
 * the reference rendering gives them, and that role resolves to the published catalogue value.
 *
 * <p>One {@link Screen} entry per screen keeps a later screen task to a single added case: it names what to show,
 * and for each part the selector that finds it, whether the role fills its background or draws its border, the role,
 * and the light and dark values published for that role. The values are written out by hand from {@code theme.css}'s
 * published catalogue, never read back from the code under test. Every (screen, part, block) is its own test case, so
 * a failure names exactly one of them. Pixel placement is deliberately not asserted.
 */
class ScreenConformanceTest extends ShellTestBase {

    /** What a role is painted onto: the fill behind a part, or the line drawn around it. */
    private enum Kind {
        BACKGROUND,
        BORDER
    }

    /** A part of a screen: where to find it, what its role paints, and what the role must resolve to. */
    private record Part(String selector, Kind kind, String role, String lightHex, String darkHex) {

        String expected(final ThemeMode block) {
            return block == ThemeMode.DARK ? darkHex : lightHex;
        }
    }

    /** The dialog a screen has open over the shell, if any. */
    private enum Overlay {
        NONE,
        ABOUT,
        ERROR_DIALOG
    }

    /** What must have happened before the view is read: nothing, or a state reached through the import view model. */
    private enum Preparation {
        NONE,
        BOOK_OPENED,
        BOOK_REFUSED,
        LANGUAGE_MISMATCH,
        RUN_COMPLETED,
        RUN_PROVIDER_FAILED,
        BOOK_REPORTED
    }

    /**
     * What is on screen: the view to show (none for the bare shell), what was done to it first, and which dialog, if
     * any, is open over it.
     */
    record Screen(String name, @Nullable ViewNames view, Preparation preparation, Overlay overlay, List<Part> parts) {

        Screen(final String name, final @Nullable ViewNames view, final Overlay overlay, final List<Part> parts) {
            this(name, view, Preparation.NONE, overlay, parts);
        }
    }

    private final ScriptedDocumentPort documents = ScriptedDocumentPort.idle();

    @Override
    protected Injector createInjector(final Locale locale) {
        return UiTestInjector.create(locale, documents);
    }

    private static final Part CARD_FILL =
            new Part("#export-screen .card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part CARD_EDGE = new Part("#export-screen .card", Kind.BORDER, "border", "#ddd5c8", "#48585f");
    private static final Part BOX_FILL =
            new Part("#export-aux-glossary .box", Kind.BACKGROUND, "surface", "#ffffff", "#33424a");
    private static final Part BOX_EDGE =
            new Part("#export-aux-glossary .box", Kind.BORDER, "border-cool", "#cdd2d3", "#48585f");
    private static final Part SWITCH_TRACK =
            new Part("#export-aux-consistency .thumb-area", Kind.BACKGROUND, "border-cool", "#cdd2d3", "#48585f");

    /** One entry per screen; a later screen task adds exactly one line here. */
    static final List<Screen> SCREENS = List.of(
            new Screen(
                    "SHELL",
                    null,
                    Overlay.NONE,
                    List.of(
                            new Part("#shell-title-bar", Kind.BACKGROUND, "title-bg", "#324148", "#1c2429"),
                            new Part("#shell-nav", Kind.BACKGROUND, "nav-bg", "#3a4a52", "#20292e"),
                            new Part(".shell-toolbar", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part(".shell-toolbar", Kind.BORDER, "divider", "#eae4d8", "#3c4a51"))),
            new Screen(
                    "ABOUT_DIALOG",
                    null,
                    Overlay.ABOUT,
                    List.of(
                            new Part("#about-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#about-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "ERROR_DIALOG",
                    null,
                    Overlay.ERROR_DIALOG,
                    List.of(
                            new Part("#error-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#error-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "SETTINGS",
                    ViewNames.SETTINGS,
                    Overlay.NONE,
                    List.of(
                            new Part("#settings-provider-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#settings-provider-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#settings-model", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // The drop zone is the mockup's dashed sand box: sand-soft fill, sand-strong line.
            new Screen(
                    "IMPORT",
                    ViewNames.IMPORT,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-dropzone", Kind.BACKGROUND, "sand-soft", "#f2e9db", "#3a3a37"),
                            new Part("#import-dropzone", Kind.BORDER, "sand-strong", "#dcc4a3", "#6d5f4a"))),
            new Screen(
                    "IMPORT_DETECTED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#import-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "IMPORT_REFUSED",
                    ViewNames.IMPORT,
                    Preparation.BOOK_REFUSED,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-refusal", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#import-refusal", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            new Screen(
                    "IMPORT_LANGUAGE_MISMATCH",
                    ViewNames.IMPORT,
                    Preparation.LANGUAGE_MISMATCH,
                    Overlay.NONE,
                    List.of(
                            new Part("#import-mismatch", Kind.BACKGROUND, "warn-bg", "#f6ecd8", "#3a3327"),
                            new Part("#import-mismatch", Kind.BORDER, "warn-bd", "#e4cfa2", "#5c4d31"))),
            new Screen(
                    "BOOK_BRIEF",
                    ViewNames.BOOK_BRIEF,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#brief-languages-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#brief-languages-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "BOOK_BRIEF_NO_BOOK",
                    ViewNames.BOOK_BRIEF,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "STRUCTURE",
                    ViewNames.STRUCTURE,
                    Preparation.BOOK_OPENED,
                    Overlay.NONE,
                    List.of(
                            new Part("#structure-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#structure-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            new Screen(
                    "STRUCTURE_NO_BOOK",
                    ViewNames.STRUCTURE,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#nobook-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#nobook-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"))),
            // Ready to translate: the banner is the info role, the bar's track surface-2 and its fill the primary.
            new Screen(
                    "TRANSLATING",
                    ViewNames.TRANSLATING,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-progress-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-progress-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part(
                                    "#translating-progress .track", Kind.BACKGROUND, "surface-2", "#f6f1e8", "#2e3b41"),
                            new Part("#translating-progress .bar", Kind.BACKGROUND, "primary", "#a58075", "#c4917e"),
                            new Part("#translating-tile-accepted", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-tile-accepted", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#translating-log-card", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#translating-log-card", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#translating-banner", Kind.BACKGROUND, "info-bg", "#e5edf0", "#293940"),
                            new Part("#translating-banner", Kind.BORDER, "info-bd", "#b7cbd2", "#3d525b"))),
            new Screen(
                    "TRANSLATING_COMPLETED",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_COMPLETED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "ok-bg", "#e7efe8", "#2b3a33"),
                            new Part("#translating-banner", Kind.BORDER, "ok-bd", "#bcd4c1", "#3f5a49"))),
            // A provider failure is the run's own state: the banner takes the err role, light and dark.
            new Screen(
                    "TRANSLATING_PROVIDER_ERROR",
                    ViewNames.TRANSLATING,
                    Preparation.RUN_PROVIDER_FAILED,
                    Overlay.NONE,
                    List.of(
                            new Part("#translating-banner", Kind.BACKGROUND, "err-bg", "#f7e4df", "#3b2b28"),
                            new Part("#translating-banner", Kind.BORDER, "err-bd", "#e4b6ad", "#5e3f39"))),
            // The report screen: cards and count tiles on the surface role, the unavailable controls' boxes and track
            // on
            // the cool border role. Both states keep the right-hand "also export" cards, so both have a card.
            new Screen(
                    "EXPORT",
                    ViewNames.EXPORT,
                    Preparation.NONE,
                    Overlay.NONE,
                    List.of(CARD_FILL, CARD_EDGE, BOX_FILL, BOX_EDGE, SWITCH_TRACK)),
            new Screen(
                    "EXPORT_COMPLETED",
                    ViewNames.EXPORT,
                    Preparation.BOOK_REPORTED,
                    Overlay.NONE,
                    List.of(
                            CARD_FILL,
                            CARD_EDGE,
                            new Part("#export-accepted", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-accepted", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            new Part("#export-flagged", Kind.BACKGROUND, "surface", "#ffffff", "#33424a"),
                            new Part("#export-flagged", Kind.BORDER, "border", "#ddd5c8", "#48585f"),
                            BOX_FILL,
                            BOX_EDGE,
                            SWITCH_TRACK)));

    private static final long CONFORMANCE_WAIT_SECONDS = 10;

    static Stream<Arguments> partsInEachBlock() {
        return SCREENS.stream()
                .flatMap(screen -> screen.parts().stream()
                        .flatMap(part -> Stream.of(ThemeMode.LIGHT, ThemeMode.DARK)
                                .map(block -> Arguments.of(
                                        Named.of(screen.name(), screen),
                                        Named.of(
                                                part.kind() + " " + part.selector() + " (-color-" + part.role() + ")",
                                                part),
                                        block))));
    }

    private void show(final Screen screen) throws TimeoutException {
        final ViewNames view = screen.view();
        if (view != null) {
            onFx(() -> shell.activate(view));
        }
        switch (screen.preparation()) {
            case NONE -> {
                // nothing to do before the view is read
            }
            case BOOK_OPENED -> openABook();
            case BOOK_REFUSED -> refuseABook();
            case LANGUAGE_MISMATCH -> showMismatch();
            case RUN_COMPLETED -> completeARun();
            case RUN_PROVIDER_FAILED -> failARunWithAProviderError();
            case BOOK_REPORTED -> reportAFinishedBook();
        }
        switch (screen.overlay()) {
            case NONE -> {
                // nothing is open over the shell
            }
            case ABOUT -> onFx(() -> ((Button) required("shell-about")).fire());
            case ERROR_DIALOG ->
                onFx(() -> injector.getInstance(ErrorPresenter.class)
                        .present(AppError.of(
                                ErrorCode.timeout, "Translation failed", "The provider did not answer in time.")));
        }
    }

    private void openABook() throws TimeoutException {
        final Path source = Path.of("Frankenstein.epub");
        documents.on(source, Result.ok(BookFixtures.frankenstein()));
        openAndAwait(source, ImportState.Detected.class);
    }

    private void refuseABook() throws TimeoutException {
        final Path source = Path.of("secret.epub");
        documents.on(
                source,
                Result.err(AppError.of(ErrorCode.validation, "This book is protected", "The book is encrypted.")));
        openAndAwait(source, ImportState.Refused.class);
    }

    private void showMismatch() {
        final BookCard card =
                new BookCard("Frankenstein.epub", BookFormat.EPUB, "Frankenstein", "Mary Shelley", "en", 3, 9);
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        onFx(() -> viewModel.showLanguageMismatch(card, "uk"));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void completeARun() {
        final JobReport report =
                new JobReport(BookFormat.TXT, JobState.COMPLETED, 10, 10, 0, List.of(), Path.of("out.txt"), null);
        injector.getInstance(StateMirror.class).publishOutcome(RunState.COMPLETED, report, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void reportAFinishedBook() {
        final Path written = Path.of("/books/Frankenstein.uk.epub");
        final JobReport report =
                new JobReport(BookFormat.EPUB, JobState.COMPLETED, 1240, 1237, 3, List.of(), written, null);
        injector.getInstance(StateMirror.class).publishOutcome(RunState.COMPLETED, report, null);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void failARunWithAProviderError() {
        final AppError unreachable =
                AppError.of(ErrorCode.unreachable, "Unreachable", "Nothing is listening at http://localhost:11434.");
        injector.getInstance(StateMirror.class).publishOutcome(RunState.FAILED, null, unreachable);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void openAndAwait(final Path source, final Class<? extends ImportState> expected) throws TimeoutException {
        final ImportViewModel viewModel = injector.getInstance(ImportViewModel.class);
        onFx(() -> viewModel.open(source));
        WaitForAsyncUtils.waitFor(
                CONFORMANCE_WAIT_SECONDS,
                TimeUnit.SECONDS,
                () -> ThemeTestSupport.onFx(
                        () -> expected.isInstance(viewModel.state().get())));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Paint paintOf(final Node node, final Kind kind) {
        final Region region = (Region) node;
        return switch (kind) {
            case BACKGROUND -> {
                assertThat(region.getBackground())
                        .as("%s must paint a background", node)
                        .isNotNull();
                yield region.getBackground().getFills().get(0).getFill();
            }
            case BORDER -> {
                assertThat(region.getBorder()).as("%s must draw a border", node).isNotNull();
                yield region.getBorder().getStrokes().get(0).getBottomStroke();
            }
        };
    }

    // IF a part of the screen painted with another role, or a role resolved differently, in either value block, THEN
    // the screen would not match the reference rendering in that block.
    @ParameterizedTest(name = "{0}: {1} under {2}")
    @MethodSource("partsInEachBlock")
    void screen_part_underEachBlock_paintsWithThePublishedRoleValue(
            final Screen screen, final Part part, final ThemeMode block) throws TimeoutException {
        show(screen);
        onFx(() -> themeController.setMode(block));

        final Node node = scene.getRoot().lookup(part.selector());

        assertThat(node)
                .as("%s must exist on %s", part.selector(), screen.name())
                .isNotNull();
        ThemeTestSupport.assertSameColour(
                paintOf(node, part.kind()),
                part.expected(block),
                "%s %s (-color-%s) under %s".formatted(part.kind(), part.selector(), part.role(), block));
    }
}
