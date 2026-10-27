package ua.bookloom.ui.screen;

import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.ViewNames;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.control.Tips;
import ua.bookloom.ui.control.Visibility;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ActivityKind;
import ua.bookloom.ui.state.Controls;
import ua.bookloom.ui.state.ExportFigures;
import ua.bookloom.ui.state.ExportViewModel;
import ua.bookloom.ui.state.HiddenWork;
import ua.bookloom.ui.state.Tone;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The Export screen's banners, at most one of each at a time: a translating run (with Pause, since an export waits for
 * it), a partial book (how much is translated; the rest keeps the original text), nothing translated yet, and the
 * export running in the background while the screen is read-only. Each follows the view model; nothing is recomputed
 * here beyond which one applies.
 */
@Slf4j
final class ExportBanners {

    private static final double SPACING = 8;

    private final ExportViewModel exports;
    private final TranslatingViewModel translating;
    private final HiddenWork hidden;
    private final Messages messages;
    // Held here, so the screen keeps the banner's words alive exactly as long as its nodes.
    private final StringBinding partialWords;

    ExportBanners(
            final ExportViewModel exports,
            final TranslatingViewModel translating,
            final HiddenWork hidden,
            final Messages messages) {
        this.exports = Objects.requireNonNull(exports, "exports");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.hidden = Objects.requireNonNull(hidden, "hidden");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.partialWords = Bindings.createStringBinding(
                () -> {
                    final ExportFigures figures = exports.figures().get();
                    return figures == null
                            ? ""
                            : messages.get(MessageKey.EXPORT_BANNER_PARTIAL, figures.translated(), figures.ofTotal());
                },
                exports.figures());
    }

    Node build() {
        final BooleanBinding running = Bindings.createBooleanBinding(
                () -> exports.blocker().get() == ActivityKind.TRANSLATION, exports.blocker());
        final VBox banners = new VBox(
                SPACING, runningBanner(running), partialBanner(running), nothingBanner(running), hiddenBanner());
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

    private Banner hiddenBanner() {
        return Visibility.bind(
                new Banner("export-banner-hidden", Tone.INFO, "ℹ", messages.get(MessageKey.EXPORT_HIDDEN_BANNER), ""),
                hidden.freezes(ViewNames.EXPORT));
    }
}
