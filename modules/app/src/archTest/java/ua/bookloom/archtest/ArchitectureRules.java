package ua.bookloom.archtest;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The nine boundary rules of {@code 02_Architecture/02_MODULES_AND_LAYERING.md#archunit-rules}, declared once
 * for the whole module graph.
 *
 * <p>Each rule's description begins with its spec name followed by {@value #NAME_SEPARATOR}. That is not
 * decoration: {@link RuleSuite} parses it, so a rule whose name drifts from the spec's list fails the
 * completeness test rather than quietly becoming a rule nobody can find.
 *
 * <p>Every rule carries {@code allowEmptyShould(true)}. On this tree — eight modules holding one placeholder
 * type each — several rules genuinely match zero classes, and ArchUnit's default would fail them for it. The
 * protection against a rule that matches nothing <em>because it is mis-scoped</em> is not this flag but
 * {@code RuleViolationFixtureTest}, which feeds every rule an input it must reject.
 */
public final class ArchitectureRules {

    /** Separates a rule's spec name from its prose in {@link ArchRule#getDescription()}. */
    public static final String NAME_SEPARATOR = " — ";

    private static final String ROOT = ModuleGraph.ROOT_PACKAGE;

    /**
     * Carrier packages for {@code records-first}, per the spec's wording ({@code ..dto}, {@code ..api..}). Any
     * package with an {@code api} or {@code dto} segment holds data crossing a boundary.
     */
    private static final String[] CARRIER_PACKAGES = {"..dto..", "..api.."};

    /**
     * The pre-logging bootstrap path, identified by <strong>package</strong> rather than by class name.
     *
     * <p>The rule cannot cover the whole of {@code ua.bookloom.app}: everything there other than the startup path
     * runs after logging is configured and may log freely. It previously narrowed that scope by matching simple
     * names — {@code Launcher}, or anything containing {@code Lock} — and that was a trap. The class most able to
     * make the mistake this rule exists to prevent is the one whose entire job is to configure Logback, and no
     * natural name for it ({@code LoggingBootstrap}, {@code LogSetup}) matches either pattern. It would have been
     * free to declare a static logger, in the one place where doing so silently misdirects every log file the
     * application ever writes.
     *
     * <p>Widening the name predicate would have relocated the coupling rather than removed it: the class's name
     * would still be load-bearing, in two places, and the next pre-logging class would arrive unprotected until
     * somebody remembered to edit the pattern again. A package is a membership a developer chooses deliberately,
     * it is visible in the import, and anything added to it is covered with no edit here at all.
     */
    private static final DescribedPredicate<JavaClass> ON_BOOTSTRAP_PATH = resideInAPackage(ROOT + ".util.paths..")
            .or(resideInAPackage(ROOT + ".app.bootstrap.."))
            .as("on the pre-logging bootstrap path");

    private ArchitectureRules() {
        // Rule holder.
    }

    // ===== 1. fx-free-core ===============================================================================

    /** Rule 1: the six core modules must be compilable and runnable without a JavaFX toolkit. */
    public static final ArchRule FX_FREE_CORE = noClasses()
            .that()
            .resideInAnyPackage(ModuleGraph.packagesOf(ModuleGraph.CORE_MODULES))
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("javafx..")
            .as("fx-free-core" + NAME_SEPARATOR
                    + "no class in :api :util :document :llm :pipeline :persistence may depend on javafx..")
            .because("the core runs headless under JUnit and WireMock; a JavaFX dependency anywhere in it would "
                    + "make the business logic untestable without a toolkit (DD-06)")
            .allowEmptyShould(true);

    // ===== 2. dependency-direction =======================================================================

    /** Rule 2: the internal graph is exactly the allowed-edge table, and therefore a DAG pointing at {@code :api}. */
    public static final ArchRule DEPENDENCY_DIRECTION = classes()
            .that()
            .resideInAPackage(ROOT + "..")
            .should(RuleConditions.onlyDependOnAllowedModules())
            .as("dependency-direction" + NAME_SEPARATOR
                    + "every internal edge must appear in the allowed-edge table (:app -> all; :ui -> :pipeline "
                    + ":api :util; :pipeline -> :document :llm :persistence :api :util; "
                    + ":document/:llm/:persistence -> :api :util; :util -> :api; :api -> nothing)")
            .because("an inward-pointing acyclic graph is what lets any collaborator be mocked at :api, and what "
                    + "keeps :api free of everything above it (02_MODULES_AND_LAYERING.md#dependency-direction)")
            .allowEmptyShould(true);

    // ===== 3. ports-not-concretes ========================================================================

    /** Rule 3: {@code :ui} and {@code :pipeline} hold ports, never another module's implementation classes. */
    public static final ArchRule PORTS_NOT_CONCRETES = classes()
            .that()
            .resideInAnyPackage(ROOT + ".ui..", ROOT + ".pipeline..")
            .should(RuleConditions.notDependOnConcreteTypesInOtherModules())
            .as("ports-not-concretes" + NAME_SEPARATOR
                    + "no class in :ui or :pipeline may depend on a concrete ..Impl/..Dao/..Service residing in a "
                    + "different module; cross-module dependencies target :api interfaces")
            .because("ports are what make every collaborator mockable at the :api seam, and what keep the "
                    + "dependency graph aimed at :api rather than at whichever module happens to implement it")
            .allowEmptyShould(true);

    // ===== 4. no-http-in-core-except-llm =================================================================

    /**
     * Rule 4: exactly one module may open a socket. This is the structural seed of the offline invariant (F9,
     * DD-01) — the spec's wording is unqualified ("only {@code :llm} may depend on {@code java.net.http..}"), so
     * the scope here is every module except {@code :llm}, not merely the six core ones the rule name mentions.
     */
    public static final ArchRule NO_HTTP_IN_CORE_EXCEPT_LLM = noClasses()
            .that()
            .resideInAPackage(ROOT + "..")
            .and()
            .resideOutsideOfPackage(ROOT + ".llm..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("java.net.http..")
            .as("no-http-in-core-except-llm" + NAME_SEPARATOR + "only :llm may depend on java.net.http..")
            .because("\"only one module can open a socket\" must be a compile-gate fact rather than a promise; "
                    + "the sole outbound path is user-triggered provider communication from :llm (F9, DD-01)")
            .allowEmptyShould(true);

    // ===== 5. no-sql-in-core-except-persistence ==========================================================

    /** Rule 5: SQL, JDBI and Flyway are {@code :persistence} internals and reach no other module. */
    public static final ArchRule NO_SQL_IN_CORE_EXCEPT_PERSISTENCE = noClasses()
            .that()
            .resideInAPackage(ROOT + "..")
            .and()
            .resideOutsideOfPackage(ROOT + ".persistence..")
            .should()
            .dependOnClassesThat()
            // `javax.sql` carries `DataSource`, which is the same concern wearing a different package name.
            .resideInAnyPackage("java.sql..", "javax.sql..", "org.jdbi..", "org.flywaydb..")
            .as("no-sql-in-core-except-persistence" + NAME_SEPARATOR
                    + "only :persistence may depend on java.sql.., javax.sql.., org.jdbi.. or org.flywaydb..")
            .because("the storage engine is an implementation detail behind the repository ports; a caller that "
                    + "reaches for a Connection has bypassed the seam that makes storage swappable and testable")
            .allowEmptyShould(true);

    // ===== 6. api-is-framework-free ======================================================================

    /**
     * Rule 6: the dependency floor stays framework-free. Scoped to {@code :api} alone — the service impl modules
     * legitimately {@code requires com.google.guice} because each owns a Guice {@code Module}; that is the
     * intended boundary, and only {@code :api} is forbidden the import.
     */
    public static final ArchRule API_IS_FRAMEWORK_FREE = noClasses()
            .that()
            .resideInAPackage(ROOT + ".api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.google.inject..",
                    "jakarta.inject..",
                    "javax.inject..",
                    "com.fasterxml.jackson..",
                    "javafx..",
                    "org.jdbi..",
                    "org.flywaydb..",
                    "org.jdom2..",
                    "org.dom4j..",
                    "org.jsoup..",
                    "org.commonmark..",
                    "com.ibm.icu..",
                    "com.github.pemistahl.lingua..",
                    "ch.qos.logback..")
            .as("api-is-framework-free" + NAME_SEPARATOR
                    + "no class in :api may depend on Guice, Jackson, JavaFX, JDBI, Flyway, a parser library, "
                    + "ICU4J, Lingua or Logback")
            .because(":api is the floor all seven other modules point at, so a framework admitted here propagates "
                    + "to every one of them and can never be removed from just one place")
            .allowEmptyShould(true);

    // ===== 7. records-first ==============================================================================

    /**
     * Rule 7: data carriers are records. Scoped to carrier packages ONLY. Service classes are deliberately out
     * of scope — Lombok {@code @RequiredArgsConstructor}/{@code @Slf4j}/{@code @Builder} on a service is the
     * accepted hybrid (DD-05, ADR-0014), and a rule that flagged it would be arguing with a decision already
     * taken.
     *
     * <p>The exclusions are the spec's "excluding declared exceptions", widened by what a carrier package
     * legitimately also holds: ports are interfaces, {@code ErrorCode} is an enum, and {@code package-info}
     * compiles to an interface too.
     */
    public static final ArchRule RECORDS_FIRST = classes()
            .that()
            .resideInAnyPackage(CARRIER_PACKAGES)
            .and()
            .areNotInterfaces()
            .and()
            .areNotEnums()
            .and()
            .areNotAssignableTo(Throwable.class)
            .and()
            .areTopLevelClasses()
            .should()
            .beRecords()
            .as("records-first" + NAME_SEPARATOR
                    + "every non-interface, non-enum, non-exception top-level type in a ..dto or ..api package "
                    + "must be a record, never a Lombok @Data/@Value class")
            .because("value semantics, immutability and pattern-matching come free with records, and a mutable "
                    + "carrier crossing a module boundary is aliasable state the receiver cannot trust (DD-05)")
            .allowEmptyShould(true);

    // ===== 8. bootstrap-no-static-logger =================================================================

    /**
     * Rule 8: the two packages whose classes run before Logback is configured.
     *
     * <p>Membership is the whole mechanism. Putting a class in {@code ua.bookloom.app.bootstrap} is the act of
     * declaring "this runs before logging exists", and the rule holds it to that declaration.
     */
    public static final ArchRule BOOTSTRAP_NO_STATIC_LOGGER = classes()
            .that(ON_BOOTSTRAP_PATH)
            .should(RuleConditions.notBindSlf4jAtClassInit())
            .as("bootstrap-no-static-logger" + NAME_SEPARATOR
                    + "no class on the pre-logging startup path (ua.bookloom.util.paths.. and "
                    + "ua.bookloom.app.bootstrap..) may declare a static SLF4J Logger or touch org.slf4j.. "
                    + "from its static initializer")
            .because("these classes run BEFORE Logback is configured (paths-first startup, DD-39/ADR-0015); a "
                    + "logger created there pins logging to a default directory that is not the resolved one")
            .allowEmptyShould(true);

    // ===== 9. no-inline-style-in-ui ======================================================================

    /**
     * Rule 9: no {@code :ui} code styles anything JavaFX owns from Java. The owner test is "any {@code javafx..}
     * type" rather than {@code Node}, because {@code Tooltip}, {@code ContextMenu}, {@code Tab} and
     * {@code MenuItem} all carry {@code setStyle} without being nodes; the access-target form also catches a method
     * reference ({@code label::setStyle}). Style classes stay free: only a method named {@code setStyle} is banned.
     */
    public static final ArchRule NO_INLINE_STYLE_IN_UI = noClasses()
            .that()
            .resideInAPackage(ROOT + ".ui..")
            .should()
            .accessTargetWhere(target(DescribedPredicate.<AccessTarget>alwaysTrue()
                    .and(name("setStyle"))
                    .and(owner(resideInAPackage("javafx..")))))
            .as("no-inline-style-in-ui" + NAME_SEPARATOR
                    + "no class in :ui may call setStyle on any javafx.. type (nodes, tooltips, menus, tabs); "
                    + "styling comes from theme.css tokens and style classes")
            .because("an inline style is invisible to the token sheet, so no theme swap can reach it and it "
                    + "hard-codes a value the single stylesheet was meant to own (FR-THEME-5, theming-tokens.md)")
            .allowEmptyShould(true);
}
