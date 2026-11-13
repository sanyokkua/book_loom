package ua.bookloom.ui.screen;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ExportOpenWork;
import ua.bookloom.api.pipeline.ProofreadTally;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.ExportFigures;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.Tone;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The Export screen's banners, at most one of each at a time: a translating run (with Pause, since an export waits for
 * it), a partial book (how much is translated; the rest keeps the original text), Proofread windows still unread,
 * segments waiting on a given fact, segments that will be written in the source language because their marks were
 * lost, segments still carrying a proofread mark, nothing translated yet. Each follows the view model; nothing is recomputed
 * here beyond which one applies.
 */
@Slf4j
final class ExportBanners {

    private static final double SPACING = 8;

    private final ExportViewModel exports;
    private final TranslatingViewModel translating;
    private final Messages messages;
    // Held here, so the screen keeps the banner's words alive exactly as long as its nodes.
    private final StringBinding partialWords;
    private final StringBinding fallbackWords;
    private final StringBinding proofreadWords;
    private final StringBinding waitingWords;
    private final StringBinding marksWords;

    ExportBanners(final ExportViewModel exports, final TranslatingViewModel translating, final Messages messages) {
        this.exports = Objects.requireNonNull(exports, "exports");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.partialWords = figureWords(
                figures -> messages.get(MessageKey.EXPORT_BANNER_PARTIAL, figures.translated(), figures.ofTotal()));
        this.fallbackWords = figureWords(figures -> messages.get(
                MessageKey.EXPORT_BANNER_SOURCE_FALLBACK, figures.counts().sourceFallback()));
        this.proofreadWords = Bindings.createStringBinding(
                () -> proofreadText(exports.proofreadLeft().get()), exports.proofreadLeft());
        this.waitingWords = figureWords(this::waitingText);
        this.marksWords = figureWords(figures ->
                messages.get(MessageKey.EXPORT_BANNER_OPEN_MARKS, figures.open().openMarks()));
    }

    private StringBinding figureWords(final Function<ExportFigures, String> words) {
        return Bindings.createStringBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures == null ? "" : words.apply(figures);
                },
                exports.figures());
    }

    private String proofreadText(final @Nullable ProofreadTally tally) {
        return tally == null
                ? ""
                : messages.get(MessageKey.EXPORT_BANNER_PROOFREAD_UNREAD, tally.read(), tally.windows());
    }

    private String waitingText(final ExportFigures figures) {
        final ExportOpenWork open = figures.open();
        final String facts = open.facts().stream()
                .map(name -> messages.get(MessageKey.EXPORT_BANNER_WAITING_FACT_GENDER, name))
                .collect(Collectors.joining(", "));
        return messages.get(MessageKey.EXPORT_BANNER_WAITING_FACT, open.waitingOnFact(), facts);
    }

    Node build() {
        final BooleanBinding running = Bindings.createBooleanBinding(
                () -> exports.blocker().get() == ActivityKind.TRANSLATION, exports.blocker());
        final VBox banners = new VBox(
                SPACING,
                runningBanner(running),
                partialBanner(running),
                proofreadBanner(running),
                waitingFactBanner(running),
                fallbackBanner(running),
                openMarksBanner(running),
                nothingBanner(running));
        banners.setId("export-banners");
        return banners;
    }

    private Banner runningBanner(final BooleanBinding running) {
        final Banner banner = Visibility.bind(
                new Banner(
                        "export-banner-running",
                        Tone.WARN,
                        ImportViews.WARNING_GLYPH,
                        messages.get(MessageKey.EXPORT_BANNER_RUNNING),
                        messages.get(MessageKey.EXPORT_BANNER_RUNNING_NOTE)),
                running);
        final Button pause = banner.addTrailingAction(
                "export-banner-pause", messages.get(MessageKey.EXPORT_BANNER_PAUSE), this::pause);
        Tips.install(messages, pause, MessageKey.EXPORT_BANNER_PAUSE_TIP);
        final ReadOnlyObjectProperty<Controls> controls = translating.controls();
        pause.disableProperty()
                .bind(Bindings.createBooleanBinding(
                        () -> !controls.get().pause().isEnabled(), controls));
        return banner;
    }

    private void pause() {
        log.debug("pause pressed on the export banner");
        translating.pause();
    }

    private Banner partialBanner(final BooleanBinding running) {
        final Banner banner = new Banner("export-banner-partial", Tone.INFO, "ℹ", "", "");
        final BooleanBinding partial = Bindings.createBooleanBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures != null && figures.isPartial() && !figures.isNothing();
                },
                exports.figures());
        partialWords.addListener((observed, was, now) -> banner.setTitle(now));
        banner.setTitle(partialWords.get());
        return Visibility.bind(banner, partial.and(running.not()));
    }

    // Marks lost in a segment make the export write its source text; saying so before Export book lets the person fix
    // it on the desk first.
    private Banner fallbackBanner(final BooleanBinding running) {
        final Banner banner = new Banner("export-banner-source-fallback", Tone.WARN, ImportViews.WARNING_GLYPH, "", "");
        final BooleanBinding fallback = Bindings.createBooleanBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures != null && figures.counts().sourceFallback() > 0;
                },
                exports.figures());
        fallbackWords.addListener((observed, was, now) -> banner.setTitle(now));
        banner.setTitle(fallbackWords.get());
        return Visibility.bind(banner, fallback.and(running.not()));
    }

    // The Proofread stage's windows still unread: the book is good, but the rest has not been read yet.
    private Banner proofreadBanner(final BooleanBinding running) {
        final BooleanBinding unread =
                Bindings.createBooleanBinding(() -> exports.proofreadLeft().get() != null, exports.proofreadLeft());
        return wordedBanner("export-banner-proofread", Tone.WARN, proofreadWords, unread.and(running.not()));
    }

    private Banner waitingFactBanner(final BooleanBinding running) {
        final BooleanBinding waiting = openWork(open -> open.waitingOnFact() > 0);
        return wordedBanner("export-banner-waiting-fact", Tone.WARN, waitingWords, waiting.and(running.not()));
    }

    private Banner openMarksBanner(final BooleanBinding running) {
        final BooleanBinding marks = openWork(open -> open.openMarks() > 0);
        return wordedBanner("export-banner-open-marks", Tone.INFO, marksWords, marks.and(running.not()));
    }

    private BooleanBinding openWork(final Predicate<ExportOpenWork> has) {
        return Bindings.createBooleanBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures != null && has.test(figures.open());
                },
                exports.figures());
    }

    private static Banner wordedBanner(
            final String id, final Tone tone, final StringBinding words, final BooleanBinding shown) {
        final Banner banner = new Banner(id, tone, tone == Tone.WARN ? ImportViews.WARNING_GLYPH : "ℹ", "", "");
        words.addListener((observed, was, now) -> banner.setTitle(now));
        banner.setTitle(words.get());
        return Visibility.bind(banner, shown);
    }

    private Banner nothingBanner(final BooleanBinding running) {
        final BooleanBinding nothing = Bindings.createBooleanBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures != null && figures.isNothing();
                },
                exports.figures());
        return Visibility.bind(
                new Banner(
                        "export-banner-nothing",
                        Tone.INFO,
                        "ℹ",
                        messages.get(MessageKey.EXPORT_BANNER_NOTHING),
                        messages.get(MessageKey.EXPORT_BANNER_NOTHING_NOTE)),
                nothing.and(running.not()));
    }
}
