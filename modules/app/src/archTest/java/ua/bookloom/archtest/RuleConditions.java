package ua.bookloom.archtest;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;
import java.util.Set;

/**
 * The three boundary rules that cannot be expressed with ArchUnit's fluent predicates alone.
 *
 * <p>All three are phrased <em>positively</em> — {@code classes().should(condition)} with the condition emitting
 * {@code violated} events. The alternative, {@code noClasses().should(condition)}, inverts the meaning of every
 * event a condition emits, which reads backwards at the exact moment someone is trying to understand why a build
 * went red.
 */
final class RuleConditions {

    /** Suffixes that mark a type as a concrete implementation rather than a port. */
    private static final Set<String> CONCRETE_SUFFIXES = Set.of("Impl", "Dao", "Service");

    private static final String SLF4J_PACKAGE = "org.slf4j";

    private RuleConditions() {
        // Condition factory.
    }

    // ===== dependency-direction ==========================================================================

    /** Every internal edge a class declares must appear in {@link ModuleGraph#ALLOWED_EDGES}. */
    static ArchCondition<JavaClass> onlyDependOnAllowedModules() {
        return new ArchCondition<JavaClass>("only depend on modules its allowed-edge set permits") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Optional<String> from = ModuleGraph.moduleOf(item);
                if (from.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    checkEdge(from.get(), dependency, events);
                }
            }
        };
    }

    private static void checkEdge(String from, Dependency dependency, ConditionEvents events) {
        Optional<String> to = ModuleGraph.moduleOf(dependency.getTargetClass());
        if (to.isEmpty() || ModuleGraph.isAllowedEdge(from, to.get())) {
            return;
        }
        events.add(SimpleConditionEvent.violated(
                dependency,
                ":%s -> :%s is not an allowed edge: %s".formatted(from, to.get(), dependency.getDescription())));
    }

    // ===== ports-not-concretes ===========================================================================

    /**
     * A cross-module dependency must target an {@code :api} port, never a concrete {@code ..Impl}/{@code ..Dao}/
     * {@code ..Service} living in the other module. Interfaces are exempt — a {@code DocumentService} interface
     * declared in {@code :api} is a port whose name happens to end in {@code Service}.
     */
    static ArchCondition<JavaClass> notDependOnConcreteTypesInOtherModules() {
        return new ArchCondition<JavaClass>("not depend on a concrete ..Impl/..Dao/..Service in another module") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                Optional<String> from = ModuleGraph.moduleOf(item);
                if (from.isEmpty()) {
                    return;
                }
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    checkConcreteTarget(from.get(), dependency, events);
                }
            }
        };
    }

    private static void checkConcreteTarget(String from, Dependency dependency, ConditionEvents events) {
        JavaClass target = dependency.getTargetClass();
        Optional<String> to = ModuleGraph.moduleOf(target);
        if (to.isEmpty() || to.get().equals(from) || target.isInterface()) {
            return;
        }
        if (CONCRETE_SUFFIXES.stream()
                .noneMatch(suffix -> target.getSimpleName().endsWith(suffix))) {
            return;
        }
        events.add(SimpleConditionEvent.violated(
                dependency,
                "%s is a concrete type in :%s, not an :api port: %s"
                        .formatted(target.getSimpleName(), to.get(), dependency.getDescription())));
    }

    // ===== bootstrap-no-static-logger ====================================================================

    /**
     * Pre-logging bootstrap classes may hold no static SLF4J field and may touch no SLF4J type from their static
     * initializer. Both halves matter: the field is the common form, but a {@code static { }} block calling
     * {@code LoggerFactory} pins the logging context just as hard while holding no field at all (DD-39,
     * ADR-0015).
     */
    static ArchCondition<JavaClass> notBindSlf4jAtClassInit() {
        return new ArchCondition<JavaClass>("hold no static org.slf4j field and touch no org.slf4j type at <clinit>") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                item.getFields().forEach(field -> checkStaticField(field, events));
                item.getStaticInitializer().ifPresent(initializer -> checkClassInit(initializer, events));
            }
        };
    }

    private static void checkStaticField(JavaField field, ConditionEvents events) {
        if (!field.getModifiers().contains(JavaModifier.STATIC) || !isSlf4j(field.getRawType())) {
            return;
        }
        events.add(SimpleConditionEvent.violated(
                field,
                "static field %s.%s is of SLF4J type %s and initialises before logging is configured"
                        .formatted(
                                field.getOwner().getSimpleName(),
                                field.getName(),
                                field.getRawType().getName())));
    }

    private static void checkClassInit(JavaCodeUnit initializer, ConditionEvents events) {
        for (JavaAccess<?> access : initializer.getAccessesFromSelf()) {
            if (!isSlf4j(access.getTargetOwner())) {
                continue;
            }
            events.add(SimpleConditionEvent.violated(
                    access,
                    "static initializer of %s touches SLF4J: %s"
                            .formatted(initializer.getOwner().getSimpleName(), access.getDescription())));
        }
    }

    private static boolean isSlf4j(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.equals(SLF4J_PACKAGE) || packageName.startsWith(SLF4J_PACKAGE + ".");
    }
}
