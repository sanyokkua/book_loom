package ua.bookloom.pipeline.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Writes the call's own blocks of a translate-family prompt (design D8): block 11 from the repair and format re-ask
 * templates, block 12 from the translate template and block 13's numbered items, so the wording of each is the
 * catalogue's, never code.
 */
@Slf4j
final class CallBlocks {

    private final PromptTemplates templates;
    private final BlockWords words;

    CallBlocks(final PromptTemplates templates, final BlockWords words) {
        this.templates = templates;
        this.words = words;
    }

    String task(@Nullable final String source, final String target) {
        return templates
                .renderUser(PromptName.TRANSLATE, RunFrames.languageSlots(source, target))
                .strip();
    }

    // Block 11: a repair's previous attempt and defects, a re-ask's rejected reply and diagnosis, and a note.
    String kindExtra(final CallKind kind, final Focus focus, final KindExtra extra) {
        final List<String> parts = new ArrayList<>();
        if (kind == CallKind.REPAIR) {
            final List<String> defects = defects(focus);
            log.debug(
                    "Plan {}: {} defect lines, previous attempt {}",
                    kind,
                    defects.size(),
                    extra.previousAttempt() != null);
            if (!defects.isEmpty() || extra.previousAttempt() != null) {
                final Map<String, String> values = new HashMap<>();
                values.put("defects", String.join("\n", defects));
                values.put("previousAttempt", Objects.toString(extra.previousAttempt(), ""));
                parts.add(templates.renderUser(PromptName.REPAIR, values).strip());
            }
        }
        final @Nullable String rejected = extra.rejectedReply();
        if (kind == CallKind.STRUCTURAL_REPAIR && rejected != null) {
            log.debug("Plan {}: re-ask of a {}-character reply", kind, rejected.length());
            parts.add(templates
                    .renderUser(
                            PromptName.FORMAT_REASK, Map.of("rejectedReply", rejected, "diagnostic", extra.diagnosis()))
                    .strip());
        }
        if (!extra.note().isBlank()) {
            parts.add(words.get(BlockWords.NOTE_HEADING) + "\n" + extra.note().strip());
        }
        return String.join("\n\n", parts);
    }

    private List<String> defects(final Focus focus) {
        final List<String> lines = new ArrayList<>();
        final boolean one = focus.items().size() == 1;
        for (int index = 0; index < focus.items().size(); index++) {
            final String id = Integer.toString(index + 1);
            focus.reasons().getOrDefault(focus.items().get(index).segmentId(), List.of()).stream()
                    .filter(reason -> !reason.isBlank())
                    .forEach(reason -> lines.add(
                            one
                                    ? words.get(BlockWords.REPAIR_LINE_ONE).replace("{reason}", reason)
                                    : words.get(BlockWords.REPAIR_LINE)
                                            .replace("{id}", id)
                                            .replace("{reason}", reason)));
        }
        return lines;
    }

    String items(final Focus focus, final List<String> reminder) {
        final List<String> lines = new ArrayList<>();
        lines.add(words.get(BlockWords.ITEMS_OPEN));
        for (int index = 0; index < focus.items().size(); index++) {
            lines.add(words.get(BlockWords.ITEM)
                    .replace("{id}", Integer.toString(index + 1))
                    .replace("{text}", focus.items().get(index).maskedSource()));
        }
        lines.add(words.get(BlockWords.ITEMS_CLOSE));
        lines.addAll(reminder);
        return String.join("\n", lines);
    }
}
