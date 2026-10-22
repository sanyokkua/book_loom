package ua.bookloom.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.SegmentPreview;

/** What {@link ScriptedProjectService#segments} answers per unit, with optional holds, and a record of each call. */
final class ScriptedSegments {

    private final Map<String, Result<List<SegmentPreview>>> answers = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<Boolean> onFxThread = new CopyOnWriteArrayList<>();

    Result<List<SegmentPreview>> list(final String projectId, final String unitId) {
        calls.add(Objects.requireNonNull(projectId, "projectId") + "/" + Objects.requireNonNull(unitId, "unitId"));
        onFxThread.add(Platform.isFxApplicationThread());
        ScriptedProjects.awaitGate(gates.get(unitId));
        final Result<List<SegmentPreview>> scripted = answers.get(unitId);
        return scripted != null ? scripted : ScriptedProjects.notScripted();
    }

    void on(final String unitId, final Result<List<SegmentPreview>> answer) {
        answers.put(Objects.requireNonNull(unitId, "unitId"), Objects.requireNonNull(answer, "answer"));
    }

    void hold(final String unitId) {
        gates.put(unitId, new CountDownLatch(1));
    }

    void release(final String unitId) {
        final CountDownLatch held = gates.get(unitId);
        if (held != null) {
            held.countDown();
        }
    }

    List<String> calls() {
        return List.copyOf(calls);
    }

    List<Boolean> onFxThread() {
        return List.copyOf(onFxThread);
    }
}
