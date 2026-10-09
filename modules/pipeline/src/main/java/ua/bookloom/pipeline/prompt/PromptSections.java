package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.PromptSection;

/**
 * Splits a template into the parts a person can name, with the same block rule {@link PromptTemplates} renders by: a
 * block whose slot is empty is not sent and has no part. A part is an optional block, headed by its first line when
 * that line holds no slot; or a slot standing alone on its line, headed by the line above when that line holds no
 * slot; or a slot inside a line right under a {@code [heading]}, such as the immutable-token line. A slot inside a
 * sentence with no heading of its own (the language names) is part of the fixed wording and has no part.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PromptSections {

    private static final Pattern STANDALONE = Pattern.compile("^\\{\\{(\\w+)}}$");

    static List<PromptSection> of(
            final String text, final Map<String, String> values, final PromptSection.Origin origin) {
        final List<PromptSection> sections = new ArrayList<>();
        final Matcher block = PromptTemplates.BLOCK.matcher(text);
        int from = 0;
        while (block.find()) {
            plain(text.substring(from, block.start()), values, origin, sections);
            final String slot = block.group(1);
            if (!values.getOrDefault(slot, "").isEmpty() && !PromptTemplates.isRuleGate(slot)) {
                sections.add(blockSection(slot, block.group(2), values, origin));
            }
            from = block.end();
        }
        plain(text.substring(from), values, origin, sections);
        return List.copyOf(sections);
    }

    private static PromptSection blockSection(
            final String slot, final String body, final Map<String, String> values, final PromptSection.Origin origin) {
        final List<String> lines = body.lines().toList();
        final boolean headed = !lines.isEmpty()
                && !PromptTemplates.SLOT.matcher(lines.getFirst()).find();
        final List<String> rest = headed ? lines.subList(1, lines.size()) : lines;
        return new PromptSection(
                slot,
                headed ? lines.getFirst() : "",
                origin,
                sent(PromptTemplates.fill(String.join("\n", rest), values)));
    }

    private static void plain(
            final String part,
            final Map<String, String> values,
            final PromptSection.Origin origin,
            final List<PromptSection> sections) {
        final List<String> lines = part.lines().toList();
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final String above = index == 0 ? "" : lines.get(index - 1);
            final boolean isHeading =
                    !above.isBlank() && !PromptTemplates.SLOT.matcher(above).find();
            final Matcher alone = STANDALONE.matcher(line);
            final Matcher inside = PromptTemplates.SLOT.matcher(line);
            if (alone.matches()) {
                add(
                        sections,
                        alone.group(1),
                        isHeading ? above : "",
                        origin,
                        values,
                        values.getOrDefault(alone.group(1), ""));
            } else if (isHeading && above.startsWith("[") && inside.find()) {
                add(sections, inside.group(1), above, origin, values, PromptTemplates.fill(line, values));
            }
        }
    }

    private static void add(
            final List<PromptSection> sections,
            final String slot,
            final String heading,
            final PromptSection.Origin origin,
            final Map<String, String> values,
            final String shown) {
        if (!values.getOrDefault(slot, "").isEmpty()) {
            sections.add(new PromptSection(slot, heading, origin, sent(shown)));
        }
    }

    // The lines as the model read them, without the blank lines that only separate one part from the next.
    private static List<String> sent(final String text) {
        final List<String> lines = text.lines().toList();
        int first = 0;
        int last = lines.size();
        while (first < last && lines.get(first).isBlank()) {
            first++;
        }
        while (last > first && lines.get(last - 1).isBlank()) {
            last--;
        }
        return lines.subList(first, last);
    }
}
