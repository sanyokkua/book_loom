package ua.bookloom.document.model;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * Writes a restored multi-part value — one element per part, such as an FB2 author's {@code first-name} and
 * {@code last-name} — back into the parts it came from. The target is only read for what each part encloses: an
 * author's other children ({@code id}, {@code email}, {@code home-page}) are identifiers, not text, and are never
 * touched.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NameParts {

    /**
     * Sets the text of each of {@code owner}'s children that has a same-named part in {@code restoredMarkup}, taking
     * both in document order; a part the target lacks keeps its source text.
     *
     * @param owner the element holding the parts
     * @param restoredMarkup the restored target: the parts' markup with the translated text inside
     */
    static void write(TreeNode owner, String restoredMarkup) {
        final List<TreeNode> children = owner.childNodes();
        int cursor = 0;
        for (final Element part :
                Jsoup.parse(restoredMarkup, "", Parser.xmlParser()).children()) {
            final String name = part.normalName();
            while (cursor < children.size() && !name.equals(children.get(cursor).tagName())) {
                cursor++;
            }
            if (cursor == children.size()) {
                log.debug("name part {} has no source element left; skipped", name);
                return;
            }
            log.trace("name part {} written: {}", name, part.wholeText());
            children.get(cursor).setText(part.wholeText());
            cursor++;
        }
    }
}
