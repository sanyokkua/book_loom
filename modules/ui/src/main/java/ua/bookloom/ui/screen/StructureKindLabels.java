package ua.bookloom.ui.screen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.ui.i18n.MessageKey;

/** The wording of each segment kind on the structure screen. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs, so it
// cannot see the private constructor @NoArgsConstructor generates (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StructureKindLabels {

    static MessageKey of(final SegmentKind kind) {
        return switch (kind) {
            case PARAGRAPH -> MessageKey.STRUCTURE_KIND_PARAGRAPH;
            case HEADING -> MessageKey.STRUCTURE_KIND_HEADING;
            case VERSE_LINE -> MessageKey.STRUCTURE_KIND_VERSE_LINE;
            case LIST_ITEM -> MessageKey.STRUCTURE_KIND_LIST_ITEM;
            case TABLE_CELL -> MessageKey.STRUCTURE_KIND_TABLE_CELL;
            case FOOTNOTE -> MessageKey.STRUCTURE_KIND_FOOTNOTE;
            case CAPTION -> MessageKey.STRUCTURE_KIND_CAPTION;
            case TITLE -> MessageKey.STRUCTURE_KIND_TITLE;
            case METADATA_TITLE -> MessageKey.STRUCTURE_KIND_METADATA_TITLE;
            case METADATA_AUTHOR -> MessageKey.STRUCTURE_KIND_METADATA_AUTHOR;
            case METADATA_DESCRIPTION -> MessageKey.STRUCTURE_KIND_METADATA_DESCRIPTION;
            case FRONTMATTER_VALUE -> MessageKey.STRUCTURE_KIND_FRONTMATTER_VALUE;
            case ALT -> MessageKey.STRUCTURE_KIND_ALT;
            case NAV_LABEL -> MessageKey.STRUCTURE_KIND_NAV_LABEL;
        };
    }
}
