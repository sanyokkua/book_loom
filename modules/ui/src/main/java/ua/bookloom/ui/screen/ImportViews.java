package ua.bookloom.ui.screen;

import java.util.List;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookCard;

/**
 * The parts of the import screen whose number and wording depend on what was opened: the card, the refusal and the
 * mismatch warning. They are built here, not in the FXML, because a row exists only when the book declared its value.
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

    /** Rows only for what the parse carries; a field the book did not declare gets no row at all. */
    static Node card(final Messages messages, final BookCard card) {
        final VBox box = new VBox(SECTION_SPACING);
        box.setId("import-card");
        box.getStyleClass().add("card");
        box.setMaxWidth(CARD_MAX_WIDTH);
        final Label heading = new Label(messages.get(MessageKey.IMPORT_CARD_TITLE));
        heading.getStyleClass().add("card-title");
        box.getChildren().add(heading);
        addRows(box, messages, card);
        return box;
    }

    private static void addRows(final VBox box, final Messages messages, final BookCard card) {
        final String declared = card.declaredLang();
        box.getChildren().add(row("file", messages.get(MessageKey.IMPORT_CARD_FILE), card.fileName()));
        box.getChildren()
                .add(row(
                        "format",
                        messages.get(MessageKey.IMPORT_CARD_FORMAT),
                        messages.get(formatName(card.format()))));
        addIfDeclared(box, "title", messages.get(MessageKey.IMPORT_CARD_TITLE_ROW), card.title());
        addIfDeclared(box, "author", messages.get(MessageKey.IMPORT_CARD_AUTHOR), card.author());
        addIfDeclared(
                box,
                "declaredLang",
                messages.get(MessageKey.IMPORT_CARD_LANGUAGE),
                declared == null ? null : languageLabel(messages, declared));
        box.getChildren()
                .add(row(
                        "units",
                        messages.get(MessageKey.IMPORT_CARD_UNITS),
                        messages.get(MessageKey.IMPORT_COUNT_UNITS, card.unitCount())));
        box.getChildren()
                .add(row(
                        "segments",
                        messages.get(MessageKey.IMPORT_CARD_SEGMENTS),
                        messages.get(MessageKey.IMPORT_COUNT_SEGMENTS, card.segmentCount())));
    }

    /** A refusal names the file, says why in the error's own words and carries the typed code the port answered with. */
    static Node refusal(final Messages messages, final String fileName, final AppError error) {
        final Label code = new Label(error.code().name());
        code.setId("import-refusal-code");
        code.getStyleClass().addAll("chip", "chip-err");
        final Label file = new Label(messages.get(MessageKey.IMPORT_REFUSAL_FILE, fileName));
        file.getStyleClass().add("hint");
        return banner(
                "import-refusal",
                "banner-err",
                ERROR_GLYPH,
                List.of(wrapped(error.title(), "banner-title"), wrapped(error.message(), "banner-text"), code, file));
    }

    static Node mismatch(final Messages messages, final String declaredLang, final String detectedLang) {
        return banner(
                "import-mismatch",
                "banner-warn",
                WARNING_GLYPH,
                List.of(
                        wrapped(messages.get(MessageKey.IMPORT_MISMATCH_TITLE), "banner-title"),
                        wrapped(
                                messages.get(
                                        MessageKey.IMPORT_MISMATCH_TEXT,
                                        languageLabel(messages, declaredLang),
                                        languageLabel(messages, detectedLang)),
                                "banner-text")));
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

    private static void addIfDeclared(
            final VBox box, final String id, final String label, final @Nullable String value) {
        if (value != null) {
            box.getChildren().add(row(id, label, value));
        }
    }

    private static Node row(final String id, final String label, final String value) {
        return keyValue("import-row-" + id, label, value);
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

    static MessageKey formatName(final BookFormat format) {
        return switch (format) {
            case EPUB -> MessageKey.IMPORT_FORMAT_EPUB;
            case FB2 -> MessageKey.IMPORT_FORMAT_FB2;
            case MARKDOWN -> MessageKey.IMPORT_FORMAT_MARKDOWN;
            case TXT -> MessageKey.IMPORT_FORMAT_TXT;
        };
    }
}
