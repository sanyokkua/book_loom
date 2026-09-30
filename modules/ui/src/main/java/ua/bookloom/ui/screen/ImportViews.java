package ua.bookloom.ui.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.control.Banner;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.ImportState;
import ua.bookloom.ui.state.LanguageWarning;

/**
 * The parts of the import screen whose number and wording depend on what was opened: the refusals and the language
 * warnings. They are built here, not in the FXML, because a row exists only when the book declared its value.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ImportViews {

    private static final double SECTION_SPACING = 6;
    private static final double BANNER_SPACING = 3;
    private static final double CARD_MAX_WIDTH = 720;
    private static final String ERROR_GLYPH = "⛔";
    static final String WARNING_GLYPH = "⚠";

    static Node progress(final Messages messages, final String fileName) {
        final Label progress = new Label(messages.get(MessageKey.IMPORT_PROGRESS, fileName));
        progress.setId("import-progress");
        progress.getStyleClass().add("muted");
        return progress;
    }

    /** A refusal names the file, says why in the error's own words and carries the typed code the port answered with. */
    static Node refusal(final Messages messages, final String fileName, final AppError error) {
        final Label code = new Label(error.code().name());
        code.setId("import-refusal-code");
        code.getStyleClass().addAll("chip", "chip-err");
        final Label file = new Label(messages.get(MessageKey.IMPORT_REFUSAL_FILE, fileName));
        file.getStyleClass().add("hint");
        final Banner banner =
                new Banner("import-refusal", Banner.Role.ERR, ERROR_GLYPH, error.title(), error.message());
        banner.addDetail(code);
        banner.addDetail(file);
        banner.setMaxWidth(CARD_MAX_WIDTH);
        return banner;
    }

    static Node warning(final Messages messages, final LanguageNames names, final LanguageWarning warning) {
        return switch (warning) {
            case LanguageWarning.Mismatch mismatch ->
                warningBanner(
                        "import-mismatch",
                        messages.get(MessageKey.IMPORT_MISMATCH_TITLE),
                        messages.get(
                                MessageKey.IMPORT_MISMATCH_TEXT,
                                ImportCardView.language(messages, names, mismatch.declared(), messages.locale()),
                                ImportCardView.language(messages, names, mismatch.content(), messages.locale())));
            case LanguageWarning.Unrecognized unrecognized ->
                warningBanner(
                        "import-unrecognized",
                        messages.get(MessageKey.IMPORT_UNRECOGNIZED_TITLE),
                        messages.get(MessageKey.IMPORT_UNRECOGNIZED_TEXT, unrecognized.rawCode()));
        };
    }

    private static Node warningBanner(final String id, final String title, final String text) {
        final Banner banner = new Banner(id, Banner.Role.WARN, WARNING_GLYPH, title, text);
        banner.setMaxWidth(CARD_MAX_WIDTH);
        return banner;
    }

    /** The encrypted book: an error banner, then what is known about the file, and only the way to another file. */
    static List<Node> drmBlocked(final Messages messages, final ImportState.DrmBlocked blocked) {
        final Banner banner = new Banner(
                "import-drm",
                Banner.Role.ERR,
                ERROR_GLYPH,
                messages.get(MessageKey.IMPORT_DRM_TITLE),
                messages.get(MessageKey.IMPORT_DRM_TEXT));
        banner.setMaxWidth(CARD_MAX_WIDTH);
        final List<Node> rows = new ArrayList<>();
        rows.add(keyValue("import-drm-file", messages.get(MessageKey.IMPORT_DRM_FILE), blocked.fileName()));
        final String scheme = blocked.scheme();
        if (scheme != null) {
            rows.add(keyValueNode("import-drm-scheme", messages.get(MessageKey.IMPORT_DRM_SCHEME), chip(scheme)));
        }
        rows.add(keyValue(
                "import-drm-status",
                messages.get(MessageKey.IMPORT_DRM_STATUS),
                messages.get(MessageKey.IMPORT_DRM_STATUS_VALUE)));
        rows.add(hint("import-drm-note", messages.get(MessageKey.IMPORT_DRM_NOTE)));
        return List.of(banner, blockedCard(rows));
    }

    /** The file that is none of the four formats: an error banner, the detected type and what is supported. */
    static List<Node> unsupported(final Messages messages, final ImportState.Unsupported unsupported) {
        final Banner banner = new Banner(
                "import-unsupported",
                Banner.Role.ERR,
                ERROR_GLYPH,
                messages.get(MessageKey.IMPORT_UNSUPPORTED_TITLE),
                messages.get(MessageKey.IMPORT_UNSUPPORTED_TEXT));
        banner.setMaxWidth(CARD_MAX_WIDTH);
        final Label type = new Label(unsupported.detectedType());
        type.setId("import-unsupported-detected");
        type.getStyleClass().add("kv-value");
        final Label badge = chip(messages.get(MessageKey.IMPORT_UNSUPPORTED_BADGE));
        badge.setId("import-unsupported-badge");
        badge.getStyleClass().add("chip-neutral");
        final HBox typeAndBadge = new HBox(SECTION_SPACING, type, badge);
        typeAndBadge.setAlignment(Pos.CENTER_LEFT);
        final List<Node> rows = List.of(
                keyValue(
                        "import-unsupported-file",
                        messages.get(MessageKey.IMPORT_UNSUPPORTED_FILE),
                        unsupported.fileName()),
                keyValueNode("import-unsupported-type", messages.get(MessageKey.IMPORT_UNSUPPORTED_TYPE), typeAndBadge),
                hint("import-unsupported-hint", messages.get(MessageKey.IMPORT_UNSUPPORTED_HINT)));
        return List.of(banner, blockedCard(rows));
    }

    private static Node blockedCard(final List<Node> rows) {
        final VBox box = new VBox(SECTION_SPACING);
        box.setId("import-blocked-card");
        box.getStyleClass().add("card");
        box.setMaxWidth(CARD_MAX_WIDTH);
        box.getChildren().addAll(rows);
        return box;
    }

    private static Label chip(final String text) {
        final Label chip = new Label(text);
        chip.getStyleClass().addAll("chip", "chip-err");
        return chip;
    }

    private static Label hint(final String id, final String text) {
        final Label hint = wrapped(text, "hint");
        hint.setId(id);
        return hint;
    }

    /** The language's name in the display language with its code, or the code alone when the JDK knows no name for it. */
    static String languageLabel(final Messages messages, final String code) {
        final String name = Locale.forLanguageTag(code).getDisplayLanguage(messages.locale());
        return name.isBlank() || name.equalsIgnoreCase(code)
                ? code
                : messages.get(MessageKey.IMPORT_LANGUAGE, name, code);
    }

    static Node banner(
            final String id, final String styleClass, final String glyph, final List<? extends Node> content) {
        final Label icon = new Label(glyph);
        icon.getStyleClass().add("banner-icon");
        final VBox text = new VBox(BANNER_SPACING);
        text.getChildren().addAll(content);
        final HBox banner = new HBox(icon, text);
        banner.setId(id);
        banner.setAlignment(Pos.TOP_LEFT);
        banner.getStyleClass().addAll("banner", styleClass);
        banner.setMaxWidth(CARD_MAX_WIDTH);
        return banner;
    }

    static Label wrapped(final String text, final String styleClass) {
        final Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** One key and its wrapped value on a ruled row, whose whole node id is {@code id}. */
    static Node keyValue(final String id, final String label, final String value) {
        final Label key = new Label(label);
        key.getStyleClass().add("kv-key");
        final Label shown = new Label(value);
        shown.getStyleClass().add("kv-value");
        shown.setWrapText(true);
        final HBox row = new HBox(key, shown);
        row.setId(id);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("kv");
        return row;
    }

    /** As {@link #keyValue} but the value is a node, such as a chip. */
    static Node keyValueNode(final String id, final String label, final Node value) {
        final Label key = new Label(label);
        key.getStyleClass().add("kv-key");
        final HBox row = new HBox(key, value);
        row.setId(id);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("kv");
        return row;
    }

    static MessageKey formatName(final BookFormat format) {
        return switch (format) {
            case EPUB -> MessageKey.IMPORT_FORMAT_EPUB;
            case FB2 -> MessageKey.IMPORT_FORMAT_FB2;
            case MARKDOWN -> MessageKey.IMPORT_FORMAT_MARKDOWN;
            case TXT -> MessageKey.IMPORT_FORMAT_TXT;
        };
    }
}
