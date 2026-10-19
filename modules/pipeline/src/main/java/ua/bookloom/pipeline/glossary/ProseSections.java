package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.pipeline.checks.StoryMeasure;
import ua.bookloom.pipeline.index.BookIndex;
import ua.bookloom.pipeline.index.SegmentInfo;

/**
 * The segments of a book's prose sections (15i.N5 junk): the body is cut into sections at each unit and each heading
 * or title, and a section is prose when one of its paragraphs holds two full sentences ({@link StoryMeasure#hasProse}).
 * A book that keeps a converter's credit or a publisher's note in a section of its own body (a plain-text or FB2 book
 * has no front matter to put it in) writes it as short lines, so that name is written only outside every prose section.
 * Story chapters by {@link StoryMeasure#isStory} would be too coarse: a book's sections between sub-headings are often
 * fewer than twenty sentences.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProseSections {

    private static final Set<SegmentKind> SECTION_OPENINGS = Set.of(SegmentKind.HEADING, SegmentKind.TITLE);

    /**
     * The ordinals of every segment in a prose section, its heading included.
     *
     * @param index the book's index; never null
     * @return the ordinals; never null, empty when no section of the body is prose
     */
    static Set<Integer> of(final BookIndex index) {
        final List<Integer> body = index.story();
        final Map<Integer, String> texts = new HashMap<>();
        final List<String> visible = index.visibleTexts(body);
        for (int i = 0; i < body.size(); i++) {
            texts.put(body.get(i), visible.get(i));
        }
        final List<List<Integer>> sections = sectionsOf(index, body);
        final Set<Integer> prose = new HashSet<>();
        sections.stream()
                .filter(section -> StoryMeasure.hasProse(paragraphsOf(index, section, texts)))
                .forEach(prose::addAll);
        log.debug(
                "Prose sections: {} body segments in {} sections, {} in prose",
                body.size(),
                sections.size(),
                prose.size());
        return prose;
    }

    // A new section opens at each unit and each heading or title.
    private static List<List<Integer>> sectionsOf(final BookIndex index, final List<Integer> body) {
        final List<List<Integer>> sections = new ArrayList<>();
        @Nullable String unit = null;
        for (final int ordinal : body) {
            final SegmentInfo info = index.segment(ordinal);
            if (sections.isEmpty() || !Objects.equals(info.unitId(), unit) || SECTION_OPENINGS.contains(info.kind())) {
                sections.add(new ArrayList<>());
            }
            unit = info.unitId();
            sections.getLast().add(ordinal);
        }
        return sections;
    }

    private static List<String> paragraphsOf(
            final BookIndex index, final List<Integer> section, final Map<Integer, String> texts) {
        return section.stream()
                .filter(ordinal -> index.segment(ordinal).kind() == SegmentKind.PARAGRAPH)
                .map(ordinal -> Objects.requireNonNull(texts.get(ordinal), "text"))
                .toList();
    }
}
