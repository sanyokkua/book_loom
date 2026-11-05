package ua.bookloom.pipeline.context;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.CallKind;

/** The context builder's DEBUG and TRACE lines, kept apart so the builder holds the recipe alone. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PlanLog {

    static void planning(final CallKind kind, final FrameKind frameKind, final Window window, final CallInputs inputs) {
        log.debug(
                "Planning {} ({} frame) items={} previous={} contextSize={}",
                kind,
                frameKind,
                window.focus().items().size(),
                window.previous().size(),
                inputs.contextSize());
    }

    static void plan(final CallKind kind, final Focus focus, final PromptPlan plan) {
        log.debug(
                "Plan {} items={} blocks={} promptTokens={} maxOutputTokens={} contextSize={}",
                kind,
                focus.items().size(),
                plan.blocks().stream()
                        .map(block -> block.slot() + ":" + block.text().length())
                        .toList(),
                plan.promptTokens(),
                plan.maxOutputTokens(),
                plan.contextSize());
        if (log.isTraceEnabled()) {
            plan.blocks().forEach(block -> log.trace("Plan {} block {}: {}", kind, block.slot(), block.text()));
        }
    }
}
