package ua.bookloom.ui.screen;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.CoverImage;
import ua.bookloom.ui.i18n.LanguageNames;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;
import ua.bookloom.ui.state.BookCard;
import ua.bookloom.ui.state.WordCount;

/**
 * What identifies an opened book, shown in the "About this book" fold: its cover or the words "No cover" beside the rows
 * the inspection could fill. Every count the translation depends on is in the facts beside it, so none is repeated
 * here except the chapters, which this section owns together with the words.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class ImportCardView {

    private static final double SECTION_SPACING = 6;
    private static final double CARD_SPACING = 16;
    private static final double COVER_WIDTH = 110;
    private static final double COVER_HEIGHT = 150;
    private static final String DOT = " · ";

    /** Rows only for what the inspection carries; a field the book did not declare gets no row at all. */
    static Node card(final Messages messages, final LanguageNames names, final BookCard card) {
        final VBox rows = new VBox(SECTION_SPACING);
        rows.getChildren().addAll(rowsOf(messages, names, card));
        HBox.setHgrow(rows, Priority.ALWAYS);
        final HBox box = new HBox(CARD_SPACING, cover(messages, card.cover()), rows);
        box.setId("import-card");
        box.setAlignment(Pos.TOP_LEFT);
        box.getStyleClass().add("import-details");
        return box;
    }

    /** The language named in English with its tag, {@code Latin (la)}, or the tag alone when nothing names it. */
    static String language(final Messages messages, final LanguageNames names, final String tag, final Locale locale) {
        final String name = names.nameOf(tag, locale);
        return name.equalsIgnoreCase(tag) ? tag : messages.get(MessageKey.IMPORT_LANGUAGE, name, tag);
    }

    /**
     * The format row's text. An EPUB inspection reports its version as {@code EPUB 2.0}, already naming the format, so
     * the name is written once; a bare version such as {@code 2.0} gets the name in front.
     *
     * @param format the format's display name
     * @param version the inspection's version string, or {@code null} when it reports none
     * @return the name and the version, the name never twice
     */
    static String formatText(final String format, final @Nullable String version) {
        if (version == null || version.isBlank()) {
            return format;
        }
        return version.regionMatches(true, 0, format, 0, format.length()) ? version : format + " " + version;
    }

    private static List<Node> rowsOf(final Messages messages, final LanguageNames names, final BookCard card) {
        final List<Node> rows = new ArrayList<>();
        final String version = card.formatVersion();
        final String format = messages.get(ImportViews.formatName(card.format()));
        rows.add(ImportViews.keyValue("import-row-file", messages.get(MessageKey.IMPORT_CARD_FILE), card.fileName()));
        rows.add(ImportViews.keyValue(
                "import-row-format", messages.get(MessageKey.IMPORT_CARD_FORMAT), formatText(format, version)));
        final String titleAuthor = titleAuthor(card);
        if (titleAuthor != null) {
            rows.add(ImportViews.keyValue(
                    "import-row-titleAuthor", messages.get(MessageKey.IMPORT_CARD_TITLE_AUTHOR), titleAuthor));
        }
        final String declared = card.declaredLanguage();
        if (declared != null) {
            rows.add(ImportViews.keyValue(
                    "import-row-declaredLang",
                    messages.get(MessageKey.IMPORT_CARD_LANGUAGE),
                    language(messages, names, declared, Locale.ENGLISH)));
        }
        rows.addAll(chaptersAndDrm(messages, card));
        return rows;
    }

    // A card exists only for a readable book, and the inspection names an encryption scheme only for a protected one,
    // so "none" is the true fact here and not a placeholder for a missing link.
    private static List<Node> chaptersAndDrm(final Messages messages, final BookCard card) {
        final Label none = new Label(messages.get(MessageKey.IMPORT_DRM_NONE));
        none.getStyleClass().addAll("chip", "chip-ok");
        return List.of(
                ImportViews.keyValue(
                        "import-row-chapters",
                        messages.get(MessageKey.IMPORT_CARD_CHAPTERS),
                        card.chapters() + DOT + WordCount.text(messages, card.words())),
                ImportViews.keyValueNode("import-row-drm", messages.get(MessageKey.IMPORT_CARD_DRM), none));
    }

    private static @Nullable String titleAuthor(final BookCard card) {
        final String joined = Stream.of(card.title(), card.author())
                .filter(part -> part != null)
                .reduce((first, second) -> first + DOT + second)
                .orElse("");
        return joined.isEmpty() ? null : joined;
    }

    /** The picture built from the cover's bytes in memory, or a neutral placeholder when there is none to draw. */
    private static Node cover(final Messages messages, final @Nullable CoverImage cover) {
        if (cover != null) {
            final Image image =
                    new Image(new ByteArrayInputStream(cover.bytes()), COVER_WIDTH, COVER_HEIGHT, true, true);
            if (!image.isError()) {
                final ImageView view = new ImageView(image);
                view.setId("import-cover");
                view.setPreserveRatio(true);
                view.setAccessibleText(messages.get(MessageKey.IMPORT_CARD_COVER));
                return view;
            }
            log.warn("the cover {} could not be decoded; the placeholder is shown", cover.resourcePath());
        }
        final Label placeholder = new Label(messages.get(MessageKey.IMPORT_ABOUT_NO_COVER));
        placeholder.setId("import-cover-none");
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().addAll("cover-placeholder", "muted");
        placeholder.setMinSize(COVER_WIDTH, COVER_HEIGHT);
        placeholder.setPrefSize(COVER_WIDTH, COVER_HEIGHT);
        placeholder.setMaxSize(COVER_WIDTH, COVER_HEIGHT);
        return placeholder;
    }
}
