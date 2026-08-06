package ua.bookloom.archtest;

import com.tngtech.archunit.core.domain.JavaClass;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The eight-module graph as data: which Gradle subproject a class belongs to, and which other subprojects that
 * one is allowed to depend on.
 *
 * <p>The table below is a transcription of
 * {@code docs/specification/02_Architecture/02_MODULES_AND_LAYERING.md#dependency-direction}. It is deliberately
 * the <em>edge set</em> rather than the four-layer model that section also sketches, because the layered form is
 * strictly weaker: layers Foundation({@code api},{@code util}) / Services / Orchestration / Presentation cannot
 * express {@code :ui} ↛ {@code :document} (both would be legal, Presentation above Services) nor {@code :api} ↛
 * {@code :util} (same layer, so both directions legal). The edge set forbids exactly what the table forbids, and
 * it subsumes {@code beFreeOfCycles()} — the table is a DAG, so a graph that satisfies it has no cycle to find.
 */
public final class ModuleGraph {

    /** Package prefix every BookLoom production class shares. */
    public static final String ROOT_PACKAGE = "ua.bookloom";

    /**
     * Allowed edges: {@code a → b} means "a class in {@code :a} may depend on a class in {@code :b}". Absence
     * from a module's set is a prohibition, not an omission.
     */
    public static final Map<String, Set<String>> ALLOWED_EDGES = Map.of(
            "api", Set.of(),
            "util", Set.of("api"),
            "document", Set.of("api", "util"),
            "llm", Set.of("api", "util"),
            "persistence", Set.of("api", "util"),
            "pipeline", Set.of("api", "util", "document", "llm", "persistence"),
            "ui", Set.of("api", "util", "pipeline"),
            "app", Set.of("api", "util", "document", "llm", "persistence", "pipeline", "ui"));

    /** All eight subprojects, in dependency order. */
    public static final List<String> ALL_MODULES =
            List.of("api", "util", "document", "llm", "pipeline", "persistence", "ui", "app");

    /**
     * The six modules that must stay JavaFX-free and run headless under JUnit/WireMock. Only {@code :ui} and
     * {@code :app} are absent.
     */
    public static final List<String> CORE_MODULES =
            List.of("api", "util", "document", "llm", "pipeline", "persistence");

    private ModuleGraph() {
        // Data holder.
    }

    /** Turns module names into the ArchUnit package identifiers that select them, e.g. {@code ua.bookloom.api..}. */
    public static String[] packagesOf(List<String> modules) {
        return modules.stream()
                .map(module -> ROOT_PACKAGE + "." + module + "..")
                .toArray(String[]::new);
    }

    /**
     * The subproject a class belongs to, derived from the third package segment. Empty for anything outside
     * {@code ua.bookloom.<one of the eight>} — third-party types, the JDK, and this suite's own
     * {@code ua.bookloom.archtest} package all fall through here rather than being mis-attributed to a module.
     */
    public static Optional<String> moduleOf(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        String prefix = ROOT_PACKAGE + ".";
        if (!packageName.startsWith(prefix)) {
            return Optional.empty();
        }
        String remainder = packageName.substring(prefix.length());
        int firstDot = remainder.indexOf('.');
        String module = firstDot < 0 ? remainder : remainder.substring(0, firstDot);
        return ALLOWED_EDGES.containsKey(module) ? Optional.of(module) : Optional.empty();
    }

    /** Whether {@code from} may depend on {@code to}. A module always may depend on itself. */
    public static boolean isAllowedEdge(String from, String to) {
        return from.equals(to) || ALLOWED_EDGES.getOrDefault(from, Set.of()).contains(to);
    }
}
