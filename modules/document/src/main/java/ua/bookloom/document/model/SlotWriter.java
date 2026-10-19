package ua.bookloom.document.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

/** Writes one restored auxiliary target into the tree node its slot names, by the slot's kind. */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SlotWriter {

    static void write(AuxiliarySlots.Slot slot, String segmentId, String targetInner) {
        final TreeNode element = SkeletonAnchors.nodeAt(slot.root(), slot.path());
        log.trace("auxiliary slot {} written: {}", segmentId, targetInner);
        if (slot instanceof AuxiliarySlots.Slot.Markup) {
            element.replaceChildren(0, element.childNodes().size(), targetInner);
        } else if (slot instanceof AuxiliarySlots.Slot.Parts) {
            NameParts.write(element, targetInner);
        } else {
            element.setText(plainTextOf(targetInner));
        }
    }

    /**
     * The character data of restored markup: the restored target is markup, so a text slot decodes it and hands the
     * tree plain text to escape once on output — so an ampersand is never escaped twice.
     */
    private static String plainTextOf(String restoredMarkup) {
        return Jsoup.parseBodyFragment(restoredMarkup).body().wholeText();
    }
}
