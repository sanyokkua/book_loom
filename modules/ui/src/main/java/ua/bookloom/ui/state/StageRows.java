package ua.bookloom.ui.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.pipeline.NamesPassStage;
import ua.bookloom.api.pipeline.StepKind;

/**
 * The stage rows of work that plans stages: one row per stage, each carrying how many requests it sent. A request
 * belongs to the stage announced before it, since a stage runs its requests one after another; one announced before
 * any stage belongs to the first. A stage planned and not started is a row of its own, named by its place in the plan.
 *
 * @param rows the stages in order, started ones first then the planned ones; never null
 * @param lastRequest for each stage row's key, the key of the last request it sent; a stage that sent none has no entry
 */
public record StageRows(List<StepEntry> rows, Map<String, String> lastRequest) {

    /** Freezes both parts. */
    public StageRows {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        lastRequest = Map.copyOf(Objects.requireNonNull(lastRequest, "lastRequest"));
    }

    /**
     * Folds a log into its stage rows.
     *
     * @param log the steps the work announced
     * @return the rows, empty when the work planned no stage
     */
    public static StageRows of(final StepLog log) {
        Objects.requireNonNull(log, "log");
        if (log.stages() == 0) {
            return new StageRows(List.of(), Map.of());
        }
        final List<StepEntry> stages = log.entries().stream()
                .filter(entry -> entry.kind() == StepKind.STAGE)
                .toList();
        final int[] counts = new int[Math.max(stages.size(), 1)];
        final Map<String, String> last = new HashMap<>();
        int at = -1;
        for (final StepEntry entry : log.entries()) {
            if (entry.kind() == StepKind.STAGE) {
                at++;
            } else if (entry.kind() == StepKind.REQUEST && !stages.isEmpty()) {
                final int stage = Math.max(at, 0);
                counts[stage]++;
                last.put(stages.get(stage).key(), entry.key());
            }
        }
        final List<StepEntry> rows = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            rows.add(stages.get(i).withRequests(counts[i]));
        }
        for (int i = stages.size(); i < log.stages(); i++) {
            rows.add(pending(i, log.stages()).withRequests(0));
        }
        return new StageRows(rows, last);
    }

    // The full pass names its planned stages by place; a shorter plan is an action and then the write.
    private static StepEntry pending(final int index, final int planned) {
        final NamesPassStage[] all = NamesPassStage.values();
        final NamesPassStage stage = planned == all.length ? all[index] : NamesPassStage.WRITE;
        return new StepEntry(StepKind.STAGE, stage.code(), index, null, null, null, null);
    }
}
